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
        <span>{{ isEmail ? '正在连接收信服务…' : '正在读取短信通道…' }}</span>
      </div>

      <template v-else>
        <!-- 不可用 / 连接失败：给出明确原因，不留空白抽屉 -->
        <div v-if="overview?.message" class="mb-alert">
          <ReIcon name="Warning" :size="14" />
          <span>{{ overview.message }}</span>
          <a v-if="consoleUrl" class="mb-console" :href="consoleUrl" target="_blank" rel="noopener noreferrer">
            {{ consoleLabel }}
          </a>
          <button type="button" class="mb-retry" @click="reload()">重试</button>
        </div>

        <div class="mb-body">
          <!-- 文件夹轨：短信没有收件箱，只有真有收信文件夹时才显示 -->
          <aside v-if="showFolderRail" class="mb-folders">
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
              {{ overview?.supported ? '暂无可用文件夹' : (isEmail ? '收信未接入' : '暂无收信文件夹') }}
            </div>

            <div class="mb-folder-foot">
              <ReButton show-text icon="Refresh" size="small" plain :loading="loadingMessages" @click="reload()">
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
                <button
                  v-if="!inboxUnavailable"
                  type="button"
                  class="mb-unread"
                  :class="{ 'is-on': onlyUnread }"
                  :aria-pressed="onlyUnread"
                  :title="onlyUnread ? (isEmail ? '显示全部邮件' : '显示全部消息') : (isEmail ? '只看未读邮件' : '只看未读消息')"
                  @click="toggleOnlyUnread"
                >
                  <ReIcon name="Message" :size="12" />
                  <span>只看未读</span>
                  <b v-if="folderUnread">{{ folderUnread }}</b>
                </button>
                <em v-if="unreadFirstActive" class="mb-order">未读优先</em>
                <ReButton
                  v-if="!inboxUnavailable && folderUnread"
                  show-text
                  size="small"
                  plain
                  icon="CircleCheck"
                  :loading="markingAll"
                  @click="markAllRead"
                >
                  全部已读
                </ReButton>
                <label class="mb-search">
                  <ReIcon name="Search" :size="13" />
                  <input
                    v-model="keyword"
                    :placeholder="isEmail ? '搜索主题 / 发件人' : '搜索内容 / 号码'"
                    @keyup.enter="reloadMessages()"
                  />
                </label>
                <ReButton v-if="overview?.sendable" show-text icon="EditPen" type="primary" @click="startCompose()">
                  {{ isEmail ? '写信' : '写短信' }}
                </ReButton>
              </div>

              <ReTable
                v-if="!inboxUnavailable"
                :data="messages"
                :loading="loadingMessages"
                row-key="id"
                border
                stripe
                class="mb-table"
                :empty-text="isEmail ? '这个文件夹没有邮件' : '没有短消息'"
                :settings="{ highlightCurrentRow: true }"
                :config="{ pagination: { enable: false }, toolbar: { enable: false } }"
                @row-click="openMessage"
              >
                <ReTableColumn :label="isEmail ? '发件人' : '发送号码'" min-width="180">
                  <template #default="{ row }">
                    <div class="mb-from" :class="{ 'is-unread': row.unread }">
                      <i class="mb-dot"></i>
                      <span class="ellipsis" :title="row.from">{{ row.from || (isEmail ? '（未知发件人）' : '（未知号码）') }}</span>
                    </div>
                  </template>
                </ReTableColumn>
                <ReTableColumn :label="isEmail ? '主题' : '内容'" min-width="280">
                  <template #default="{ row }">
                    <div class="mb-subject" :class="{ 'is-unread': row.unread }">
                      <span class="ellipsis" :title="messageTitle(row)">{{ messageTitle(row) }}</span>
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

              <div v-else class="mb-state mb-state--block">
                <ReIcon name="Message" :size="18" />
                <span>{{ isEmail ? '这个账号还没有可用的收件文件夹' : '短信账号没有收件箱，可直接写短信发送' }}</span>
              </div>

              <div class="mb-more">
                <ReButton v-if="hasMore" plain :loading="loadingMessages" @click="loadMore">加载更多</ReButton>
                <span v-else-if="messages.length" class="mb-end">{{ isEmail ? '已到最早一封' : '没有更多消息' }}</span>
              </div>
            </template>

            <!-- ==================== 详情 ==================== -->
            <template v-else-if="mode === 'detail'">
              <div class="mb-toolbar">
                <button type="button" class="mb-back" @click="backToList">
                  <ReIcon name="ArrowLeft" :size="14" />
                  <span>返回</span>
                </button>
                <span class="mb-title ellipsis" :title="activeMessage ? messageTitle(activeMessage) : ''">
                  {{ activeMessage ? messageTitle(activeMessage) : '' }}
                </span>
                <template v-if="activeMessage">
                  <ReButton v-if="isEmail" show-text icon="Message" @click="replyMessage">回复</ReButton>
                  <ReButton v-if="isEmail" show-text icon="Share" @click="forwardMessage">转发</ReButton>
                  <ReButton
                    v-if="overview?.modifiable"
                    show-text
                    :icon="activeMessage.unread ? 'Message' : 'View'"
                    :loading="markingBusy"
                    @click="toggleSeen"
                  >
                    {{ activeMessage.unread ? '标为已读' : '标为未读' }}
                  </ReButton>
                  <ReButton
                    v-if="overview?.deletable"
                    show-text
                    icon="Delete"
                    type="danger"
                    plain
                    :loading="deletingMessage"
                    @click="removeMessage"
                  >
                    删除
                  </ReButton>
                </template>
                <ReButton v-if="overview?.sendable" show-text icon="EditPen" @click="startCompose()">
                  {{ isEmail ? '写信' : '写短信' }}
                </ReButton>
              </div>

              <div v-if="loadingMessage" class="mb-state">
                <ReIcon name="Loading" :size="18" class="spin" />
                <span>{{ isEmail ? '正在读取邮件…' : '正在读取消息…' }}</span>
              </div>

              <div v-else-if="activeMessage" class="mb-detail">
                <dl class="mb-head">
                  <div><dt>{{ isEmail ? '发件人' : '发送号码' }}</dt><dd>{{ activeMessage.from || '—' }}</dd></div>
                  <div><dt>{{ isEmail ? '收件人' : '接收号码' }}</dt><dd>{{ activeMessage.to || '—' }}</dd></div>
                  <div v-if="activeMessage.cc"><dt>抄送</dt><dd>{{ activeMessage.cc }}</dd></div>
                  <div><dt>时间</dt><dd>{{ formatFullTime(activeMessage.sentAt) }}</dd></div>
                  <div v-if="activeMessage.attachments?.length">
                    <dt>附件</dt>
                    <dd class="mb-attachments">
                      <ReTag v-for="name in activeMessage.attachments" :key="name" type="info" effect="plain">
                        {{ name }}
                      </ReTag>
                      <span class="mb-attach-tip">仅回显文件名，暂不支持在应用内下载</span>
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
                <button v-if="showFolderRail" type="button" class="mb-back" @click="backToList">
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
 * 邮箱收发 / 短信发信抽屉。
 *
 * <p><b>为什么不是跳转网页版</b>：账号页右键「打开邮箱」要打开的是这个账号<b>真实</b>的
 * 收信箱 / 已发送，而不是邮箱服务商的登录页。邮件收信由后端收信 SPI（IMAP）直连；
 * 短信没有收件箱，只按账号凭据走 {@code MessagePush} 发信，因此这里没有 {@code window.open}。</p>
 *
 * <p>三种模式共用右侧主区：邮件列表（分页取信）→ 详情（HTML 放沙箱 iframe）→ 写信；
 * 短信默认直接进入写短信，发完仍留在写短信。</p>
 *
 * <p><b>邮箱进入即优先收信</b>：打开抽屉立刻连 IMAP 取最新一页，并请求 {@code preferUnread}，
 * 未读邮件排在前面（服务端 {@code SEARCH UNSEEN}）；工具栏还提供「只看未读」。
 * 抽屉重新可见时静默刷新一次，不做定时轮询，避免 IMAP 会话被反复开关。</p>
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDrawer from '@repo/ui/re-drawer';
import ReIcon from '@repo/ui/re-icon';
import ReTable from '@repo/ui/re-table';
import ReTag from '@repo/ui/re-tag';

