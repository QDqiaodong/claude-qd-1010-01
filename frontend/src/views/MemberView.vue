<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const LEVELS = ['普通', '银卡', '金卡']
const ADJ_STATUS = ['待复核', '已通过', '已驳回', '已撤回']
const ADJ_TAG = { 待复核: 'warning', 已通过: 'success', 已驳回: 'danger', 已撤回: 'info' }

const tab = ref('members')
const saving = ref(false)

// ---------------- 会员 ----------------
const rows = ref([])
const dialog = ref(false)
const form = reactive({ id: null, memberNo: '', name: '', phone: '', level: '普通', points: 0, joinedAt: '' })

// ---------------- 积分调整申请 ----------------
const adjustments = ref([])
const filterStatus = ref('')
const filterMemberId = ref(null)

const applyDialog = ref(false)
const applyForm = reactive({ memberId: null, delta: 100, reason: '', voucher: '', applicant: '' })

const reviewDialog = ref(false)
const reviewForm = reactive({ id: null, no: '', kind: 'approve', reviewer: '', note: '' })

const withdrawDialog = ref(false)
const withdrawForm = reactive({ id: null, no: '', applicant: '', operator: '' })

const historyDialog = ref(false)
const historyMember = ref({})
const historyRows = ref([])

const detailDialog = ref(false)
const detail = ref({})

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')
const deltaText = (d) => (d == null ? '—' : d > 0 ? `+${d}` : `${d}`)

async function load() {
  rows.value = await api.listMembers()
  await loadAdjustments()
}

async function loadAdjustments() {
  const filter = {}
  if (filterStatus.value) filter.status = filterStatus.value
  if (filterMemberId.value) filter.memberId = filterMemberId.value
  adjustments.value = await api.listPointsAdjustments(filter)
}

