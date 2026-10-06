package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Member;
import com.museum.collection.entity.PointsAdjustment;
import com.museum.collection.repository.MemberRepository;
import com.museum.collection.repository.PointsAdjustmentRepository;

/**
 * 会员积分调整申请与复核。
 *
 * <p>状态机：待复核 → 已通过 / 已驳回 / 已撤回（均为终态，不再流转）。
 *
 * <p>核心规则：① 创建申请只落单为「待复核」，绝不直接改会员积分；
 * ② 待复核期间申请人本人可撤回，进入终态的记录不可再处理；
 * ③ 复核通过时才在同一事务内更新会员积分并按阈值重算等级，同时记录前后积分、
 * 复核人、复核时间，保证审批记录与会员余额一致；
 * ④ 扣减后积分为负则复核不生效、整单回滚，申请保持「待复核」，便于修正后重新提交；
 * ⑤ 并发复核靠申请行悲观锁保证只有先到者生效，后到者看到明确的已处理结果，
 * 不会重复增减积分，也不会覆盖首次复核意见。</p>
 */
@Service
public class PointsAdjustmentService {

    private final PointsAdjustmentRepository adjustmentRepository;
    private final MemberRepository memberRepository;

    public PointsAdjustmentService(PointsAdjustmentRepository adjustmentRepository,
                                   MemberRepository memberRepository) {
        this.adjustmentRepository = adjustmentRepository;
        this.memberRepository = memberRepository;
    }

    // ---------------- 查询 ----------------

    public List<PointsAdjustment> list(String status, Long memberId) {
        List<PointsAdjustment> base;
        if (memberId != null) {
            base = adjustmentRepository.findByMemberIdOrderByIdDesc(memberId);
        } else if (status != null && !status.isBlank()) {
            base = adjustmentRepository.findByStatusOrderByIdDesc(status);
        } else {
            base = adjustmentRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        }
        // 组合过滤：按会员定位的同时再按状态细分
        if (status != null && !status.isBlank() && memberId != null) {
            base = base.stream().filter(a -> status.equals(a.getStatus())).toList();
        }
        return base;
    }

    public PointsAdjustment get(Long id) {
        return adjustmentRepository.findById(id)
                .orElseThrow(() -> new BizException("积分调整申请不存在：" + id));
    }

    // ---------------- 申请 ----------------

