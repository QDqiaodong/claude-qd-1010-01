package com.museum.collection.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 会员积分调整申请单。
 *
 * <p>状态机：待复核 → 已通过 / 已驳回 / 已撤回（均为终态，不再流转）。
 *
 * <p>核心规则：创建申请只落单，不改会员积分；复核通过时才在同一事务内更新
 * 会员积分并按阈值重算等级，同时记录前后积分，保证审批记录与会员余额一致。</p>
 */
@Entity
@Table(name = "points_adjustment")
@Getter
@Setter
public class PointsAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请单号，唯一，如 PA-2026-001 */
    @Column(name = "apply_no", nullable = false, unique = true, length = 32)
    private String applyNo;

    /** 会员 id */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 会员编号（冗余，便于列表展示） */
    @Column(name = "member_no", length = 32)
    private String memberNo;

    /** 会员姓名（冗余） */
    @Column(name = "member_name", length = 32)
    private String memberName;

    /** 调整值：正为加分，负为扣分 */
    @Column(nullable = false)
    private Integer delta;

    /** 业务原因 */
    @Column(nullable = false, length = 256)
    private String reason;

    /** 关联凭据（票据号、活动编号等） */
    @Column(length = 128)
    private String voucher;

    /** 申请人 */
    @Column(nullable = false, length = 32)
    private String applicant;

    /** 待复核 / 已通过 / 已驳回 / 已撤回 */
    @Column(length = 16)
    private String status;

    /** 复核人 */
    @Column(length = 32)
    private String reviewer;

    /** 复核意见 */
    @Column(name = "review_note", length = 512)
    private String reviewNote;

    /** 复核时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 复核通过前积分（仅已通过时有值） */
    @Column(name = "points_before")
    private Integer pointsBefore;

    /** 复核通过后积分（仅已通过时有值） */
    @Column(name = "points_after")
    private Integer pointsAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
