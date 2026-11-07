<!--
  调用日志 PAGE
  记录模板库的调用（预览 / 设计 / 打印）：调用参数、时间、IP、操作人。
  数据来源：`logs`（API `page` 分页 + 条件查询），应用侧每执行一次操作就写一条。
  路由：/template/logs （visible = 1，侧边栏可见）
-->
<template>
  <div class="log-page">
    <ReTable
      ref="tableRef"
      table-id="template-logs"
      :search="{ filters: searchFilters, title: '查询', btnMode: 'text' }"
      :search-gap="16"
      v-model:search-model="query"
      @search-reset="handleReset"
      :loader="loadLogs"
      :columns="columns"
      :pagination="true"
      :params="query"
      row-key="id"
      v-model:page="query.page"
      v-model:page-size="query.size"
    >
      <template #empty>
        <div class="page-empty">
          <span class="page-empty__icon"><ReIcon name="Document" :size="22" /></span>
          <span class="page-empty__title">还没有调用记录</span>
          <span class="page-empty__desc">模板被预览 / 设计 / 打印时会产生记录，包含调用参数、时间和来源 IP。</span>
        </div>
      </template>
      <template #actionType="{ row }">
        <el-tag :type="actionTypeMeta(row.action).type" size="small" effect="plain" round>
          {{ actionTypeMeta(row.action).label }}
        </el-tag>
      </template>
      <template #parameters="{ row }">
        <span class="mono">{{ JSON.stringify(row.parameters ?? {}) }}</span>
      </template>
      <template #time="{ row }">
        <ReTime :time="row.time" />
      </template>
      <template #operator="{ row }">
        <span class="mono">{{ row.operator ?? '-' }}</span>
      </template>
    </ReTable>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import ReTable from '@repo/ui/re-table'
import ReTime from '@repo/ui/re-time'
import ReIcon from '@repo/ui/re-icon'
import type { ReTableColumn, ReTableLoadParams, RePageResult } from '@repo/ui/re-table'

import { templateApi } from '../api/templateApi'
import { actionLabel, tplActionLabels } from '../utils/i18n'

const tableRef = ref<InstanceType<typeof ReTable> | null>(null)

const query = ref({
  templateName: '',
  templateId: '',
  action: '',
  ip: '',
  page: 1,
  size: 10
})

/** 搜索条件 */
const searchFilters = computed(() => [
  { key: 'action', label: '操作类型', type: 'select' as const, placeholder: '全部', options: Object.entries(tplActionLabels).map(([value, label]) => ({ label, value })) },
  { key: 'templateName', label: '模板名', type: 'input' as const, placeholder: '模糊查询' },
  { key: 'ip', label: '来源 IP', type: 'input' as const, placeholder: '127.0.0.1' }
])

/** 操作类型标签 */
const actionTypeMeta = (action: string | undefined) => {
  const a = action ?? ''
  const label = actionLabel(a)
  const type = a === 'design' ? 'primary' : a === 'print' ? 'success' : 'info'
  return { label, type }
}

/** 加载日志 */
async function loadLogs({ page, pageSize, params }: ReTableLoadParams): Promise<RePageResult> {
  try {
    const res = await templateApi.logs({
      page,
      size: pageSize,
      action: (params.action as 'preview' | 'design' | 'print') || undefined,
      ip: (params.ip as string) || undefined,
      templateName: (params.templateName as string) || undefined,
      templateId: (params.templateId as string) || undefined
    })
    return { total: Number(res?.total ?? 0), records: (res?.records ?? []) as unknown as Record<string, unknown>[] }
  } catch (err) {
    ElMessage.error(String(err))
    return { total: 0, records: [] }
  }
}

const columns: ReTableColumn[] = [
  { prop: 'actionType', label: '操作类型', width: 110, slot: 'actionType', align: 'center' },
  { prop: 'templateName', label: '模板', minWidth: 150 },
  { prop: 'parameters', label: '调用参数', minWidth: 200, slot: 'parameters' },
  { prop: 'time', label: '时间', width: 170, slot: 'time' },
  { prop: 'ip', label: '来源 IP', width: 130 },
  { prop: 'operator', label: '操作人', width: 100, slot: 'operator' }
]

/** 恢复初始查询条件 */
function handleReset(): void {
  query.value = {
    templateName: '',
    templateId: '',
    action: '',
    ip: '',
    page: 1,
    size: 10
  }
  void tableRef.value?.reload()
}
</script>

<style lang="scss" scoped>
.log-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
  overflow: hidden;
}

/* 操作日志空态：复用于 ReTable 的 `#empty` 插槽 */
.page-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 24px;

  b {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.page-empty__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 12px;
  opacity: 0.9;
}

.page-empty__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.page-empty__desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mono {
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
}
</style>