    private String nextApplyNo() {
        int year = LocalDateTime.now().getYear();
        int maxSeq = 0;
        Pattern p = Pattern.compile("^PA-" + year + "-(\\d+)$");
        for (PointsAdjustment a : adjustmentRepository.findAll()) {
            if (a.getApplyNo() == null) {
                continue;
            }
            Matcher m = p.matcher(a.getApplyNo());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("PA-%d-%03d", year, maxSeq + 1);
    }

    /**
     * 提交申请：只落单为「待复核」，此处绝不动会员积分；
     * 积分只有在复核通过时才会变动。
     */
    @Transactional
    public PointsAdjustment create(PointsAdjustment input) {
        if (input.getMemberId() == null) {
            throw new BizException("必须指定会员");
        }
        Member member = memberRepository.findById(input.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在：" + input.getMemberId()));
        if (input.getDelta() == null || input.getDelta() == 0) {
            throw new BizException("调整值不能为 0（正为加分，负为扣分）");
        }
        if (isBlank(input.getReason())) {
            throw new BizException("业务原因不能为空");
        }
        if (isBlank(input.getApplicant())) {
            throw new BizException("申请人不能为空");
        }
        PointsAdjustment a = new PointsAdjustment();
        a.setApplyNo(nextApplyNo());
        a.setMemberId(member.getId());
        a.setMemberNo(member.getMemberNo());
        a.setMemberName(member.getName());
        a.setDelta(input.getDelta());
        a.setReason(input.getReason().trim());
        a.setVoucher(trim(input.getVoucher()));
        a.setApplicant(input.getApplicant().trim());
        a.setStatus("待复核");
        a.setCreatedAt(LocalDateTime.now());
        return adjustmentRepository.save(a);
    }

    /** 撤回：仅待复核可撤回，且须申请人本人操作；撤回即终态，不可再处理 */
    @Transactional
    public PointsAdjustment withdraw(Long id, String operator) {
        if (isBlank(operator)) {
            throw new BizException("操作人不能为空");
        }
        PointsAdjustment a = lockGet(id);
        if (!"待复核".equals(a.getStatus())) {
            throw new BizException(terminalTip(a) + "，只有待复核的申请才能撤回");
        }
        if (!a.getApplicant().equals(operator.trim())) {
            throw new BizException("只有申请人「" + a.getApplicant() + "」本人才能撤回该申请");
        }
        a.setStatus("已撤回");
        return adjustmentRepository.save(a);
    }

    /**
     * 复核通过：待复核 → 已通过，同一事务内更新会员积分并按阈值重算等级，
     * 记录前后积分、复核人、复核时间，审批记录与会员余额同生同灭。
     *
     * <p>并发口径：先锁申请行、再锁会员行（固定顺序，避免死锁）。两名复核人同时
     * 复核同一申请时，后到者阻塞在申请行锁上，拿到锁时看到的是对方已提交的终态，
     * 得到明确的已处理提示，不会重复增减积分，也不会覆盖首次复核意见。</p>
     *
     * <p>扣减后积分为负：抛出业务异常、整单回滚，申请保持「待复核」，便于修正后重新提交。</p>
     */
    @Transactional
    public PointsAdjustment approve(Long id, String reviewer, String note) {
        if (isBlank(reviewer)) {
            throw new BizException("复核人不能为空");
        }
        PointsAdjustment a = lockGet(id);
        if (!"待复核".equals(a.getStatus())) {
            throw new BizException(terminalTip(a) + "，不能重复复核");
        }
        Member member = lockMember(a.getMemberId());
        int before = member.getPoints();
        int after = before + a.getDelta();
        if (after < 0) {
            throw new BizException("扣减后积分不能为负（会员当前 " + before + " 分，拟调整 " + a.getDelta()
                    + " 分），本次复核未生效，申请仍为待复核；请撤回修正后重新提交");
        }
        member.setPoints(after);
        member.setLevel(Dict.decideMemberLevel(after));
        memberRepository.save(member);
        a.setStatus("已通过");
        a.setReviewer(reviewer.trim());
        a.setReviewNote(trim(note));
        a.setReviewedAt(LocalDateTime.now());
        a.setPointsBefore(before);
        a.setPointsAfter(after);
        return adjustmentRepository.save(a);
    }

    /** 复核驳回：待复核 → 已驳回（终态留痕），会员积分不变；驳回必须填复核意见 */
    @Transactional
    public PointsAdjustment reject(Long id, String reviewer, String note) {
        if (isBlank(reviewer)) {
            throw new BizException("复核人不能为空");
        }
        if (isBlank(note)) {
            throw new BizException("驳回必须填写复核意见");
        }
        PointsAdjustment a = lockGet(id);
        if (!"待复核".equals(a.getStatus())) {
            throw new BizException(terminalTip(a) + "，不能重复复核");
        }
        a.setStatus("已驳回");
        a.setReviewer(reviewer.trim());
        a.setReviewNote(note.trim());
        a.setReviewedAt(LocalDateTime.now());
        return adjustmentRepository.save(a);
    }

    // ---------------- 内部 ----------------

    private PointsAdjustment lockGet(Long id) {
        return adjustmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("积分调整申请不存在：" + id));
    }

    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + memberId));
    }

    /** 终态提示：让后到者看到明确的已处理结果（状态 + 复核人），且不覆盖首次复核意见 */
    private static String terminalTip(PointsAdjustment a) {
        String tip = "申请「" + a.getApplyNo() + "」已处理为「" + a.getStatus() + "」";
        if (a.getReviewer() != null) {
            tip += "（复核人：" + a.getReviewer() + "）";
        }
        return tip;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