import {
  deleteMailboxMessage,
  getMailboxMessage,
  getMailboxOverview,
  listMailboxMessages,
  markMailboxMessageSeen,
  sendMailboxMessage,
  type MailboxFolder,
  type MailboxMessage,
  type MailboxOverview,
} from '../api/mailbox';
import type { SysAccount } from '../api/account';
import { confirmThen } from '../../shared/confirmThen';

const props = defineProps<{
  modelValue: boolean;
  account?: SysAccount | null;
  /** 登录页地址（兜底提示：IMAP/SMTP 连不通时可去网页版） */
  consoleUrl?: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  /** 收件箱未读数变化（外层用在索引行 / 页签角标上） */
  (e: 'unread', value: number): void;
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
/** 标记已读 / 未读进行中 */
const markingBusy = ref(false);
/** 批量标记全部已读进行中 */
const markingAll = ref(false);
/** 删除进行中 */
const deletingMessage = ref(false);
/** 只看未读（工具栏开关；进入时若收件箱有未读则默认打开） */
const onlyUnread = ref(false);
/** 首屏是否已按「未读优先」加载过（翻页与刷新都不再重排） */
const preferUnread = ref(true);

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

/** 是否显示文件夹轨：短信没有收件箱，只有真的返回收信文件夹才显示 */
const showFolderRail = computed(() => isEmail.value || folders.value.length > 0);

/** 首次概览是否已决定默认模式（静默刷新不把正在写的短信顶回列表） */
const autoComposeDone = ref(false);

/** 列表 / 详情标题：邮件用主题，短信没有主题，用内容摘要 */
function messageTitle(msg: MailboxMessage): string {
  return isEmail.value
    ? (msg.subject || '（无主题）')
    : (msg.subject || msg.preview || '（无内容）');
}

/** 抽屉标题 */
const drawerTitle = computed(() => {
  const name = props.account?.name || props.account?.username || '';
  const suffix = isEmail.value ? '邮箱' : '短信';
  return name ? `${suffix} · ${name}` : suffix;
});

/** 当前文件夹展示名 */
const activeFolderName = computed(() => {
  const found = folders.value.find(folder => folder.key === activeFolder.value)?.name;
  if (found) return found;
  return isEmail.value ? '收件箱' : '短消息';
});

/**
 * 是否没有可读的收件文件夹。
 *
 * <p>邮箱没配 IMAP、短信部署没注册收信实现时，后端概览会回 {@code supported=false} 且
 * {@code folders} 为空。此时不该再渲染一张空表格（用户会以为「真的没信」），
 * 而应直说这个账号没有收件箱；发信能力是独立的，写信入口照旧可用。</p>
 */
const inboxUnavailable = computed(() => !activeFolder.value);

/** 当前文件夹的未读数（收件箱角色优先，回退到第一个文件夹） */
const folderUnread = computed(() => {
  const inbox = folders.value.find(folder => folder.role === 'INBOX')
    ?? folders.value.find(folder => folder.key === activeFolder.value);
  return Number(inbox?.unread ?? 0) || 0;
});

/** 进入后是否真的按「未读优先」排了序（用于工具栏提示） */
const unreadFirstActive = computed(() => preferUnread.value && !onlyUnread.value && folderUnread.value > 0);

/** 未读数上报外层（索引行 / 页签角标） */
watch(folderUnread, value => emit('unread', value), { immediate: true });

/** 兜底链接文案：邮箱叫网页版邮箱，短信是服务商控制台 */
const consoleLabel = computed(() => (isEmail.value ? '网页版邮箱' : '服务商控制台'));

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
    'table{max-width:100%;border-collapse:collapse;}a{color:#1877ff;}</style>',
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
    autoComposeDone.value = false;
    messages.value = [];
    activeMessage.value = null;
    keyword.value = '';
    // 每次进入都从「全量 + 未读优先」开始，避免上次的筛选把新邮件藏起来
    onlyUnread.value = false;
    preferUnread.value = true;
    resetForm();
    void reload();
  },
);

