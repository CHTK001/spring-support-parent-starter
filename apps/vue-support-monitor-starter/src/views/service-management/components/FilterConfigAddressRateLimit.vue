<template>
  <el-dialog v-model="visibleInner" title="地址限流配置" width="760px" :close-on-click-modal="false" @close="handleClose">
    <div>
      <div class="rule-row" v-for="(r, idx) in rules" :key="idx">
        <el-input v-model="r.addressRateLimitAddress" placeholder="接口路径，如 /api/demo" style="width: 360px" />
        <el-input-number v-model="r.addressRateLimitQps" :min="1" :max="100000" style="width: 160px;margin-left: 8px" />
        <el-switch v-model="r.addressRateLimitEnabled" style="margin-left: 8px" />
        <el-button type="danger" circle style="margin-left: 8px" @click="rules.splice(idx,1)"><IconifyIconOnline icon="ri:delete-bin-line" /></el-button>
      </div>
      <el-button type="primary" link @click="addRule"><IconifyIconOnline icon="ri:add-line" />新增规则</el-button>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAddressRateLimitRules, saveAddressRateLimitRules, type AddressRateLimitRule } from '@/api/system-server-setting'

interface Props { visible: boolean; serverId: number }
const props = defineProps<Props>()
const emit = defineEmits<{ 'update:visible': [boolean]; success: [] }>()

const visibleInner = ref(false)
const loading = ref(false)
const rules = ref<AddressRateLimitRule[]>([])

watch(() => props.visible, async (v) => { visibleInner.value = v; if (v) await loadData() }, { immediate: true })
watch(visibleInner, v => emit('update:visible', v))

async function loadData() {
  rules.value = []
  try {
    const res = await getAddressRateLimitRules(props.serverId)
    if (res.success && Array.isArray(res.data)) { rules.value = res.data }
  } catch (e) { /* ignore */ }
}

function addRule() { rules.value.push({ addressRateLimitAddress: '', addressRateLimitQps: 100, addressRateLimitEnabled: true }) }

async function handleSave() {
  loading.value = true
  try {
    const res = await saveAddressRateLimitRules(props.serverId, rules.value)
    if (res.success) {
      ElMessage.success('保存成功，已热应用')
      emit('success')
      visibleInner.value = false
    } else { ElMessage.error(res.msg || '保存失败') }
  } finally { loading.value = false }
}

function handleClose() { visibleInner.value = false }
</script>

<style scoped>
.rule-row { display:flex; align-items:center; margin-bottom:8px }
</style>

