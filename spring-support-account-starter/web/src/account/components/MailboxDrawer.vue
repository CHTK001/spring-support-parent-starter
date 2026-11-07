<template>
  <ReDrawer
    v-model="visible"
    :title="drawerTitle"
    size="980"
    append-to-body
    destroy-on-close
    footer-hidden
  >
    <div class="mailbox">
      <!-- 加载概览 -->
      <div v-if="loadingOverview" class="mb-state">
        <ReIcon name="Loading" :size="18" class="spin" />
        <span>正在连接收信服务…</span>
      </div>

      <template v-else>
        <!-- 不可用 / 连接失败：给出明确原因，不留空白抽屉 -->
        <div v-if="overview?.message" class="mb-alert">
          <ReIcon name="Warning" :size="14" />
          <span>{{ overview.message }}</span>
          <a v-if="consoleUrl" class="mb-console" :href="consoleUrl" target="_blank" rel="noopener noreferrer">
            网页版邮箱
          </a>
          <button type="button" class="mb-retry" @click="reload">重试</button>
        </div>

        <div class="mb-body">
          <!-- 文件夹轨 -->
          <aside class="mb-folders">
            <button
              v-for="folder in folders"
              :key="folder.key"
              type="button"
              class="mb-folder"
              :class="{ 'is-on': folder.key === activeFolder }"
              @click="selectFolder(folder.key)"
            >
              <ReIcon :name="folderIcon(folder.role)" :size="14" />
              <span class="mb-folder-name">{{ folder.name }}</span>
              <b v-if="folder.unread" class="mb-folder-badge">{{ folder.unread }}</b>
              <em v-else class="mb-folder-total">{{ folder.total ?? 0 }}</em>
            </button>

            <div v-if="!folders.length" class="mb-folder-hint">
              {{ overview?.supported ? '暂无可用文件夹' : '收信未接入' }}
            </div>

            <div class="mb-folder-foot">
              <ReButton show-text icon="Refresh" size="small" plain :loading="loadingMessages" @click="reload">
                刷新
              </ReButton>
            </div>
          </aside>

          <!-- 主区：列表 / 详情 / 写信 -->
          <section class="mb-main">
            <!-- ==================== 列表 ==================== -->
            <template v-if="mode === 'list'">
              <div class="mb-toolbar">
                <span class="mb-title">
                  {{ activeFolderName }}
                  <em v-if="keyword" class="mb-keyword">“{{ keyword }}”</em>
                </span>
                <label class="mb-search">
                  <ReIcon name="Search" :size="13" />
                  <input v-model="keyword" placeholder="搜索主题 / 发件人" @keyup.enter="reloadMessages" />
                </label>
                <ReButton v-if="overview?.sendable" show-text icon="EditPen" type="primary" @click="startCompose">
                  写信
                </ReButton>
              </div>

              <ReTable
                :data="messages"
                :loading="loadingMessages"
                row-key="id"
                border
                stripe
                class="mb-table"
                empty-text="这个文件夹没有邮件"
                :settings="{ highlightCurrentRow: true }"
                :config="{ pagination: { enable: false }, toolbar: { enable: false } }"
                @row-click="openMessage"
              >
                <ReTableColumn label="发件人" min-width="180">
                  <template #default="{ row }">
                    <div class="mb-from" :class="{ 'is-unread': row.unread }">
                      <i class="mb-dot"></i>
                      <span class="ellipsis" :title="row.from">{{ row.from || '（未知发件人）' }}</span>
                    </div>
                  </template>
                </ReTableColumn>
                <ReTableColumn label="主题" min-width="280">
                  <template #default="{ row }">
                    <div class="mb-subject" :class="{ 'is-unread': row.unread }">
                      <span class="ellipsis" :title="row.subject">{{ row.subject || '（无主题）' }}</span>
                      <ReTag v-if="row.hasAttachment" type="info" size="small" effect="plain">附件</ReTag>
                    </div>
                    <div class="mb-preview ellipsis">{{ row.preview || '—' }}</div>
                  </template>
                </ReTableColumn>
                <ReTableColumn label="时间" width="130" align="right">
                  <template #default="{ row }">
                    <span class="mb-time">{{ formatTime(row.sentAt) }}</span>
                  </template>
                </ReTableColumn>
              </ReTable>

              <div class="mb-more">
                <ReButton v-if="hasMore" plain :loading="loadingMessages" @click="loadMore">加载更多</ReButton>
                <span v-else-if="messages.length" class="mb-end">已到最早一封</span>
              </div>
            </template>

            <!-- ==================== 详情 ==================== -->
            <template v-else-if="mode === 'detail'">
              <div class="mb-toolbar">
                <button type="button" class="mb-back" @click="backToList">
                  <ReIcon name="ArrowLeft" :size="14" />
                  <span>返回</span>
                </button>
                <span class="mb-title ellipsis" :title="activeMessage?.subject">
                  {{ activeMessage?.subject || '（无主题）' }}
                </span>
                <ReButton v-if="overview?.sendable" show-text icon="EditPen" @click="startCompose">
                  写信
                </ReButton>
              </div>

              <div v-if="loadingMessage" class="mb-state">
                <ReIcon name="Loading" :size="18" class="spin" />
                <span>正在读取邮件…</span>
              </div>

              <div v-else-if="activeMessage" class="mb-detail">
                <dl class="mb-head">
                  <div><dt>发件人</dt><dd>{{ activeMessage.from || '—' }}</dd></div>
                  <div><dt>收件人</dt><dd>{{ activeMessage.to || '—' }}</dd></div>
                  <div v-if="activeMessage.cc"><dt>抄送</dt><dd>{{ activeMessage.cc }}</dd></div>
                  <div><dt>时间</dt><dd>{{ formatFullTime(activeMessage.sentAt) }}</dd></div>
                  <div v-if="activeMessage.attachments?.length">
                    <dt>附件</dt>
                    <dd class="mb-attachments">
                      <ReTag v-for="name in activeMessage.attachments" :key="name" type="info" effect="plain">
                        {{ name }}
                      </ReTag>
                    </dd>
                  </div>
                </dl>

                <!-- HTML 邮件放沙箱 iframe（不执行脚本、不继承宿主样式），纯文本按原样折行 -->
                <iframe
                  v-if="activeMessage.contentType === 'html'"
                  class="mb-html"
                  sandbox=""
                  referrerpolicy="no-referrer"
                  :srcdoc="htmlDocument"
                ></iframe>
                <pre v-else class="mb-text">{{ activeMessage.content || activeMessage.preview || '（无正文）' }}</pre>
              </div>
            </template>

            <!-- ==================== 写信 ==================== -->
            <template v-else>
              <div class="mb-toolbar">
                <button type="button" class="mb-back" @click="backToList">
                  <ReIcon name="ArrowLeft" :size="14" />
                  <span>返回</span>
                </button>
                <span class="mb-title">写{{ isEmail ? '邮件' : '短信' }}</span>
              </div>

              <div class="mb-compose">
                <label class="mb-field">
                  <span class="mb-label">{{ isEmail ? '收件人' : '手机号' }}</span>
                  <input v-model="form.to" class="mb-input" :placeholder="isEmail ? 'a@example.com，多个用逗号分隔' : '接收短信的手机号'" />
                </label>
                <label v-if="isEmail" class="mb-field">
                  <span class="mb-label">抄送</span>
                  <input v-model="form.cc" class="mb-input" placeholder="可空，多个用逗号分隔" />
                </label>
                <label v-if="isEmail" class="mb-field">
                  <span class="mb-label">主题</span>
                  <input v-model="form.subject" class="mb-input" placeholder="邮件主题" />
                </label>
                <label v-if="!isEmail" class="mb-field">
                  <span class="mb-label">模板编码</span>
                  <input v-model="form.templateCode" class="mb-input" placeholder="可空；填写后按模板发送" />
                </label>
                <label class="mb-field mb-field--body">
                  <span class="mb-label">正文</span>
                  <textarea v-model="form.content" class="mb-textarea" rows="10" placeholder="正文内容"></textarea>
                </label>

                <div class="mb-compose-foot">
                  <span class="mb-sender">
                    发件账号：{{ overview?.address || account?.username || '—' }}
                    <em v-if="!overview?.sendable">（该账号未配置发信能力）</em>
                  </span>
                  <ReButton
                    type="primary"
                    :loading="sending"
                    :disabled="!overview?.sendable"
                    @click="submitSend"
                  >
                    发送
                  </ReButton>
                </div>
              </div>
            </template>
          </section>
        </div>
      </template>
    </div>
  </ReDrawer>
