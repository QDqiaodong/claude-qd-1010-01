<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const dialog = ref(false)
const saving = ref(false)
const LEVELS = ['普通', '银卡', '金卡']

const form = reactive({ id: null, memberNo: '', name: '', phone: '', level: '普通', points: 0, joinedAt: '' })

async function load() { rows.value = await api.listMembers() }
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
async function addPoints(row) {
  const d = Number(prompt('本次积分变动（正加负减）：', '100'))
  if (Number.isNaN(d)) return
  try { await api.addPoints(row.id, d); ElMessage.success('积分已更新'); await load(); emit('changed') }
  catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>会员管理</h3>
      <el-button type="primary" size="small" @click="openCreate">录入会员</el-button>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="memberNo" label="编号" width="92" />
      <el-table-column prop="name" label="姓名" width="90" />
      <el-table-column prop="phone" label="手机" width="110" />
      <el-table-column prop="level" label="等级" width="70" />
      <el-table-column prop="points" label="积分" width="70" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="addPoints(row)">积分</el-button>
        </template>
      </el-table-column>
    </el-table>

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
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
</style>