// ---------------- 会员录入 ----------------
function openCreate() {
  Object.assign(form, { id: null, memberNo: '', name: '', phone: '', level: '普通', points: 0, joinedAt: '' })
  dialog.value = true
}
async function submit() {
  saving.value = true
  try {
    if (form.id) { await api.updateMember(form.id, { ...form }); ElMessage.success('已更新') }
    else { await api.createMember({ ...form }); ElMessage.success('会员已录入') }
    dialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

// ---------------- 提交调整申请 ----------------
function openApply(row) {
  Object.assign(applyForm, { memberId: row ? row.id : null, delta: 100, reason: '', voucher: '', applicant: '' })
  applyDialog.value = true
}
async function submitApply() {
  if (!applyForm.memberId) { ElMessage.error('请选择会员'); return }
  if (!applyForm.delta) { ElMessage.error('调整值不能为 0（正为加分，负为扣分）'); return }
  if (!applyForm.reason.trim()) { ElMessage.error('请填写业务原因'); return }
  if (!applyForm.applicant.trim()) { ElMessage.error('请填写申请人'); return }
  saving.value = true
  try {
    await api.createPointsAdjustment({ ...applyForm, reason: applyForm.reason.trim(), applicant: applyForm.applicant.trim() })
    ElMessage.success('申请已提交，待复核（复核通过才会变更积分）')
    applyDialog.value = false
    tab.value = 'adjust'
    await loadAdjustments()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

// ---------------- 复核（通过 / 驳回） ----------------
function openReview(row, kind) {
  Object.assign(reviewForm, { id: row.id, no: row.applyNo, kind, reviewer: '', note: '' })
  reviewDialog.value = true
}
async function submitReview() {
  if (!reviewForm.reviewer.trim()) { ElMessage.error('请填写复核人'); return }
  if (reviewForm.kind === 'reject' && !reviewForm.note.trim()) { ElMessage.error('驳回必须填写复核意见'); return }
  saving.value = true
  try {
    if (reviewForm.kind === 'approve') {
      await api.approvePointsAdjustment(reviewForm.id, reviewForm.reviewer.trim(), reviewForm.note.trim())
      ElMessage.success('复核通过，积分已更新')
    } else {
      await api.rejectPointsAdjustment(reviewForm.id, reviewForm.reviewer.trim(), reviewForm.note.trim())
      ElMessage.success('已驳回')
    }
    reviewDialog.value = false
    await load(); emit('changed')
  } catch (e) {
    // 并发被抢先 / 扣减为负等：提示后端给出的明确结果，并刷新出最新状态
    ElMessage.error(e.message)
    await loadAdjustments()
  } finally { saving.value = false }
}

// ---------------- 撤回 ----------------
function openWithdraw(row) {
  Object.assign(withdrawForm, { id: row.id, no: row.applyNo, applicant: row.applicant, operator: '' })
  withdrawDialog.value = true
}
async function submitWithdraw() {
  if (!withdrawForm.operator.trim()) { ElMessage.error('请填写操作人'); return }
  saving.value = true
  try {
    await api.withdrawPointsAdjustment(withdrawForm.id, withdrawForm.operator.trim())
    ElMessage.success('申请已撤回')
    withdrawDialog.value = false
    await loadAdjustments()
  } catch (e) {
    ElMessage.error(e.message)
    await loadAdjustments()
  } finally { saving.value = false }
}

// ---------------- 会员调整历史 / 申请详情 ----------------
async function openHistory(row) {
  historyMember.value = row
  historyDialog.value = true
  try {
    historyRows.value = await api.listPointsAdjustments({ memberId: row.id })
  } catch (e) { ElMessage.error(e.message) }
}
function openDetail(row) {
  detail.value = row
  detailDialog.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-tabs v-model="tab" class="member-tabs">
      <!-- ================= 会员 ================= -->
      <el-tab-pane label="会员" name="members">
        <div class="v-head">
          <h3>会员管理</h3>
          <el-button type="primary" size="small" @click="openCreate">录入会员</el-button>
        </div>

        <el-table :data="rows" size="small" height="232">
          <el-table-column prop="memberNo" label="编号" width="88" />
          <el-table-column prop="name" label="姓名" width="72" />
          <el-table-column prop="phone" label="手机" width="104" />
          <el-table-column prop="level" label="等级" width="64" />
          <el-table-column prop="points" label="积分" width="64" />
          <el-table-column label="操作" width="104" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openApply(row)">调分</el-button>
              <el-button size="small" text @click="openHistory(row)">历史</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ================= 积分调整申请 ================= -->
      <el-tab-pane label="积分调整" name="adjust">
        <div class="v-head">
          <div class="v-head-ops">
            <el-select v-model="filterStatus" size="small" style="width: 96px" placeholder="全部状态" clearable @change="loadAdjustments">
              <el-option v-for="s in ADJ_STATUS" :key="s" :label="s" :value="s" />
            </el-select>
            <el-select v-model="filterMemberId" size="small" style="width: 104px" placeholder="全部会员" clearable filterable @change="loadAdjustments">
              <el-option v-for="m in rows" :key="m.id" :label="m.name" :value="m.id" />
            </el-select>
          </div>
          <el-button type="primary" size="small" @click="openApply(null)">新建申请</el-button>
        </div>

        <el-table :data="adjustments" size="small" height="232">
          <el-table-column prop="applyNo" label="单号" width="102" />
          <el-table-column prop="memberName" label="会员" width="64" />
          <el-table-column label="调整" width="58">
            <template #default="{ row }">
              <span :class="row.delta > 0 ? 'delta-up' : 'delta-down'">{{ deltaText(row.delta) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="applicant" label="申请人" width="62" />
          <el-table-column label="状态" width="68">
            <template #default="{ row }">
              <el-tag size="small" :type="ADJ_TAG[row.status] || 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="158" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === '待复核'">
                <el-button size="small" text type="success" @click="openReview(row, 'approve')">通过</el-button>
                <el-button size="small" text type="danger" @click="openReview(row, 'reject')">驳回</el-button>
                <el-button size="small" text type="warning" @click="openWithdraw(row)">撤回</el-button>
              </template>
              <el-button size="small" text @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 录入会员 -->
    <el-dialog v-model="dialog" title="录入会员" width="420px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="编号"><el-input v-model="form.memberNo" placeholder="如 MB-0009" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level"><el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" /></el-select>
        </el-form-item>
        <el-form-item label="入会日"><el-date-picker v-model="form.joinedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 提交积分调整申请 -->
    <el-dialog v-model="applyDialog" title="提交积分调整申请" width="440px">
      <el-alert type="info" :closable="false" show-icon title="申请提交后为「待复核」，复核通过才会变更会员积分" style="margin-bottom: 10px" />
      <el-form :model="applyForm" label-width="72px">
        <el-form-item label="会员" required>
          <el-select v-model="applyForm.memberId" filterable placeholder="选择会员" style="width: 100%">
            <el-option v-for="m in rows" :key="m.id" :label="`${m.memberNo} ${m.name}（当前 ${m.points} 分）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整值" required>
          <el-input-number v-model="applyForm.delta" :step="50" style="width: 150px" />
          <span class="form-tip">正为加分，负为扣分</span>
        </el-form-item>
        <el-form-item label="业务原因" required>
          <el-input v-model="applyForm.reason" type="textarea" :rows="2" placeholder="如：志愿服务补录 / 门票退订扣减" />
        </el-form-item>
        <el-form-item label="关联凭据">
          <el-input v-model="applyForm.voucher" placeholder="票据号、活动编号等（可空）" />
        </el-form-item>
        <el-form-item label="申请人" required>
          <el-input v-model="applyForm.applicant" placeholder="提交人姓名（撤回时须本人操作）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="applyDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 复核（通过 / 驳回） -->
    <el-dialog v-model="reviewDialog" :title="`${reviewForm.kind === 'approve' ? '复核通过' : '复核驳回'} ${reviewForm.no}`" width="420px">
      <el-alert v-if="reviewForm.kind === 'approve'" type="warning" :closable="false" show-icon
        title="通过后立即更新会员积分并重算等级；扣减后为负则不生效，申请保持待复核" style="margin-bottom: 10px" />
      <el-form label-width="72px">
        <el-form-item label="复核人" required><el-input v-model="reviewForm.reviewer" placeholder="复核人姓名" /></el-form-item>
        <el-form-item label="复核意见" :required="reviewForm.kind === 'reject'">
          <el-input v-model="reviewForm.note" type="textarea" :rows="3"
            :placeholder="reviewForm.kind === 'reject' ? '驳回原因（必填）' : '复核意见（可空）'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="reviewDialog = false">取消</el-button>
        <el-button size="small" :type="reviewForm.kind === 'approve' ? 'success' : 'danger'" :loading="saving" @click="submitReview">
          {{ reviewForm.kind === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 撤回申请 -->
    <el-dialog v-model="withdrawDialog" :title="`撤回申请 ${withdrawForm.no}`" width="420px">
      <el-alert type="warning" :closable="false" show-icon
        :title="`仅申请人本人（${withdrawForm.applicant}）可撤回；撤回后为终态，不可再处理`" style="margin-bottom: 10px" />
      <el-form label-width="72px">
        <el-form-item label="操作人" required><el-input v-model="withdrawForm.operator" placeholder="须为申请人本人" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="withdrawDialog = false">取消</el-button>
        <el-button size="small" type="warning" :loading="saving" @click="submitWithdraw">确认撤回</el-button>
      </template>
    </el-dialog>

    <!-- 会员详情：积分调整历史（处理人 / 处理时间 / 前后积分） -->
    <el-dialog v-model="historyDialog" :title="`积分调整历史 · ${historyMember.name || ''}`" width="600px">
      <el-descriptions :column="4" size="small" border style="margin-bottom: 10px">
        <el-descriptions-item label="编号">{{ historyMember.memberNo }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ historyMember.level }}</el-descriptions-item>
        <el-descriptions-item label="当前积分">{{ historyMember.points }}</el-descriptions-item>
        <el-descriptions-item label="入会日">{{ historyMember.joinedAt || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="historyRows" size="small" height="240">
        <el-table-column prop="applyNo" label="单号" width="100" />
        <el-table-column label="调整" width="58">
          <template #default="{ row }">
            <span :class="row.delta > 0 ? 'delta-up' : 'delta-down'">{{ deltaText(row.delta) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="业务原因" min-width="104" show-overflow-tooltip />
        <el-table-column label="状态" width="66">
          <template #default="{ row }">
            <el-tag size="small" :type="ADJ_TAG[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="64">
          <template #default="{ row }">{{ row.reviewer || '—' }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="96">
          <template #default="{ row }">{{ fmt(row.reviewedAt) }}</template>
        </el-table-column>
        <el-table-column label="积分变化" width="88">
          <template #default="{ row }">
            <span v-if="row.pointsBefore != null">{{ row.pointsBefore }} → {{ row.pointsAfter }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyRows.length" description="暂无调整记录" :image-size="60" />
    </el-dialog>

    <!-- 申请详情 -->
    <el-dialog v-model="detailDialog" :title="`调整申请 ${detail.applyNo || ''}`" width="460px">
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="ADJ_TAG[detail.status] || 'info'">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="会员">{{ detail.memberNo }} {{ detail.memberName }}</el-descriptions-item>
        <el-descriptions-item label="调整值">{{ deltaText(detail.delta) }}</el-descriptions-item>
        <el-descriptions-item label="业务原因">{{ detail.reason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联凭据">{{ detail.voucher || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicant || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
        <template v-if="detail.status === '已通过' || detail.status === '已驳回'">
          <el-descriptions-item label="复核人">{{ detail.reviewer || '—' }}</el-descriptions-item>
          <el-descriptions-item label="复核意见">{{ detail.reviewNote || '—' }}</el-descriptions-item>
          <el-descriptions-item label="复核时间">{{ fmt(detail.reviewedAt) }}</el-descriptions-item>
        </template>
        <el-descriptions-item v-if="detail.status === '已通过'" label="积分变化">
          {{ detail.pointsBefore }} → {{ detail.pointsAfter }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.member-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
.v-head-ops { display: flex; gap: 8px; align-items: center; }
.form-tip { margin-left: 8px; font-size: 12px; color: var(--muted); }
.delta-up { color: var(--el-color-success); font-weight: 600; }
.delta-down { color: var(--el-color-danger); font-weight: 600; }
</style>