</template>

<script setup lang="ts">
/**
 * 收 / 发信箱抽屉。
 *
 * <p><b>为什么不是跳转网页版</b>：账号页右键「打开邮箱」要打开的是这个账号<b>真实</b>的
 * 收信箱 / 已发送，而不是邮箱服务商的登录页。收信由后端收信 SPI（IMAP）直连，
 * 发信走 {@code MessagePush}，因此这里没有 {@code window.open}。</p>
 *
 * <p>三种模式共用右侧主区：列表（分页取信）→ 详情（HTML 放沙箱 iframe）→ 写信。
 * 抽屉宽度 980，左栏只放文件夹，主区留给内容，移动端窄屏时自动收窄。</p>
 */
import { computed, reactive, ref, watch } from 'vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDrawer from '@repo/ui/re-drawer';
import ReIcon from '@repo/ui/re-icon';
import ReTable from '@repo/ui/re-table';
import ReTag from '@repo/ui/re-tag';

import {
  getMailboxMessage,
  getMailboxOverview,
  listMailboxMessages,
  sendMailboxMessage,
  type MailboxFolder,
  type MailboxMessage,
  type MailboxOverview,
} from '../api/mailbox';
import type { SysAccount } from '../api/account';

const props = defineProps<{
  modelValue: boolean;
  account?: SysAccount | null;
  /** 登录页地址（兜底提示：IMAP/SMTP 连不通时可去网页版） */
  consoleUrl?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

/** 抽屉显隐双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 每页取多少封 */
const PAGE_SIZE = 20;

/** 概览 */
const overview = ref<MailboxOverview | null>(null);
/** 概览加载中 */
const loadingOverview = ref(false);
/** 文件夹 */
const folders = ref<MailboxFolder[]>([]);
/** 当前文件夹（服务端名） */
const activeFolder = ref('');
/** 列表 */
const messages = ref<MailboxMessage[]>([]);
/** 列表加载中 */
const loadingMessages = ref(false);
/** 是否还有更早的信 */
const hasMore = ref(false);
/** 搜索词 */
const keyword = ref('');
/** 主区模式 */
const mode = ref<'list' | 'detail' | 'compose'>('list');
/** 正在读取的邮件 */
const activeMessage = ref<MailboxMessage | null>(null);
/** 详情加载中 */
const loadingMessage = ref(false);
/** 发送中 */
const sending = ref(false);

/** 写信表单 */
const form = reactive({
  to: '',
  cc: '',
  subject: '',
  content: '',
  templateCode: '',
});

/** 是否邮件账号 */
const isEmail = computed(() => props.account?.kind === 'EMAIL');

/** 抽屉标题 */
const drawerTitle = computed(() => {
  const name = props.account?.name || props.account?.username || '';
  const suffix = isEmail.value ? '邮箱' : '短信';
  return name ? `${suffix} · ${name}` : suffix;
});

/** 当前文件夹展示名 */
const activeFolderName = computed(
  () => folders.value.find(folder => folder.key === activeFolder.value)?.name || '收件箱',
);

/**
 * HTML 邮件的沙箱文档。
 *
 * <p>邮件正文是外部内容：直接 {@code v-html} 会把脚本带进宿主页面，
 * 因此整段塞进 {@code sandbox=""} 的 iframe（禁脚本、禁表单、禁同源），
 * 只保留排版与图片。</p>
 */
const htmlDocument = computed(() => {
  const body = activeMessage.value?.content || activeMessage.value?.preview || '';
  return [
    '<!doctype html><html><head><meta charset="utf-8">',
    '<meta name="referrer" content="no-referrer"><base target="_blank">',
    '<style>html,body{margin:0;padding:0;background:#fff;color:#1f2329;',
    "font:14px/1.7 -apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;}",
    'body{padding:16px 20px;word-break:break-word;}img{max-width:100%;height:auto;}',
    'table{max-width:100%;border-collapse:collapse;}a{color:#5a4bd9;}</style>',
    '</head><body>',
    body,
    '</body></html>',
  ].join('');
});

/** 打开时加载概览 */
watch(
  () => props.modelValue,
  open => {
    if (!open) return;
    mode.value = 'list';
    messages.value = [];
    activeMessage.value = null;
    keyword.value = '';
    resetForm();
    void reload();
  },
);

/**
 * 重新加载概览与当前文件夹。
 */
async function reload(): Promise<void> {
  await loadOverview();
  await reloadMessages();
}

/**
 * 加载概览并确定默认文件夹。
 */
async function loadOverview(): Promise<void> {
  const account = props.account;
  if (!account?.accountId) {
    overview.value = null;
    folders.value = [];
    return;
  }
  loadingOverview.value = true;
  try {
    const data = await getMailboxOverview(account.accountId);
    overview.value = data;
    folders.value = data?.folders ?? [];
    if (!folders.value.some(folder => folder.key === activeFolder.value)) {
      const inbox = folders.value.find(folder => folder.role === 'INBOX');
      activeFolder.value = inbox?.key || folders.value[0]?.key || '';
    }
  } catch (e: any) {
    overview.value = null;
    folders.value = [];
    message.error(e?.message || '收信箱连接失败');
  } finally {
    loadingOverview.value = false;
  }
}

/**
 * 重新从第一页取信。
 */
async function reloadMessages(): Promise<void> {
  if (!props.account?.accountId || !activeFolder.value) {
    messages.value = [];
    hasMore.value = false;
    return;
  }
  loadingMessages.value = true;
  try {
    const rows = await listMailboxMessages(props.account.accountId, {
      folder: activeFolder.value,
      limit: PAGE_SIZE,
      offset: 0,
      keyword: keyword.value.trim() || undefined,
    });
    messages.value = rows ?? [];
    hasMore.value = messages.value.length >= PAGE_SIZE;
  } catch (e: any) {
    messages.value = [];
    hasMore.value = false;
    message.error(e?.message || '收取邮件失败');
  } finally {
    loadingMessages.value = false;
  }
}

/**
 * 加载更早的信（追加到列表末尾）。
 */
async function loadMore(): Promise<void> {
  if (!props.account?.accountId || loadingMessages.value) return;
  loadingMessages.value = true;
  try {
    const rows = await listMailboxMessages(props.account.accountId, {
      folder: activeFolder.value,
      limit: PAGE_SIZE,
      offset: messages.value.length,
      keyword: keyword.value.trim() || undefined,
    });
    const next = rows ?? [];
    messages.value = [...messages.value, ...next];
    hasMore.value = next.length >= PAGE_SIZE;
  } catch (e: any) {
    message.error(e?.message || '加载更多失败');
  } finally {
    loadingMessages.value = false;
  }
}

/**
 * 切换文件夹。
 *
 * @param key 服务端文件夹名
 */
function selectFolder(key: string): void {
  if (activeFolder.value === key) return;
  activeFolder.value = key;
  mode.value = 'list';
  void reloadMessages();
}

/**
 * 打开一封邮件详情。
 *
 * @param row 行数据
 */
async function openMessage(row: Record<string, unknown>): Promise<void> {
  const id = String(row.id ?? '');
  if (!id || !props.account?.accountId) return;
  mode.value = 'detail';
  activeMessage.value = { ...(row as unknown as MailboxMessage) };
  loadingMessage.value = true;
  try {
    const detail = await getMailboxMessage(props.account.accountId, id, activeFolder.value);
    activeMessage.value = detail;
  } catch (e: any) {
    message.error(e?.message || '读取邮件失败');
  } finally {
    loadingMessage.value = false;
  }
}

/** 返回列表 */
function backToList(): void {
  mode.value = 'list';
  activeMessage.value = null;
  resetForm();
}

/** 进入写信（预填收件人） */
function startCompose(): void {
  resetForm();
  mode.value = 'compose';
}

/** 重置写信表单 */
function resetForm(): void {
  form.to = '';
  form.cc = '';
  form.subject = '';
  form.content = '';
  form.templateCode = '';
}

/** 提交发送 */
async function submitSend(): Promise<void> {
  if (!props.account?.accountId || sending.value) return;
  if (!form.to.trim()) {
    message.warning(isEmail.value ? '请填写收件人' : '请填写手机号');
    return;
  }
  if (!form.content.trim()) {
    message.warning('请填写正文');
    return;
  }
  sending.value = true;
  try {
    const result = await sendMailboxMessage(props.account.accountId, {
      to: form.to.trim(),
      cc: splitAddress(form.cc),
      subject: isEmail.value ? form.subject.trim() : undefined,
      content: form.content,
      contentType: 'text',
      templateCode: form.templateCode.trim() || undefined,
    });
    if (result?.success) {
      message.success(isEmail.value ? '邮件已发送' : '短信已提交');
      mode.value = 'list';
      resetForm();
      void reloadMessages();
    } else {
      message.error(result?.message || '发送失败');
    }
  } catch (e: any) {
    message.error(e?.message || '发送失败');
  } finally {
    sending.value = false;
  }
}

/**
 * 拆分逗号 / 分号分隔的地址。
 *
 * @param raw 原始文本
 * @returns 地址数组
 */
function splitAddress(raw: string): string[] | undefined {
  const list = raw
    .split(/[,;，；]/)
    .map(item => item.trim())
    .filter(Boolean);
  return list.length ? list : undefined;
}

/**
 * 文件夹图标。
 *
 * @param role 文件夹角色
 * @returns 图标名
 */
function folderIcon(role?: string): string {
  if (role === 'SENT') return 'Promotion';
  if (role === 'DRAFT') return 'EditPen';
  if (role === 'JUNK') return 'Warning';
  if (role === 'TRASH') return 'Delete';
  return 'Message';
}

/**
 * 列表时间：今天显示时分，本年显示月日，跨年显示完整日期。
 *
 * @param at 毫秒时间戳
 * @returns 展示文本
 */
function formatTime(at?: number | null): string {
  if (!at) return '—';
  const date = new Date(at);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}/${date.getDate()}`;
  }
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`;
}