/**
 * 抽屉重新可见时静默刷新一次。
 *
 * <p>不做定时轮询：IMAP 每次都要建连 / 登录，轮询会拖慢服务端并让连接被限流。
 * 用户切回浏览器标签时补一次最新即可。</p>
 */
function onVisibilityChange(): void {
  if (document.visibilityState === 'visible' && props.modelValue) {
    void refreshQuiet();
  }
}

onMounted(() => document.addEventListener('visibilitychange', onVisibilityChange));
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisibilityChange));

/**
 * 静默刷新：不显示加载态、不弹错误提示，避免打断正在阅读的用户。
 */
async function refreshQuiet(): Promise<void> {
  if (mode.value !== 'list') {
    void refreshFolderCounters();
    return;
  }
  await loadOverview(true);
  await reloadMessages(true);
}

/**
 * 切换「只看未读」。
 */
function toggleOnlyUnread(): void {
  onlyUnread.value = !onlyUnread.value;
  mode.value = 'list';
  activeMessage.value = null;
  void reloadMessages();
}

/**
 * 重新加载概览与当前文件夹。
 */
async function reload(quiet = false): Promise<void> {
  await loadOverview(quiet);
  await reloadMessages(quiet);
}

/**
 * 加载概览并确定默认文件夹。
 *
 * @param quiet 静默刷新（不显示加载态、不弹错误）
 */
