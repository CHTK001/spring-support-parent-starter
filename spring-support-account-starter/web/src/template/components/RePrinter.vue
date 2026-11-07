<!--
  RePrinter —— 打印机管理面板（独立组件）

  职责（第三方打印的全部接口在这个组件里收口）：
    - 打印机列表：所有已接入的打印机（本地 / 局域网 / 第三方云），展示地址/端口/Token 等连接参数
    - 默认打印机：支持在表格/列表里把任意一台设为默认（设置后前端打印直接选它）
    - 新增/编辑：地址/端口/Token/认证名 由本组件的表单维护
    - 发现：把外部 Agent（局域网/代理）扫描到的设备接入进来
    - selector 模式：在模板设计器的「打印」流里选打印机并确认；
      宿主传入 PrintTemplate 时本组件直接发起第三方的打（让盒子选）；
      不传时把选中的打印机名抛回给宿主，宿主调用 tpl.print 把第三方的参数递进去。

  mode 由宿主决定：
    - "page"   打印机管理页面（ReTable 驱动，全量操作）
    - "selector" 打印机选择器（轻量列表 + 确认并第三方的打印）

  @since 4.0.0.60
  @author CH
-->
<template>
  <div class="re-printer" :class="`re-printer--${mode}`">
    <!-- ==================== 顶栏 ==================== -->
    <header class="re-printer__header">
      <h3 class="re-printer__title">
        <ReIcon name="Printer" :size="14" />
        {{ title }}
        <span class="re-printer__count">共 {{ printers.length }} 台</span>
      </h3>
      <div class="re-printer__right">
        <template v-if="mode === 'page'">
          <ReButton text @click="onDiscover">发现打印机</ReButton>
          <ReButton type="primary" @click="openForm">
            <ReIcon name="Plus" :size="12" />
            添加打印机
          </ReButton>
        </template>
      </div>
    </header>

    <!-- ==================== page 模式：ReTable 驱动的打印机列表 ==================== -->
    <ReTable
      v-if="mode === 'page'"
      ref="tableRef"
      class="re-printer__table"
      table-id="printer-manager"
      :loader="loadPrinters"
      :columns="columns"
      row-key="name"
      :layout-table="{ fill: true }"
      stripe
      :page-sizes="[10, 20, 50, 100]"
      v-model:page="page"
      v-model:page-size="pageSize"
      @row-click="onRowClick"
    >
      <!-- 打印机名 + 连接地址 -->
      <template #name="{ row }: { row: PrinterConfig }">
        <div class="re-printer-device">
          <div class="re-printer-device__name">{{ row.name }}</div>
          <div class="re-printer-device__meta">{{ host(row) }}</div>
        </div>
      </template>

      <!-- 类型（模型） -->
      <template #type="{ row }: { row: PrinterConfig }">
        <span class="re-printer-cell">{{ row.type || '—' }}</span>
      </template>

      <!-- 默认 -->
      <template #isDefault="{ row }: { row: PrinterConfig }">
        <el-tag v-if="row.isDefault" type="success" size="small" effect="light">默认</el-tag>
      </template>

      <!-- 操作 -->
      <template #action="{ row }: { row: PrinterConfig }">
        <div class="re-printer-actions">
          <ReButton text type="primary" @click.stop="onSetDefault(row.name)">设为默认</ReButton>
          <ReButton text @click.stop="onEdit(row)">编辑</ReButton>
          <ReButton text type="danger" @click.stop="onRemove(row.name)">删除</ReButton>
        </div>
      </template>
    </ReTable>

    <!-- ==================== selector 模式：轻量的打印机列表 ==================== -->
    <div v-else class="re-printer__list">
      <div v-if="loading" class="re-printer__empty">加载中...</div>
      <div v-else-if="!printers.length" class="re-printer__empty">
        暂无打印机，请先
        <ReButton type="primary" text @click="openForm">添加打印机</ReButton>
        或
        <ReButton text @click="onDiscover">发现打印机</ReButton>
      </div>
      <div v-else class="re-printer__items">
        <div
          v-for="printer in printers"
          :key="printer.name"
          class="re-printer-item"
          :class="{
            're-printer-item--selected': printer.name === selectedName,
            're-printer-item--default': printer.isDefault
          }"
          @click="onSelect(printer.name)"
        >
          <div class="re-printer-item__body">
            <div class="re-printer-item__name">
              <span class="re-printer-item__name-text">{{ printer.name }}</span>
              <el-tag v-if="printer.isDefault" type="success" size="small" effect="light">默认</el-tag>
              <el-tag v-if="printer.address" type="info" size="small" effect="plain">{{ host(printer) }}</el-tag>
            </div>
            <div class="re-printer-item__meta">
              {{ printer.type || '未分类' }} · {{ printer.remark || '无备注' }}
            </div>
          </div>
          <div class="re-printer-item__actions">
            <ReButton text size="small" type="primary" @click.stop="onSetDefault(printer.name)">设为默认</ReButton>
          </div>
        </div>
      </div>

      <!-- 确认并第三方的打印 -->
      <div class="re-printer__footer">
        <ReButton type="primary" :disabled="!selectedName" @click="onPrint">确认并第三方的打印</ReButton>
      </div>
    </div>

    <!-- ==================== 新增/编辑弹窗 ==================== -->
    <ReDialog v-model="formVisible" :title="formTitle" width="520px" @confirm="onFormConfirm">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="打印机设备名" required>
          <el-input v-model="form.name" placeholder="设备名称，如 HP LaserJet Pro" maxlength="64" />
        </el-form-item>
        <el-form-item label="打印机类型">
          <el-input v-model="form.type" placeholder="如 HP LaserJet Pro / 第三方云" maxlength="64" />
        </el-form-item>
        <el-form-item label="连接地址">
          <el-input v-model="form.address" placeholder="IP/域名，如 192.168.1.100" maxlength="128" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="0" :max="65535" class="w-full" />
        </el-form-item>
        <el-form-item label="认证名">
          <el-input v-model="form.username" placeholder="认证用户名（可选）" maxlength="64" />
        </el-form-item>
        <el-form-item label="认证 Token">
          <el-input v-model="form.token" placeholder="认证 Token（可选）" maxlength="128" type="password" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <ReButton @click="formVisible = false">取消</ReButton>
        <ReButton type="primary" :loading="saving" @click="onFormConfirm">确定</ReButton>
      </template>
    </ReDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * RePrinter —— 打印机管理面板（独立组件）
 *
 * 与第三方的打印对接流程：
 *   1) 页面加载时从后端拉取已接入的打印机列表
 *   2) 默认打印机自动打在角标上（宿主点「打印」时直接选它）
 *   3) 宿主传入 tpl（PrintTemplate）时，点「确认并第三方的打印」直接调
 *      tpl.print({}, { printer: name, smartPrint: false }, cb) 达成第三方的打；
 *      不传 tpl 时抛回 { name }，由宿主自行发起 tpl.print
 */