/**
 * 详情时间：完整到分钟。
 *
 * @param at 毫秒时间戳
 * @returns 展示文本
 */
function formatFullTime(at?: number | null): string {
  if (!at) return '—';
  const date = new Date(at);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 补零。
 *
 * @param value 数字
 * @returns 两位文本
 */
function pad(value: number): string {
  return String(value).padStart(2, '0');
}
</script>

<style scoped>
.mailbox {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 440px;
  color: var(--text-primary);
}

.mb-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 28px 4px;
  color: var(--text-secondary);
}

.spin {
  animation: mb-spin 1s linear infinite;
}

@keyframes mb-spin {
  to {
    transform: rotate(360deg);
  }
}

.mb-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border: 1px solid var(--status-warning, #e6a23c);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-fill, #f7f8fa);
  color: var(--text-secondary);
  font-size: 13px;
}

.mb-retry {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--brand-600, #5a4bd9);
  cursor: pointer;
}

.mb-console {
  margin-left: auto;
  color: var(--brand-600, #5a4bd9);
  text-decoration: none;
  white-space: nowrap;
}

.mb-body {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 14px;
}

.mb-folders {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 152px;
  flex: none;
  padding-right: 10px;
  border-right: 1px solid var(--border-subtle, #ebeef5);
}

.mb-folder {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 8px 9px;
  border: 0;
  border-radius: var(--radius-sm, 6px);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease-out, ease), color 0.16s var(--ease-out, ease);
}

.mb-folder:hover {
  background: var(--bg-fill, #f7f8fa);
  color: var(--text-primary);
}

.mb-folder.is-on {
  background: var(--brand-50, #efedff);
  color: var(--brand-600, #5a4bd9);
  font-weight: 600;
}

.mb-folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mb-folder-badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--status-error, #f56c6c);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}

.mb-folder-total {
  color: var(--text-tertiary, #909399);
  font-size: 11px;
  font-style: normal;
}

.mb-folder-hint {
  padding: 10px 6px;
  color: var(--text-tertiary, #909399);
  font-size: 12px;
  line-height: 1.6;
}

.mb-folder-foot {
  margin-top: auto;
  padding-top: 8px;
}

.mb-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.mb-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  min-height: 30px;
}

.mb-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mb-keyword {
  margin-left: 6px;
  color: var(--text-tertiary, #909399);
  font-style: normal;
  font-weight: 400;
}

.mb-search {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  padding: 4px 8px;
  border: 1px solid var(--border-default, #dcdfe6);
  border-radius: var(--radius-sm, 6px);
  color: var(--text-tertiary, #909399);
  background: var(--bg-container, #fff);
}

.mb-search input {
  width: 168px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 12px;
}

.mb-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--border-default, #dcdfe6);
  border-radius: var(--radius-sm, 6px);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.mb-table {
  flex: 1;
  min-height: 0;
}

.mb-from {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.mb-from.is-unread {
  color: var(--text-primary);
  font-weight: 600;
}

.mb-dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: transparent;
}

.mb-from.is-unread .mb-dot {
  background: var(--brand-500, #6d5ce7);
}

.mb-subject {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.mb-subject.is-unread {
  font-weight: 600;
}

.mb-preview {
  margin-top: 1px;
  color: var(--text-tertiary, #909399);
  font-size: 12px;
}

.mb-time {
  color: var(--text-tertiary, #909399);
  font-size: 12px;
  white-space: nowrap;
}

.mb-more {
  display: flex;
  justify-content: center;
  padding: 10px 0 2px;
}

.mb-end {
  color: var(--text-tertiary, #909399);
  font-size: 12px;
}

.mb-detail {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.mb-head {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-subtle, #ebeef5);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-fill, #f7f8fa);
  font-size: 12px;
}

.mb-head div {
  display: flex;
  gap: 10px;
  padding: 2px 0;
}

.mb-head dt {
  flex: none;
  width: 52px;
  color: var(--text-tertiary, #909399);
}

.mb-head dd {
  margin: 0;
  min-width: 0;
  color: var(--text-secondary);
  word-break: break-all;
}

.mb-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mb-html {
  flex: 1;
  width: 100%;
  min-height: 360px;
  border: 1px solid var(--border-subtle, #ebeef5);
  border-radius: var(--radius-md, 8px);
  background: #fff;
}

.mb-text {
  flex: 1;
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border: 1px solid var(--border-subtle, #ebeef5);
  border-radius: var(--radius-md, 8px);
  background: var(--bg-container, #fff);
  color: var(--text-primary);
  font: 13px/1.75 var(--font-sans, inherit);
  white-space: pre-wrap;
  word-break: break-word;
}

.mb-compose {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
}

.mb-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mb-field--body {
  align-items: flex-start;
}

.mb-label {
  flex: none;
  width: 62px;
  color: var(--text-secondary);
  font-size: 12px;
}

.mb-input,
.mb-textarea {
  flex: 1;
  width: 100%;
  padding: 7px 10px;
  border: 1px solid var(--border-default, #dcdfe6);
  border-radius: var(--radius-sm, 6px);
  outline: none;
  background: var(--bg-container, #fff);
  color: var(--text-primary);
  font-size: 13px;
  font-family: inherit;
}

.mb-input:focus,
.mb-textarea:focus {
  border-color: var(--brand-500, #6d5ce7);
}

.mb-textarea {
  resize: vertical;
  min-height: 220px;
  line-height: 1.7;
}

.mb-compose-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 4px;
}

.mb-sender {
  color: var(--text-tertiary, #909399);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mb-sender em {
  font-style: normal;
  color: var(--status-warning, #e6a23c);
}
</style>