async function loadOverview(quiet = false): Promise<void> {
  const account = props.account;
  if (!account?.accountId) {
    overview.value = null;
    folders.value = [];
    return;
  }
  if (!quiet) loadingOverview.value = true;
  try {
    const data = await getMailboxOverview(account.accountId);
    overview.value = data;
    folders.value = data?.folders ?? [];
    if (!folders.value.some(folder => folder.key === activeFolder.value)) {
      const inbox = folders.value.find(folder => folder.role === 'INBOX');
      activeFolder.value = inbox?.key || folders.value[0]?.key || '';
    }
  } catch (e: any) {
    if (quiet) return;
    overview.value = null;
    folders.value = [];
    message.error(e?.message || (isEmail.value ? '收信箱连接失败' : '短信通道连接失败'));
  } finally {
    // 短信没有收件箱：首次概览回来后直接进写短信，别停在「没有邮件」的空列表。
    // 只判一次，之后的静默刷新 / 重试都不会打断用户正在编辑的内容。
    if (!autoComposeDone.value && overview.value) {
      autoComposeDone.value = true;
      if (!isEmail.value && !activeFolder.value) mode.value = 'compose';
    }
    if (!quiet) loadingOverview.value = false;
  }
}

/**
 * 重新从第一页取信。
 *
 * @param quiet 静默刷新（不显示加载态、不弹错误）
 */
async function reloadMessages(quiet = false): Promise<void> {
  if (!props.account?.accountId || !activeFolder.value) {
    messages.value = [];
    hasMore.value = false;
    return;
  }
  if (!quiet) loadingMessages.value = true;
  try {
    const rows = await listMailboxMessages(props.account.accountId, {
      folder: activeFolder.value,
      limit: PAGE_SIZE,
      offset: 0,
      unreadOnly: onlyUnread.value || undefined,
      // 首屏未读优先：服务端 SEARCH UNSEEN 把未读排到前面，已读补足一页
      preferUnread: !onlyUnread.value && preferUnread.value ? true : undefined,
      keyword: keyword.value.trim() || undefined,
    });
    messages.value = rows ?? [];
    hasMore.value = messages.value.length >= PAGE_SIZE;
  } catch (e: any) {
    if (quiet) return;
    messages.value = [];
    hasMore.value = false;
    message.error(e?.message || (isEmail.value ? '收取邮件失败' : '读取消息失败'));
  } finally {
    if (!quiet) loadingMessages.value = false;
  }
}

/**
 * 加载更早的信（追加到列表末尾）。
 *
 * <p>翻页不再请求「未读优先」：首屏已经重排过，再重排会让 offset 错位，
 * 出现同一封邮件重复出现或整段被跳过。</p>
 */