import { computed, onMounted, ref, watch } from 'vue'
import ReIcon from '@repo/ui/re-icon'
import ReButton from '@repo/ui/re-button'
import ReTable from '@repo/ui/re-table'
import ReDialog from '@repo/ui/re-dialog'
import type { ReTableColumn, ReTableLoadParams, RePageResult } from '@repo/ui/re-table'
import type { PrinterConfig } from '../types/template'
import { templateApi } from '../api/templateApi'
import { ElMessage, ElMessageBox } from 'element-plus'

// --------------------------- 主上的 (& 由调用的脚决定页面样式) ---------------------------
const props = defineProps<{
  /** 打印机管理面板标题 */
  title?: string
  /** 模式：page | selector */
  mode?: 'page' | 'selector'
  /** 已选的打印机名（selector 模式下双向绑定） */
  printer?: string
  /** hiprint 的 PrintTemplate（传入时本组件直接发起第三方的打印） */
  tpl?: any
}>()
const emit = defineEmits<{
  /** 确认并第三方的打印（传给桌面端：打印机名） */
  print: [name: string]
  /** 编辑打印机行（page 模式） */
  edit: [row: PrinterConfig]
  /** 删除打印机（page 模式） */
  remove: [name: string]
}>()

// ==================== 列表状态 ====================
const tableRef = ref<{ reload: () => Promise<void>; goto: (page?: number, pageSize?: number) => void } | null>(null)
const printers = ref<PrinterConfig[]>([])
const loading = ref(false)
const saving = ref(false)

/** page 分页 */
const page = ref(1)
const pageSize = ref(10)

/** 选中的打印机名（selector 模式，可被宿主的 printer 回写） */
const selectedName = ref(props.printer)
watch(() => props.printer, (v: string | undefined) => {
  selectedName.value = v
})

/** 打印机列表列定义 */
const columns: ReTableColumn[] = [
  { prop: 'name', label: '打印机名称', slot: 'name', minWidth: 220 },
  { prop: 'type', label: '类型', slot: 'type', width: 160 },
  { prop: 'isDefault', label: '默认', slot: 'isDefault', width: 90, fixed: 'right' },
  { prop: 'action', label: '操作', slot: 'action', width: 200, fixed: 'right' }
]

// ==================== 加载 ====================
/** 打印机 loader：后端分页，分页size由 ReTable 注入 */
async function loadPrinters({ page: p, pageSize: ps }: ReTableLoadParams): Promise<RePageResult> {
  loading.value = true
  try {
    const list = (await templateApi.printerList()) ?? []
    const total = list.length
    const start = (p - 1) * ps
    const data = list.slice(start, start + ps)
    return { total, data: data as unknown as Record<string, unknown>[] }
  } catch (err) {
    ElMessage.error(String(err))
    return { total: 0, data: [] }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPrinters({ page: page.value, pageSize: pageSize.value, params: {} })
})

