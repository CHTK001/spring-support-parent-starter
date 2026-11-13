<!--
  密钥管理 · 独立页面（只管密钥本身）

  与账号资产页的关系：
    - 这里是密钥的唯一管理入口：新增 / 编辑 / 启停 / 删除，都在这一页完成；
    - 账号资产页只是「引用」密钥：账号表单选密钥时按类型从这里挑一条，
      账号本身不再存 AccessKey 明文；
    - 因此本页不出现账号 / AppKey / Cookie 的分段，只有密钥。

  版式（对齐模板资产页）：
    - 顶部操作条：标题 + 总数、搜索、类型筛选（全部 / 短信 / 邮件 / 对象存储 / 支付 / 通用 API / 其他，带数量）、刷新、新建密钥；
    - 左栏 300px 密钥列表：名称、类型、厂商、AccessKey 尾段、启用状态点；
    - 右栏详情：凭据状态卡 + 字段明细，底部左下删除、右下编辑。
-->
<template>
  <div class="secret-assets">
    <!-- ==================== 顶部操作条 ==================== -->
    <header class="bar">
      <div class="bar__title">
        <h1>密钥管理</h1>
        <span class="bar__count">{{ filtered.length }} / {{ secrets.length }}</span>
      </div>

      <div class="bar__search">
        <ReInput
          v-model="keyword"
          size="small"
          clearable
          prefix-icon="Search"
          placeholder="搜索名称 / AccessKey / 备注"
        />
      </div>

      <div class="bar__filters">
        <button
          v-for="f in typeFilters"
          :key="f.key"
          type="button"
          class="filter"
          :class="{ 'is-on': typeFilter === f.key }"
          @click="typeFilter = f.key"
        >
          {{ f.label }}
          <span class="filter__n">{{ f.n }}</span>
        </button>
      </div>

      <div class="bar__acts">
        <ReButton
          text
          :show-text="false"
          icon="Refresh"
          tip="刷新密钥列表"
          :icon-size="26"
          :loading="refreshing"
          :disabled="refreshing"
          @click="refresh"
        />
        <ReButton type="primary" icon="Plus" :show-text="true" size="small" @click="openCreate">
          新建密钥
        </ReButton>
      </div>
    </header>

    <!-- ==================== 主体：左列表 + 右详情 ==================== -->
    <div class="body">
      <!-- 左栏：密钥列表 -->
      <aside class="rail">
        <div class="rail__list">
          <div v-if="loading && !secrets.length" class="rail__state">加载中…</div>
          <div v-else-if="!filtered.length" class="rail__state">
            {{ keyword || typeFilter !== 'all' ? '没有匹配的密钥' : '暂无密钥，点右上角「新建密钥」创建' }}
          </div>
          <button
            v-for="item in filtered"
            v-else
            :key="item.secretId"
            type="button"
            class="entry"
            :class="{ 'is-active': selected?.secretId === item.secretId, 'is-off': item.status === 0 }"
            @click="select(item)"
          >
            <span class="entry__mark" :class="`is-${typeKey(item)}`">
              <ReIcon :name="typeIcon(item)" :size="15" />
            </span>
            <span class="entry__main">
              <span class="entry__name-row">
                <span class="entry__name" :title="item.name">{{ item.name || '未命名密钥' }}</span>
                <span
                  class="entry__dot"
                  :class="item.status === 0 ? 'is-off' : item.hasSecret ? 'is-on' : 'is-warn'"
                  :title="item.status === 0 ? '已停用' : item.hasSecret ? '凭据已配置' : 'Secret 待补'"
                />
              </span>
              <span class="entry__code mono">
                {{ typeLabel(item.secretType) }} · {{ item.provider || 'custom' }}
                {{ item.accessKey ? ` · ${maskAccessKey(item.accessKey)}` : '' }}
              </span>
            </span>
            <span class="entry__side">
              <span class="chip" :class="{ 'is-warn': !item.hasSecret }">
                {{ item.hasSecret ? '已配置' : '待补' }}
              </span>
            </span>
          </button>
        </div>

        <footer class="rail__foot">
          <span>共 {{ secrets.length }} 条密钥</span>
        </footer>
      </aside>

      <!-- 右栏：详情 -->
      <section class="detail">
        <template v-if="selected">
          <header class="detail__head">
            <span class="detail__mark" :class="`is-${typeKey(selected)}`">
              <ReIcon :name="typeIcon(selected)" :size="16" />
            </span>
            <div class="detail__title">
              <h2 :title="selected.name">{{ selected.name || '未命名密钥' }}</h2>
              <p>
                {{ typeLabel(selected.secretType) }} · {{ selected.provider || 'custom' }}
                <span v-if="selected.accessKey" class="mono"> · {{ selected.accessKey }}</span>
              </p>
            </div>
            <ReTag :type="selected.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ selected.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <div class="detail__body">
            <div class="cred-card" :class="selected.hasSecret ? 'is-ok' : 'is-warn'">
              <ReIcon :name="selected.hasSecret ? 'CircleCheckFilled' : 'WarningFilled'" :size="16" />
              <div>
                <b>{{ selected.hasSecret ? 'AccessKeySecret 已配置' : 'AccessKeySecret 尚未配置' }}</b>
                <span>Secret 只进不出：读取接口只返回「是否已配置」，编辑时留空表示不修改。</span>
              </div>
            </div>

            <div class="facts">
              <div class="fact">
                <span class="k">密钥类型</span>
                <span class="v">{{ typeLabel(selected.secretType) }}</span>
              </div>
              <div class="fact">
                <span class="k">用途 / 厂商</span>
                <span class="v">{{ selected.provider || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">AccessKeyId</span>
                <span class="v mono">{{ selected.accessKey || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">Region</span>
                <span class="v">{{ selected.region || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">服务地址</span>
                <span class="v mono">{{ selected.endpoint || '厂商默认' }}</span>
              </div>
              <div class="fact">
                <span class="k">归属</span>
                <span class="v">{{ selected.owner ? `仅本人（${selected.owner}）` : '部署级共享' }}</span>
              </div>
              <div class="fact">
                <span class="k">创建时间</span>
                <span class="v">{{ formatTime(selected.createdAt) }}</span>
              </div>
              <div class="fact">
                <span class="k">更新时间</span>
                <span class="v">{{ formatTime(selected.updatedAt) }}</span>
              </div>
              <div class="fact">
                <span class="k">备注</span>
                <span class="v">{{ selected.remark || '—' }}</span>
              </div>
            </div>
          </div>

          <footer class="detail__foot">
            <ReButton
              text
              :show-text="false"
              icon="Delete"
              tip="删除密钥"
              :icon-size="28"
              type="danger"
              @click="removeSelected"
            />
            <span class="detail__foot-right">
              <ReButton
                text
                :show-text="false"
                icon="SwitchButton"
                :tip="selected.status === 0 ? '启用' : '停用'"
                :icon-size="28"
                @click="toggleStatus"
              />
              <ReButton
                text
                :show-text="false"
                icon="EditPen"
                tip="编辑密钥"
                :icon-size="28"
                @click="openEdit(selected)"
              />
            </span>
          </footer>
        </template>

        <!-- 空态 -->
        <div v-else class="detail__empty">
          <span class="detail__empty-mark"><ReIcon name="Lock" :size="24" /></span>
          <b>还没有选择密钥</b>
          <span>从左侧选择密钥查看详情；点右上角「新建密钥」创建</span>
        </div>
      </section>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <SecretDialog v-model="formVisible" :secret="editing" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
/**
 * 密钥管理 —— 密钥的唯一管理入口。
 *
 * <p>密钥有两个维度：{@code secretType}（拿来干什么：短信 / 邮件 / 对象存储 / 支付 /
 * 通用 API）与 {@code provider}（谁的密钥：阿里云 / 腾讯云）。账号引用密钥时按类型挑选，
 * 因此本页提供类型筛选与数量统计；账号侧的选择器同样按类型过滤。</p>
 *
 * <p>Secret 只进不出：列表与详情只显示「是否已配置」，明文仅在新增/替换时由后端落库。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { message } from '@repo/core'
import ReButton from '@repo/ui/re-button'
import ReIcon from '@repo/ui/re-icon'
import ReInput from '@repo/ui/re-input'
import ReTag from '@repo/ui/re-tag'

import {
  deleteAccountSecret,
  listAccountSecrets,
  updateAccountSecret,
  type SysAccountSecret,
} from '../api/account'
import { errorText } from '../../shared/errorText'
import { confirmThen } from '../../shared/confirmThen'
import SecretDialog from './components/SecretDialog.vue'

/** 密钥类型：后端 secretType 的取值集合 */
export type SecretType = 'sms' | 'mail' | 'storage' | 'payment' | 'api' | 'other'

/** 类型中文名 */
const TYPE_LABELS: Record<SecretType, string> = {
  sms: '短信',
  mail: '邮件',
  storage: '对象存储',
  payment: '支付',
  api: '通用 API',
  other: '其他',
}

/** 类型图标 */
const TYPE_ICONS: Record<SecretType, string> = {
  sms: 'ChatDotRound',
  mail: 'Message',
  storage: 'FolderOpened',
  payment: 'Wallet',
  api: 'Connection',
  other: 'Lock',
}

/** 类型顺序（筛选条按此排列） */
const TYPE_ORDER: SecretType[] = ['sms', 'mail', 'storage', 'payment', 'api', 'other']

/** 密钥列表 */
const secrets = ref<SysAccountSecret[]>([])
const loading = ref(false)
const refreshing = ref(false)

/** 搜索词 */
const keyword = ref('')
/** 类型筛选：all 或具体类型 */
const typeFilter = ref<'all' | SecretType>('all')

/** 当前选中的密钥 */
const selected = ref<SysAccountSecret | null>(null)
/** 新建 / 编辑弹窗 */
const formVisible = ref(false)
const editing = ref<SysAccountSecret | null>(null)

/** 类型取值：空值归为 other */
function typeKey(item: SysAccountSecret): SecretType {
  const raw = String(item.secretType || '').toLowerCase() as SecretType
  return TYPE_ORDER.includes(raw) ? raw : 'other'
}

/** 类型中文名 */
function typeLabel(secretType?: string | null): string {
  const raw = String(secretType || '').toLowerCase() as SecretType
  return TYPE_LABELS[TYPE_ORDER.includes(raw) ? raw : 'other']
}

/** 类型图标 */
function typeIcon(item: SysAccountSecret): string {
  return TYPE_ICONS[typeKey(item)]
}

/** AccessKey 尾段展示（首 4 位 + 尾 4 位） */
function maskAccessKey(accessKey: string): string {
  if (accessKey.length <= 10) return accessKey
  return `${accessKey.slice(0, 4)}…${accessKey.slice(-4)}`
}

/** 类型筛选条（带数量） */
const typeFilters = computed(() => {
  const count = (t: SecretType) => secrets.value.filter((s) => typeKey(s) === t).length
  return [
    { key: 'all' as const, label: '全部', n: secrets.value.length },
    ...TYPE_ORDER.map((t) => ({ key: t, label: TYPE_LABELS[t], n: count(t) }))
  ]
})

/** 过滤后的列表 */
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return secrets.value.filter((item) => {
    if (typeFilter.value !== 'all' && typeKey(item) !== typeFilter.value) return false
    if (!k) return true
    return `${item.name || ''} ${item.accessKey || ''} ${item.remark || ''}`.toLowerCase().includes(k)
  })
})

/** 加载密钥列表 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const list = await listAccountSecrets()
    secrets.value = Array.isArray(list) ? list : []
    // 选中项同步刷新（状态 / 备注可能已被别处改动）
    if (selected.value) {
      const fresh = secrets.value.find((s) => s.secretId === selected.value?.secretId)
      selected.value = fresh ?? null
    }
  } catch (err) {
    message.error(errorText(err))
  } finally {
    loading.value = false
  }
}

/** 刷新 */
async function refresh(): Promise<void> {
  refreshing.value = true
  try {
    await load()
  } finally {
    refreshing.value = false
  }
}

/** 选中密钥 */
function select(item: SysAccountSecret): void {
  selected.value = item
}

/** 新建 */
function openCreate(): void {
  editing.value = null
  formVisible.value = true
}

/** 编辑 */
function openEdit(item: SysAccountSecret): void {
  editing.value = item
  formVisible.value = true
}

/** 保存成功后刷新并定位 */
function onSaved(): void {
  void load()
}

/** 启用 / 停用 */
async function toggleStatus(): Promise<void> {
  const item = selected.value
  if (!item) return
  try {
    await updateAccountSecret(item.secretId, { ...item, status: item.status === 0 ? 1 : 0 })
    message.success(item.status === 0 ? '已启用' : '已停用')
    await load()
  } catch (err) {
    message.error(errorText(err))
  }
}

/** 删除 */
async function removeSelected(): Promise<void> {
  const item = selected.value
  if (!item) return
  const ok = await confirmThen(
    message.confirm,
    `删除密钥「${item.name || item.accessKey || '未命名密钥'}」？引用它的账号将失去凭据来源。`,
    '删除密钥',
  )
  if (!ok) return
  try {
    await deleteAccountSecret(item.secretId)
    message.success('密钥已删除')
    selected.value = null
    await load()
  } catch (err) {
    message.error(errorText(err))
  }
}

/** 时间格式化 */
function formatTime(value?: number | null): string {
  if (!value) return '—'
  const d = new Date(value)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(load)
</script>

<style lang="scss" scoped>
.secret-assets {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  color: var(--text-primary, var(--el-text-color-primary));
}

/* ==================== 顶部操作条 ==================== */
.bar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.bar__title {
  display: flex;
  align-items: baseline;
  gap: 8px;

  h1 {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
    letter-spacing: 0.01em;
  }
}

.bar__count {
  padding: 0 7px;
  font-size: 11px;
  font-weight: 600;
  line-height: 17px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.bar__search {
  width: 220px;
  flex: none;

  :deep(.el-input) {
    width: 100%;
  }
}

.bar__filters {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 4px;
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  background: none;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--el-color-primary);
  }

  &.is-on {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
  }
}

.filter__n {
  font-size: 10px;
  opacity: 0.65;
}

.bar__acts {
  display: flex;
  flex: none;
  align-items: center;
  gap: 4px;
}

/* ==================== 主体 ==================== */
.body {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

.rail {
  display: flex;
  flex: 0 0 300px;
  flex-direction: column;
  width: 300px;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.rail__list {
  flex: 1;
  min-height: 0;
  padding: 6px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.rail__state {
  padding: 28px 12px;
  font-size: 12px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.rail__foot {
  flex: none;
  padding: 4px 10px;
  font-size: 11px;
  color: var(--text-tertiary);
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

/* ---------- 条目 ---------- */
.entry {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 8px;
  font: inherit;
  text-align: left;
  background: none;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;

  & + & {
    margin-top: 1px;
  }

  &:hover {
    background: var(--bg-fill);
  }

  &.is-active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);

    .entry__name {
      color: var(--el-color-primary);
    }
  }

  &.is-off {
    opacity: 0.62;
  }
}

.entry__mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 8px;

  &.is-mail {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &.is-storage {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &.is-payment {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }

  &.is-other {
    color: var(--el-text-color-secondary);
    background: var(--bg-fill);
  }
}

.entry__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.entry__name-row {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.entry__name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entry__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-on {
    background: var(--el-color-success);
  }

  &.is-off {
    background: var(--el-color-danger);
  }

  &.is-warn {
    background: var(--el-color-warning);
  }
}

.entry__code {
  overflow: hidden;
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-tertiary);
}

.entry__side {
  display: flex;
  flex: none;
  align-items: center;
}

.chip {
  padding: 0 7px;
  font-size: 11px;
  line-height: 18px;
  color: var(--text-secondary);
  background: var(--bg-fill);
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 999px;
  white-space: nowrap;

  &.is-warn {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-8);
  }
}

/* ==================== 右栏详情 ==================== */
.detail {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.detail__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 40px 20px;
  color: var(--text-tertiary);

  b {
    font-size: 14px;
    color: var(--text-secondary);
  }

  span {
    font-size: 12px;
  }
}

.detail__empty-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 4px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 12px;
}

.detail__head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.detail__mark {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 9px;

  &.is-mail {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &.is-storage {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &.is-payment {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }

  &.is-other {
    color: var(--el-text-color-secondary);
    background: var(--bg-fill);
  }
}

.detail__title {
  flex: 1;
  min-width: 0;

  h2 {
    margin: 0;
    overflow: hidden;
    font-size: 15px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  p {
    margin: 2px 0 0;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text-tertiary);
  }
}

.detail__body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  overflow: auto;
}

.cred-card {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--border-subtle, var(--border-default));

  b {
    display: block;
    font-size: 13px;
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }

  &.is-ok {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-8);
  }

  &.is-warn {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-8);
  }
}

.facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin-top: 12px;
  overflow: hidden;
  background: var(--border-subtle, var(--border-default));
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 8px;
}

.fact {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 12px;
  background: var(--bg-container);

  .k {
    flex: none;
    width: 88px;
    font-size: 12px;
    color: var(--text-tertiary);
  }

  .v {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.detail__foot {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px 6px 8px;
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

.detail__foot-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
}
</style>