async function loadMore(): Promise<void> {
  if (!props.account?.accountId || loadingMessages.value) return;
  loadingMessages.value = true;
  try {
    const rows = await listMailboxMessages(props.account.accountId, {
      folder: activeFolder.value,
      limit: PAGE_SIZE,
      offset: messages.value.length,
      unreadOnly: onlyUnread.value || undefined,
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
    // 打开即视为已读（与主流邮件客户端一致）；只读来源静默失败，不影响阅读
    if (detail?.unread && overview.value?.modifiable) {
      void autoMarkRead(detail);
    }
  } catch (e: any) {
    message.error(e?.message || (isEmail.value ? '读取邮件失败' : '读取消息失败'));
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

/**
 * 打开邮件后自动标记已读（失败静默，不打扰阅读）。
 *
 * @param detail 已加载的邮件详情
 */
async function autoMarkRead(detail: MailboxMessage): Promise<void> {
  const account = props.account;
  if (!account?.accountId) return;
  try {
    await markMailboxMessageSeen(account.accountId, detail.id, activeFolder.value, true);
    applySeen(true);
  } catch {
    // 只读收信来源无法回写标记，保持未读即可
  }
}

/**
 * 切换已读 / 未读标记。
 *
 * <p>先按目标状态本地更新再刷新文件夹计数：IMAP 往返可能几百毫秒，
 * 期间按钮必须有反馈；失败时回滚并给出原因（只读来源会明确报「不支持」）。</p>
 */
async function toggleSeen(): Promise<void> {
  const account = props.account;
  const current = activeMessage.value;
  if (!account?.accountId || !current || markingBusy.value) return;
  // 当前未读 → 按钮是「标为已读」（seen=true）；当前已读 → 按钮是「标为未读」（seen=false）。
  // 这里算的是「目标 seen」，因此正好等于当前的 unread，不能取反。
  const target = Boolean(current.unread);
  markingBusy.value = true;
  try {
    await markMailboxMessageSeen(account.accountId, current.id, activeFolder.value, target);
    applySeen(target);
    message.success(target ? '已标为已读' : '已标为未读');
    void refreshFolderCounters();
  } catch (e: any) {
    message.error(e?.message || '修改已读状态失败');
  } finally {
    markingBusy.value = false;
  }
}

/**
 * 删除当前邮件（二次确认）。
 *
 * <p>删除是远端不可逆操作，必须确认；成功后回列表并重算文件夹计数，
 * 否则左侧「未读」会停在旧值。</p>
 */
async function removeMessage(): Promise<void> {
  const account = props.account;
  const current = activeMessage.value;
  if (!account?.accountId || !current || deletingMessage.value) return;
  const confirmed = await confirmThen(
    message.confirm,
    `确认删除「${messageTitle(current)}」？删除后无法从本界面恢复。`,
    isEmail.value ? '删除邮件' : '删除消息',
  );
  if (!confirmed) return;
  deletingMessage.value = true;
  try {
    await deleteMailboxMessage(account.accountId, current.id, activeFolder.value);
    message.success(isEmail.value ? '邮件已删除' : '消息已删除');
    mode.value = 'list';
    activeMessage.value = null;
    await reloadMessages();
    void refreshFolderCounters();
  } catch (e: any) {
    message.error(e?.message || (isEmail.value ? '删除邮件失败' : '删除消息失败'));
  } finally {
    deletingMessage.value = false;
  }
}

/**
 * 本地同步已读状态：详情 + 列表行 + 文件夹未读计数。
 *
 * @param seen 是否已读
 */
function applySeen(seen: boolean): void {
  const current = activeMessage.value;
  if (current) {
    current.unread = !seen;
    const hit = messages.value.find(item => item.id === current.id);
    if (hit) hit.unread = !seen;
  }
  const folder = folders.value.find(item => item.key === activeFolder.value);
  if (folder && typeof folder.unread === 'number') {
    const next = folder.unread + (seen ? -1 : 1);
    folder.unread = Math.max(0, next);
  }
}

/**
 * 只刷新文件夹计数（不重载当前列表，避免打断阅读位置）。
 */
async function refreshFolderCounters(): Promise<void> {
  const account = props.account;
  if (!account?.accountId) return;
  try {
    const data = await getMailboxOverview(account.accountId);
    if (data?.folders) {
      folders.value = data.folders;
    }
  } catch {
    // 计数刷新失败不影响已完成的标记 / 删除
  }
}

/**
 * 把当前文件夹里所有未读邮件标为已读。
 *
 * <p>后端只有单封的已读接口，这里先按 {@code unreadOnly} 拉一页未读（上限 200），
 * 再逐封回写；每封完成都即时更新本地状态，中途失败也保留已完成的部分。</p>
 */
async function markAllRead(): Promise<void> {
  const account = props.account;
  if (!account?.accountId || !activeFolder.value || markingAll.value) return;
  markingAll.value = true;
  let done = 0;
  try {
    const rows = await listMailboxMessages(account.accountId, {
      folder: activeFolder.value,
      limit: 200,
      offset: 0,
      unreadOnly: true,
    });
    for (const row of rows ?? []) {
      try {
        await markMailboxMessageSeen(account.accountId, row.id, activeFolder.value, true);
        row.unread = false;
        const hit = messages.value.find(item => item.id === row.id);
        if (hit) hit.unread = false;
        done += 1;
      } catch {
        // 单封失败（如只读来源）继续处理下一封
      }
    }
    if (done) {
      message.success(isEmail.value ? `已把 ${done} 封邮件标为已读` : `已把 ${done} 条消息标为已读`);
      await reloadMessages(true);
    } else {
      message.info(isEmail.value ? '当前文件夹没有可标记的未读邮件' : '当前没有可标记的未读消息');
    }
    void refreshFolderCounters();
  } catch (e: any) {
    message.error(e?.message || '批量标记已读失败');
  } finally {
    markingAll.value = false;
  }
}

/**
 * 从「姓名 <a@b.c>」里取出纯地址。
 *
 * @param raw 原始发件人
 * @returns 纯地址（取不到时原样返回）
 */
function pickAddress(raw?: string): string {
  const text = (raw || '').trim();
  const matched = text.match(/<([^>]+)>/);
  return (matched ? matched[1] : text).trim();
}

/** 生成引用正文：只保留文本，HTML 先剥标签 */
function quoteBody(msg: MailboxMessage): string {
  const raw = msg.content || msg.preview || '';
  const text = msg.contentType === 'html'
    ? raw.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ')
    : raw;
  const lines = [
    '',
    '',
    `---------- 原始${isEmail.value ? '邮件' : '消息'} ----------`,
    `${isEmail.value ? '发件人' : '发送号码'}：${msg.from || '—'}`,
    `时间：${formatFullTime(msg.sentAt)}`,
  ];
  // 短信没有主题，引用正文不再拼一段「主题：」
  if (isEmail.value) {
    lines.push(`主题：${msg.subject || '（无主题）'}`);
  }
  lines.push('', text.trim());
  return lines.join('\n');
}

/** 回复：填好收件人与引用正文，进入写信模式 */
function replyMessage(): void {
  const current = activeMessage.value;
  if (!current) return;
  startCompose({
    to: pickAddress(current.from),
    subject: /^re:/i.test(current.subject || '') ? (current.subject || '') : `Re: ${current.subject || ''}`,
    content: quoteBody(current),
  });
  message.info('已填入原文，可直接编辑后发送');
}

/** 转发：清空收件人，主题加 Fwd:，正文带引用 */
function forwardMessage(): void {
  const current = activeMessage.value;
  if (!current) return;
  startCompose({
    to: '',
    subject: /^fwd:/i.test(current.subject || '') ? (current.subject || '') : `Fwd: ${current.subject || ''}`,
    content: quoteBody(current),
  });
  message.info('请填写转发对象');
}

/**
 * 进入写信。
 *
 * @param prefill 预填内容（回复 / 转发时带入）
 */
function startCompose(prefill?: { to?: string; subject?: string; content?: string }): void {
  resetForm();
  if (prefill) {
    form.to = prefill.to ?? '';
    form.subject = prefill.subject ?? '';
    form.content = prefill.content ?? '';
  }
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
      resetForm();
      // 短信没有收件箱：发完留在写短信，别退回一张空的邮件列表
      if (showFolderRail.value) {
        mode.value = 'list';
        void reloadMessages();
      }
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

.mb-state--block {
  flex: 1;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  min-height: 220px;
  border: 1px dashed var(--border-subtle, #ebeef5);
  border-radius: var(--radius-md, 8px);
  color: var(--text-tertiary, #909399);
  font-size: 13px;
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
  color: var(--el-color-primary, #1877ff);
  cursor: pointer;
}

.mb-console {
  margin-left: auto;
  color: var(--el-color-primary, #1877ff);
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
  background: var(--el-color-primary-light-9, #eef5ff);
  color: var(--el-color-primary, #1877ff);
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
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  min-height: 30px;
}

.mb-title {
  flex: 1 1 auto;
  min-width: 0;
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

.mb-order {
  flex: none;
  padding: 1px 6px;
  border-radius: 9px;
  background: var(--el-color-primary-light-9, #eef5ff);
  color: var(--el-color-primary, #1877ff);
  font-size: 11px;
  font-style: normal;
  line-height: 16px;
}

.mb-unread {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 3px 8px;
  border: 1px solid var(--border-default, #dcdfe6);
  border-radius: var(--radius-sm, 6px);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.mb-unread:hover {
  border-color: var(--el-color-primary-light-5, #8bb8ff);
  color: var(--el-color-primary, #1877ff);
}

.mb-unread.is-on {
  border-color: var(--el-color-primary, #1877ff);
  background: var(--el-color-primary-light-9, #eef5ff);
  color: var(--el-color-primary, #1877ff);
}

.mb-unread b {
  min-width: 15px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--status-error, #f56c6c);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 14px;
  text-align: center;
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
  background: var(--el-color-primary, #1877ff);
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

.mb-attach-tip {
  flex: 1 0 100%;
  font-size: 11px;
  color: var(--text-tertiary, #94a3b8);
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
  border-color: var(--el-color-primary, #1877ff);
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