// ==================== 操作 ====================
function host(row: PrinterConfig): string {
  if (!row.address) return '内网'
  if (row.port) return `${row.address}:${row.port}`
  return row.address
}

/** 发现打印机（由后端局域网/代理扫描，这里只刷新） */
async function onDiscover(): Promise<void> {
  try {
    await templateApi.discoverPrinters()
    await loadPrinters({ page: page.value, pageSize: pageSize.value, params: {} })
    tableRef.value?.reload()
  } catch (err) {
    ElMessage.error(String(err))
  }
}

/** 行点击（selector 模式：点击行 = 选机） */
function onRowClick(row: Record<string, unknown>): void {
  if (props.mode === 'selector') {
    onSelect(row.name as string)
  }
}

/** 选择打印机（selector 模式：点击行 = 选机，下一页的“确认并第三方的打”以选中的为准） */
function onSelect(name: string): void {
  if (props.mode === 'selector') {
    selectedName.value = name
  }
}

/** 设为默认 */
async function onSetDefault(name: string): Promise<void> {
  saving.value = true
  try {
    await templateApi.setDefaultPrinter(name)
    await loadPrinters({ page: page.value, pageSize: pageSize.value, params: {} })
    tableRef.value?.reload()
    ElMessage.success(`「${name}」已设为默认打印机`)
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    saving.value = false
  }
}

/** 删除 */
async function onRemove(name: string): Promise<void> {
  saving.value = true
  try {
    await ElMessageBox.confirm(`确定删除打印机「${name}」？`, '删除', { type: 'warning' })
    await templateApi.removePrinter(name)
    await loadPrinters({ page: page.value, pageSize: pageSize.value, params: {} })
    tableRef.value?.reload()
    ElMessage.success('删除成功')
  } catch (err) {
    if (String(err) !== 'Error: cancel') ElMessage.error(String(err))
  } finally {
    saving.value = false
  }
}

// ==================== 新增/编辑 ====================
const formVisible = ref(false)
const editingName = ref<string | null>(null)
const formRef = ref<Record<string, unknown> | null>(null)
const form = ref<Partial<PrinterConfig>>({})

/** 窗体标题 */
const formTitle = computed(() => editingName.value ? `编辑打印机「${editingName.value}」` : '新增打印机')

/** 打开新增窗体 */
function openForm(): void {
  editingName.value = null
  form.value = {}
  formVisible.value = true
}

/** 打开编辑窗体 */
function onEdit(row: PrinterConfig): void {
  editingName.value = row.name
  form.value = { ...row }
  formVisible.value = true
}

/** 窗体保存 */
async function onFormConfirm(): Promise<void> {
  if (!form.value.name) {
    ElMessage.warning('请输入打印机设备名')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      type: form.value.type,
      address: form.value.address,
      port: form.value.port,
      username: form.value.username,
      token: form.value.token,
      remark: form.value.remark
    }
    await templateApi.setPrinter(payload)
    ElMessage.success('已保存')
    formVisible.value = false
    await loadPrinters({ page: page.value, pageSize: pageSize.value, params: {} })
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    saving.value = false
  }
}

// ==================== 第三方的打印 ====================
/** 确认并第三方的打印 */
async function onPrint(): Promise<void> {
  const name = selectedName.value
  if (!name) {
    ElMessage.warning('请先选择一台打印机')
    return
  }
  // 宿主传了 PrintTemplate：本组件直接发起第三方的打 / 打印
  if (props.tpl && props.tpl.print) {
    const tpl = props.tpl
    saving.value = true
    try {
      tpl.print({}, { printer: name, smartPrint: false }, () => {
        ElMessage.info(`已发送到 ${name}（第三方的打印）`)
      })
    } catch (err) {
      ElMessage.error(String(err))
    } finally {
      saving.value = false
    }
    emit('print', name)
    return
  }
  emit('print', name)
}

/** 重载表格（暴露给父级调用） */
function reload(): Promise<void> {
  return tableRef.value?.reload?.() ?? Promise.resolve()
}

defineExpose({ reload })
</script>

<style lang="scss" scoped>
.re-printer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
  overflow: hidden;
}

.re-printer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.re-printer__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.re-printer__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}

.re-printer__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ==================== 列表 ==================== */
.re-printer__table {
  flex: 1;
  overflow: auto;
}

.re-printer__list {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.re-printer__items {
  flex: 1;
  overflow: auto;
}

.re-printer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color-page);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;

  &:hover {
    border-color: var(--el-border-color);
    background: var(--el-fill-color-light);
  }

  &--selected {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &--default::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 8px;
    height: 8px;
    background: var(--el-color-success);
    border-radius: var(--el-radius-round);
  }
}

.re-printer-item__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.re-printer-item__name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.re-printer-item__name-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.re-printer-item__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.re-printer-item__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.re-printer__footer {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.re-printer__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

/* 打印机名称 */
.re-printer-device {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.re-printer-device__name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.re-printer-device__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.re-printer-cell {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.re-printer-actions {
  display: flex;
  gap: 8px;
}
</style>
