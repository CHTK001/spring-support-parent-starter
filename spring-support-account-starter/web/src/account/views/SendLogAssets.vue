<!--
  发送日志 · 独立页面（邮件 / 短信通道级发送事实）

  与账号资产页 / 密钥管理页的关系：
    - 这里只看「发出去的事实」：每次真实发送（成功或失败）都由 MailboxService
      发信门面与 system 消息推送侧异步落一条通道级日志；
    - 业务侧留痕（验证码日志 / 消息推送日志）仍在各业务表，两边维度互补。

  版式（对齐密钥管理页）：
    - 顶部操作条：标题 + 总数、关键字 / 收件人搜索、种类 / 结果 / 时间筛选、刷新、清理；
    - 左栏 300px 日志列表（可拖拽调宽，服务端分页滚动追加）；
    - 右栏详情：结果卡 + 字段明细 + 完整正文 / 失败原因。
-->
<template>
  <div class="sendlog-assets">
    <!-- ==================== 顶部操作条 ==================== -->
    <header class="bar">
      <div class="bar__title">
        <h1>发送日志</h1>
        <span class="bar__count">{{ total }}</span>
      </div>

      <div class="bar__search">
        <ReInput
          v-model="keyword"
          size="small"
          clearable
          prefix-icon="Search"
          placeholder="搜索主题 / 正文 / 失败原因"
        />
      </div>

      <div class="bar__search bar__search--target">
        <ReInput
          v-model="target"
          size="small"
          clearable
          prefix-icon="User"
          placeholder="收件人"
        />
      </div>

      <div class="bar__filters" role="group" aria-label="发送日志筛选">
        <button
          v-for="f in kindFilters"
          :key="f.key"
          type="button"
          class="filter"
          :class="{ 'is-on': kindFilter === f.key }"
          @click="kindFilter = f.key"
        >
          {{ f.label }}
        </button>

        <span class="bar__sep" aria-hidden="true"></span>

        <button
          v-for="f in resultFilters"
          :key="f.key"
          type="button"
          class="filter"
          :class="{ 'is-on': resultFilter === f.key }"
          @click="resultFilter = f.key"
        >
          {{ f.label }}
        </button>

        <span class="bar__sep" aria-hidden="true"></span>

        <button
          v-for="f in timeFilters"
          :key="f.key"
          type="button"
          class="filter"
          :class="{ 'is-on': timeFilter === f.key }"
          @click="timeFilter = f.key"
        >
          {{ f.label }}
        </button>
      </div>

      <div class="bar__acts">
        <ReButton
          text
          :show-text="false"
          icon="Refresh"
          tip="刷新发送日志"
          :icon-size="26"
          :loading="refreshing"
          :disabled="refreshing"
          @click="refresh"
        />
        <ReButton type="danger" icon="Delete" size="small" @click="openClear">
          清理…
        </ReButton>
      </div>
    </header>

    <!-- ==================== 主体：左列表 + 右详情 ==================== -->
    <div class="body" :style="{ '--rail-width': railWidth + 'px' }">
      <!-- 左栏：日志列表（服务端分页，滚动追加） -->
      <aside class="rail">
        <div class="rail-head">
          <span class="rail-head__dot" aria-hidden="true"></span>
          <span class="rail-head__title">发送记录</span>
          <span class="rail-head__count">{{ logs.length }} / {{ total }}</span>
        </div>

        <RailScrollList
          :loading="loading"
          :has-more="hasMore"
          :item-count="logs.length"
          :total="total"
          :threshold="48"
          @load-more="loadMore"
        >
          <div class="rail__list">
          <div v-if="loading && !logs.length" class="rail__state">加载中…</div>
          <div v-else-if="!logs.length" class="rail__state">{{ emptyText }}</div>
          <template v-else>
            <button
              v-for="item in logs"
              :key="item.sendLogId"
              type="button"
              class="entry"
              :class="{
                'is-active': selected?.sendLogId === item.sendLogId,
                'is-fail': item.sendLogSuccess === false,
              }"
              @click="select(item)"
            >
              <span class="entry__mark" :class="item.sendLogKind === 'SMS' ? 'is-sms' : 'is-mail'">
                <ReIcon :name="kindIcon(item.sendLogKind)" :size="15" />
              </span>
              <span class="entry__main">
                <span class="entry__name-row">
                  <span class="entry__name" :title="entryTitle(item)">{{ entryTitle(item) }}</span>
                  <span
                    class="entry__dot"
                    :class="item.sendLogSuccess ? 'is-on' : 'is-off'"
                    :title="item.sendLogSuccess ? '发送成功' : '发送失败'"
                  />
                </span>
                <span class="entry__code mono">
                  {{ kindLabel(item.sendLogKind) }} · {{ providerLabel(item.sendLogProvider) }} ·
                  {{ formatTime(item.sendLogTime) }}
                </span>
              </span>
              <span class="entry__side">
                <span class="chip" :class="{ 'is-fail': item.sendLogSuccess === false }">
                  {{ item.sendLogSuccess ? '成功' : '失败' }}
                </span>
              </span>
            </button>
          </template>
          </div>
        </RailScrollList>
      </aside>

      <!-- 分栏手柄：拖拽改左栏宽度，双击复位，偏好存本地 -->
      <ReRailSplit
        v-model:width="railWidth"
        class="sendlog-split"
        storage-key="sendlog.rail.width.v1"
        :default-width="300"
        label="拖拽调整发送日志列表宽度"
      />

      <!-- 右栏：详情 -->
      <section class="detail">
        <template v-if="selected">
          <header class="detail__head">
            <span class="detail__mark" :class="selected.sendLogKind === 'SMS' ? 'is-sms' : 'is-mail'">
              <ReIcon :name="kindIcon(selected.sendLogKind)" :size="16" />
            </span>
            <div class="detail__title">
              <h2 :title="entryTitle(selected)">{{ entryTitle(selected) }}</h2>
              <p>
                {{ kindLabel(selected.sendLogKind) }} · {{ providerLabel(selected.sendLogProvider) }} ·
                {{ formatTime(selected.sendLogTime) }}
              </p>
            </div>
            <ReTag :type="selected.sendLogSuccess ? 'success' : 'danger'" effect="plain" size="small">
              {{ selected.sendLogSuccess ? '成功' : '失败' }}
            </ReTag>
          </header>

          <div class="detail__body">
            <!-- 结果卡：成功给上游标识，失败给原因 -->
            <div class="cred-card" :class="selected.sendLogSuccess ? 'is-ok' : 'is-warn'">
              <ReIcon :name="selected.sendLogSuccess ? 'CircleCheckFilled' : 'WarningFilled'" :size="16" />
              <div>
                <b>{{ selected.sendLogSuccess ? '发送成功' : '发送失败' }}</b>
                <span>
                  {{
                    selected.sendLogSuccess
                      ? `上游已受理${selected.sendLogMessageId ? `，消息标识 ${selected.sendLogMessageId}` : ''}。`
                      : selected.sendLogError || '上游返回失败，未记录明确原因。'
                  }}
                </span>
              </div>
            </div>

            <div class="facts">
              <div class="fact">
                <span class="k">收件人</span>
                <span class="v mono">{{ selected.sendLogTarget || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">抄送</span>
                <span class="v mono">{{ selected.sendLogCc || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">发信账号</span>
                <span class="v">
                  {{ selected.sendLogAccountName || '—' }}
                  <span v-if="selected.sendLogAccountId" class="v-code mono"> · {{ selected.sendLogAccountId }}</span>
                </span>
              </div>
              <div class="fact">
                <span class="k">发送通道</span>
                <span class="v">{{ providerLabel(selected.sendLogProvider) }}</span>
              </div>
              <div class="fact">
                <span class="k">模板编码</span>
                <span class="v mono">{{ selected.sendLogTemplateCode || '—' }}</span>
              </div>
              <div class="fact">
                <span class="k">耗时</span>
                <span class="v">{{ costText(selected.sendLogCostMs) }}</span>
              </div>
              <div class="fact">
                <span class="k">调用来源</span>
                <span class="v">{{ sourceLabel(selected.sendLogSource) }}</span>
              </div>
              <div class="fact">
                <span class="k">发送时间</span>
                <span class="v">{{ formatTime(selected.sendLogTime) }}</span>
              </div>
              <div class="fact">
                <span class="k">写入时间</span>
                <span class="v">{{ formatTime(selected.sendLogCreatedAt) }}</span>
              </div>
            </div>

            <div v-if="selected.sendLogSubject" class="content-block">
              <h4 class="content-title">主题</h4>
              <p class="content-subject">{{ selected.sendLogSubject }}</p>
            </div>

            <div v-if="selected.sendLogContent" class="content-block">
              <h4 class="content-title">正文</h4>
              <pre class="content-pre">{{ selected.sendLogContent }}</pre>
            </div>
          </div>
        </template>

        <!-- 空态 -->
        <div v-else class="detail__empty">
          <span class="detail__empty-mark"><ReIcon name="Document" :size="24" /></span>
          <b>还没有选择日志</b>
          <span>从左侧选择一条发送记录，查看完整正文与上游结果</span>
        </div>
      </section>
    </div>

    <!-- 清理弹窗：按截止时间批量删除（后端必填 before 防误清全表） -->
    <ReDialog v-model="clearVisible" title="清理发送日志" width="460px" append-to-body destroy-on-close>
      <div class="clear-box">
        <p class="clear-tip">
          将删除 <b>{{ formatTime(clearBefore) }}</b> 之前的全部发送日志，删除后<b>不可恢复</b>。
        </p>
        <label class="clear-field">
          <span>清理截止时间</span>
          <input v-model="clearBefore" type="datetime-local" class="clear-input" />
        </label>
      </div>
      <template #footer>
        <ReButton @click="clearVisible = false">取消</ReButton>
        <ReButton type="danger" :loading="clearLoading" :disabled="clearLoading || !clearBefore" @click="doClear">
          确认清理
        </ReButton>
      </template>
    </ReDialog>
  </div>
</template>

<script setup lang="ts" name="SendLogAssets">
/**
 * 发送日志 —— 邮件 / 短信通道级发送事实的唯一查看入口。
 *
 * <p>数据来自 {@code /api/accounts/send-logs}（服务端分页）：按种类、结果、
 * 时间范围与关键字 / 收件人过滤；列表滚动追加，点条目在右栏看完整正文
 * 与上游结果。清理按截止时间批量删除，弹窗里二次确认。</p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { message } from '@repo/core'
import ReButton from '@repo/ui/re-button'
import ReDialog from '@repo/ui/re-dialog'
import ReIcon from '@repo/ui/re-icon'
import ReInput from '@repo/ui/re-input'
import ReTag from '@repo/ui/re-tag'
import ReRailSplit, { readRailWidth } from '@repo/ui/re-rail-split'

import { clearSendLogs, listSendLogs, type SysAccountSendLog } from '../api/sendlog'
import { errorText } from '../../shared/errorText'
import RailScrollList from '../components/RailScrollList.vue'

/** 左栏宽度偏好键（与账号 / 密钥页同一套分栏） */
const RAIL_WIDTH_KEY = 'sendlog.rail.width.v1'
/** 左栏宽度：可拖拽，双击复位，偏好存本地 */
const railWidth = ref(readRailWidth(RAIL_WIDTH_KEY, 300))

/** 每页条数（服务端分页，滚动追加） */
const PAGE_SIZE = 30

/** 当前页日志 */
const logs = ref<SysAccountSendLog[]>([])
/** 服务端总数 */
const total = ref(0)
/** 下一页页码 */
const nextPage = ref(1)
/** 是否加载中 */
const loading = ref(false)
/** 刷新按钮 loading */
const refreshing = ref(false)
/** 选中日志（右栏详情） */
const selected = ref<SysAccountSendLog | null>(null)

/** 关键字：主题 / 正文 / 失败原因 */
const keyword = ref('')
/** 收件人模糊匹配 */
const target = ref('')
/** 种类筛选 */
const kindFilter = ref<'all' | 'EMAIL' | 'SMS'>('all')
/** 结果筛选 */
const resultFilter = ref<'all' | 'success' | 'fail'>('all')
/** 时间范围筛选 */
const timeFilter = ref<'all' | 'today' | '7d' | '30d'>('all')

/** 种类筛选项 */
const kindFilters = [
  { key: 'all', label: '全部' },
  { key: 'EMAIL', label: '邮件' },
  { key: 'SMS', label: '短信' },
] as const

/** 结果筛选项 */
const resultFilters = [
  { key: 'all', label: '全部结果' },
  { key: 'success', label: '成功' },
  { key: 'fail', label: '失败' },
] as const

/** 时间范围筛选项 */
const timeFilters = [
  { key: 'all', label: '全部时间' },
  { key: 'today', label: '今天' },
  { key: '7d', label: '近 7 天' },
  { key: '30d', label: '近 30 天' },
] as const

/** 是否还有更多页 */
const hasMore = computed(() => logs.value.length < total.value)

/** 列表空态文案 */
const emptyText = computed(() =>
  keyword.value.trim() || target.value.trim() || kindFilter.value !== 'all' || resultFilter.value !== 'all' || timeFilter.value !== 'all'
    ? '没有匹配的发送记录'
    : '暂无发送记录：发信后会在这里留下通道级日志',
)

/** 两位补零 */
const pad = (n: number) => String(n).padStart(2, '0')

/**
 * 解析后端时间字段。
 *
 * <p>后端各实体时间序列化形状不统一（epoch 毫秒 / ISO 字符串 /
 * LocalDateTime 数组），这里统一归一成 {@code Date}，解析失败返回 {@code null}。</p>
 */
function parseTime(value?: string | number | number[] | null): Date | null {
  if (value === null || value === undefined || value === '') return null
  if (Array.isArray(value)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = value
    if (!y || !m || !d) return null
    return new Date(y, m - 1, d, h, mi, s)
  }
  if (typeof value === 'number') return new Date(value)
  const t = Date.parse(value)
  return Number.isNaN(t) ? null : new Date(t)
}

/** 时间格式化（yyyy-MM-dd HH:mm:ss） */
function formatTime(value?: string | number | number[] | null): string {
  const d = parseTime(value)
  if (!d) return '—'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 时间范围筛选 → 查询参数（ISO 本地时间，当天 / 近 N 天从零点起） */
function timeRange(): { startTime?: string; endTime?: string } {
  const now = new Date()
  const dayStart = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T00:00:00`
  switch (timeFilter.value) {
    case 'today':
      return { startTime: dayStart(now) }
    case '7d':
      return { startTime: dayStart(new Date(now.getTime() - 6 * 86_400_000)) }
    case '30d':
      return { startTime: dayStart(new Date(now.getTime() - 29 * 86_400_000)) }
    default:
      return {}
  }
}

/** 种类中文名 */
function kindLabel(kind?: string | null): string {
  if (kind === 'SMS') return '短信'
  if (kind === 'EMAIL') return '邮件'
  return kind || '—'
}

/** 种类图标 */
function kindIcon(kind?: string | null): string {
  return kind === 'SMS' ? 'ChatDotRound' : 'Message'
}

/** 通道中文名（未知通道原样展示） */
function providerLabel(provider?: string | null): string {
  const map: Record<string, string> = {
    email: '邮件 SMTP',
    'alibaba-sms': '阿里云短信',
    'tencent-sms': '腾讯云短信',
    'baidu-sms': '百度短信',
  }
  if (!provider) return '—'
  return map[provider] ?? provider
}

/** 调用来源中文名（未知来源原样展示） */
function sourceLabel(source?: string | null): string {
  const map: Record<string, string> = {
    mailbox: '信箱发信',
    'system:sms-code': '验证码',
    'system:message-push': '消息推送',
  }
  if (!source) return '—'
  return map[source] ?? source
}

/** 列表条目标题：主题优先，短信退回正文摘要 */
function entryTitle(item: SysAccountSendLog): string {
  if (item.sendLogSubject) return item.sendLogSubject
  if (item.sendLogContent) {
    const text = item.sendLogContent.replace(/\s+/g, ' ').trim()
    if (text) return text.length > 42 ? `${text.slice(0, 42)}…` : text
  }
  return item.sendLogTarget || '（无主题）'
}

/** 耗时文案 */
function costText(cost?: number | null): string {
  if (cost === null || cost === undefined) return '—'
  return cost >= 1000 ? `${(cost / 1000).toFixed(2)} s` : `${cost} ms`
}

/** 请求序号：筛选切换与滚动追加并发时，丢弃过期响应 */
let requestSeq = 0

/**
 * 加载一页日志。
 *
 * @param reset true 从第一页重取（替换列表）；false 追加下一页
 */
async function load(reset: boolean): Promise<void> {
  const seq = ++requestSeq
  if (reset) nextPage.value = 1
  loading.value = true
  try {
    const res = await listSendLogs({
      kind: kindFilter.value === 'all' ? undefined : kindFilter.value,
      success: resultFilter.value === 'all' ? undefined : resultFilter.value === 'success',
      keyword: keyword.value.trim() || undefined,
      target: target.value.trim() || undefined,
      ...timeRange(),
      page: nextPage.value,
      pageSize: PAGE_SIZE,
    })
    if (seq !== requestSeq) return
    total.value = Number(res?.total ?? 0)
    const records = res?.records ?? []
    logs.value = reset ? records : [...logs.value, ...records]
  } catch (err) {
    if (seq === requestSeq) message.error(errorText(err))
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

/** 追加下一页 */
function loadMore(): void {
  if (loading.value || !hasMore.value) return
  nextPage.value += 1
  void load(false)
}

/** 选中一条日志（右栏展示详情） */
function select(item: SysAccountSendLog): void {
  selected.value = item
}

/** 刷新（回到第一页） */
async function refresh(): Promise<void> {
  refreshing.value = true
  try {
    await load(true)
  } finally {
    refreshing.value = false
  }
}

/** 关键字 / 收件人输入防抖后重查 */
let inputTimer: ReturnType<typeof setTimeout> | undefined
watch([keyword, target], () => {
  if (inputTimer) clearTimeout(inputTimer)
  inputTimer = setTimeout(() => void load(true), 300)
})

/** 种类 / 结果 / 时间筛选切换立即重查 */
watch([kindFilter, resultFilter, timeFilter], () => void load(true))

/** 清理弹窗 */
const clearVisible = ref(false)
/** 清理截止时间（datetime-local，精确到分） */
const clearBefore = ref('')
/** 清理请求 loading */
const clearLoading = ref(false)

/** 打开清理弹窗：默认清 30 天前的日志 */
function openClear(): void {
  const d = new Date(Date.now() - 30 * 86_400_000)
  clearBefore.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  clearVisible.value = true
}

/** 执行清理：删除截止时间之前的日志，成功后刷新列表 */
async function doClear(): Promise<void> {
  if (!clearBefore.value) {
    message.warning('请先选择清理截止时间')
    return
  }
  clearLoading.value = true
  try {
    const removed = await clearSendLogs(clearBefore.value)
    message.success(`已清理 ${Number(removed ?? 0)} 条发送日志`)
    clearVisible.value = false
    selected.value = null
    await load(true)
  } catch (err) {
    message.error(errorText(err))
  } finally {
    clearLoading.value = false
  }
}

onMounted(() => void load(true))
</script>

<style lang="scss" scoped>
.sendlog-assets {
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
  font-variant-numeric: tabular-nums;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.bar__search {
  width: 210px;
  flex: none;

  :deep(.el-input) {
    width: 100%;
  }

  &--target {
    width: 150px;
  }
}

.bar__filters {
  display: flex;
  flex: 1;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.bar__sep {
  flex: none;
  width: 1px;
  height: 14px;
  margin: 0 3px;
  background: var(--border-default);
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  margin: 0;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
  color: var(--el-text-color-regular);
  background: none;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  box-shadow: none;
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
  /* 间距交给分栏手柄本身，避免 gap + 手柄叠出一段空档 */
  gap: 0;
  min-height: 0;
}

.rail {
  display: flex;
  flex: none;
  flex-direction: column;
  /* 宽度由分栏手柄写入 --rail-width（可拖拽，存本地偏好） */
  width: var(--rail-width, 300px);
  min-height: 0;
  overflow: hidden;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

/* 左栏栏头：主色圆点 + 栏名 + 数量（与侧栏分组标题同一套装饰语言） */
.rail-head {
  flex: none;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 12px 8px;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.rail-head__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
}

.rail-head__title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-secondary, var(--el-text-color-secondary));
}

.rail-head__count {
  margin-left: auto;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--text-tertiary);
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

  &.is-sms {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
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

  &.is-fail {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-8);
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

  &.is-sms {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
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
    width: 72px;
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

.v-code {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* ---------- 正文 / 失败原因 ---------- */
.content-block {
  margin-top: 12px;
}

.content-title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.content-subject {
  margin: 0;
  padding: 9px 12px;
  font-size: 13px;
  background: var(--bg-fill);
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 8px;
}

.content-pre {
  max-height: 320px;
  margin: 0;
  padding: 9px 12px;
  overflow: auto;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary, var(--el-text-color-primary));
  background: var(--bg-fill);
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 8px;
}

/* ---------- 清理弹窗 ---------- */
.clear-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.clear-tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);

  b {
    color: var(--el-color-danger);
  }
}

.clear-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);

  > span {
    font-weight: 600;
  }
}

.clear-input {
  padding: 6px 10px;
  font: inherit;
  font-size: 13px;
  color: var(--text-primary, var(--el-text-color-primary));
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm, 6px);
  outline: none;

  &:focus {
    border-color: var(--el-color-primary);
  }
}

.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
}

/* 窄屏降为上下结构，收起左右分栏手柄 */
@media (max-width: 940px) {
  .body {
    flex-direction: column;
  }
  .rail {
    width: auto;
    max-height: 340px;
  }
  .sendlog-split {
    display: none;
  }
}
</style>
