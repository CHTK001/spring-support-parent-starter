<!--
  模板管理 · 模板资产（参考设备资产版式）

  布局：
    - 顶部操作条：标题 + 总数、搜索、类型筛选（全部 / 短信 / 邮件 / 文档 / 其他）、
      刷新、新建模板（短信 / 邮件 / 文档）。
    - 左栏 300px 模板列表：模板数据直接上轨，滚动分页；
      条目展示名称、状态点 / 默认角标、编码、类型 chip；
      消息模板（短信 / 邮件）是普通类型，不再单独占特殊入口。
    - 右栏详情卡面：左键点击左侧模板 -> 预览；
      右下角编辑图标 -> 当前页打开编辑（消息模板就地表单编辑，文档模板走弹窗）；
      左下角删除图标 -> 删除。
      页面级导航（打印机管理 / 调用日志）收在左栏底部图标里。

  数据源：
    - 消息模板（短信 / 邮件）：/api/channels/templates（宿主后端进程，部署级共享）
    - 文档模板（单据 / 标签 / 其他）：/api/template/page（模板后端，滚动分页）
-->
<template>
  <div class="tpl">
    <!-- ==================== 顶部操作条 ==================== -->
    <header class="tpl__bar">
      <div class="bar__title">
        <h1>模板管理</h1>
        <span class="bar__count">{{ filteredItems.length }} / {{ allItems.length }}</span>
      </div>

      <div class="bar__search">
        <ReInput
          v-model="keyword"
          size="small"
          clearable
          prefix-icon="Search"
          placeholder="搜索名称 / 编码"
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
          tip="刷新模板列表"
          :icon-size="26"
          :loading="refreshing"
          :disabled="refreshing"
          @click="refreshAll"
        />
        <el-dropdown trigger="click" @command="onCreateCommand">
          <ReButton type="primary" icon="Plus" :show-text="true" size="small">新建模板</ReButton>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="sms">短信模板</el-dropdown-item>
              <el-dropdown-item command="email">邮件模板</el-dropdown-item>
              <el-dropdown-item command="document" divided>文档模板</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- ==================== 主体：左列表 + 右详情 ==================== -->
    <div class="tpl__body">
      <!-- 左栏：模板列表（滚动分页） -->
      <aside class="rail">
        <div
          ref="railListRef"
          class="rail__list"
          @scroll.passive="onRailScroll"
        >
          <div v-if="msgLoading && !msgItems.length" class="rail__state">加载中…</div>
          <div v-else-if="!filteredItems.length" class="rail__state">
            {{ keyword || typeFilter !== 'all' ? '没有匹配的模板' : '暂无模板，点右上角「新建模板」创建' }}
            <span v-if="docError" class="rail__warn">文档模板服务暂不可用</span>
          </div>
          <template v-else>
            <div
              v-for="item in visibleItems"
              :key="item.key"
              class="entry"
              :class="{
                'is-active': selected?.key === item.key,
                'is-off': item.source === 'message' && item.enabled === false
              }"
              role="button"
              tabindex="0"
              @click="selectItem(item)"
              @keydown.enter="selectItem(item)"
            >
              <span class="entry__mark" :class="`is-${item.type}`">
                <ReIcon :name="itemIcon(item)" :size="15" />
              </span>
              <span class="entry__main">
                <span class="entry__name-row">
                  <span class="entry__name" :title="item.name">{{ item.name }}</span>
                  <span
                    v-if="item.source === 'message'"
                    class="entry__dot"
                    :class="item.enabled === false ? 'is-off' : 'is-on'"
                    :title="item.enabled === false ? '已停用' : '已启用'"
                  />
                  <span
                    v-else-if="item.status"
                    class="entry__dot"
                    :class="`is-${item.status}`"
                    :title="statusMeta(item.status).label"
                  />
                  <span v-if="item.isDefault" class="entry__badge">默认</span>
                </span>
                <span class="entry__code mono" :title="item.code">{{ item.code || '未编码' }}</span>
              </span>
              <span class="entry__side">
                <span class="chip">{{ typeLabel(item.type) }}</span>
              </span>
            </div>
          </template>
          <div v-if="docLoading" class="rail__loading">加载更多…</div>
          <div v-else-if="docEnd && docItems.length" class="rail__loading">已到底</div>
        </div>

        <footer class="rail__foot">
          <span>滚动加载更多模板</span>
          <span class="rail__actions">
            <ReButton
              text
              :show-text="false"
              icon="Printer"
              tip="打印机管理"
              :icon-size="24"
              @click="openPrinterManage"
            />
            <ReButton
              text
              :show-text="false"
              icon="Document"
              tip="调用日志"
              :icon-size="24"
              @click="openLogs"
            />
          </span>
        </footer>
      </aside>

      <!-- 右栏：详情卡面（预览 / 编辑 / 设计） -->
      <section class="detail">
        <!-- 文档模板设计器（当前页切换） -->
        <div v-if="viewMode === 'design' && selected" class="detail__fill">
          <TemplateDesigner
            :template-id="selected.id"
            custom-design
            @back="backFromDesign"
            @preview="enterDocPreview"
            @saved="onDesignerSaved"
          />
        </div>

        <!-- 消息模板编辑（当前页打开编辑） -->
        <div v-else-if="viewMode === 'edit' && editing" class="detail__edit">
          <header class="detail__head">
            <div class="detail__title">
              <h2>{{ editing.templateName || '新模板' }}</h2>
              <p>{{ editing.templateKind === 'EMAIL' ? '邮件' : '短信' }}模板 · {{ editing.templateCode || '未设编码' }}</p>
            </div>
          </header>

          <div class="edit-form">
            <div class="edit-form__row">
              <label class="field">
                <span>模板名称</span>
                <input v-model="editing.templateName" type="text" placeholder="如：验证码优先" maxlength="64" />
              </label>
              <label class="field">
                <span>模板编码</span>
                <input v-model="editing.templateCode" type="text" placeholder="同种类内唯一" maxlength="64" />
              </label>
              <label class="field field--sm">
                <span>排序</span>
                <input v-model.number="editing.templateSortOrder" type="number" min="0" />
              </label>
            </div>

            <label v-if="editing.templateKind === 'EMAIL'" class="field">
              <span>邮件主题</span>
              <input v-model="editing.templateSubject" type="text" placeholder="支持 {{变量}}" />
            </label>

            <div class="field">
              <span>模板正文（{{ editing.templateKind === 'EMAIL' ? '邮件' : '短信' }}，支持 &#123;&#123;变量&#125;&#125; 占位）</span>
              <textarea
                ref="contentRef"
                v-model="editing.templateContent"
                class="edit-form__content"
                rows="10"
                spellcheck="false"
              />
            </div>

            <div class="edit-form__vars">
              <span class="edit-form__vars-label">插入变量</span>
              <button
                v-for="name in variables"
                :key="name"
                type="button"
                class="var"
                @click="insertVariable(name)"
              >{{ name }}</button>
            </div>

            <div class="edit-form__toggle">
              <label>
                <input v-model="editing.templateEnabled" type="checkbox" />
                启用
              </label>
              <label>
                <input v-model="editing.templateDefault" type="checkbox" />
                设为默认
              </label>
            </div>
          </div>

          <footer class="detail__foot">
            <ReButton
              v-if="editing.templateId"
              text
              :show-text="false"
              icon="Delete"
              tip="删除模板"
              :icon-size="28"
              @click="removeSelected"
            />
            <span class="detail__foot-right">
              <ReButton size="small" @click="cancelEdit">取消</ReButton>
              <ReButton type="primary" size="small" :loading="saving" :disabled="saving" @click="saveMessage">
                保存模板
              </ReButton>
            </span>
          </footer>
        </div>

        <!-- 预览 -->
        <div v-else-if="selected && viewMode === 'preview'" class="detail__preview">
          <header class="detail__head">
            <div class="detail__title">
              <h2>{{ selected.name }}</h2>
              <p class="mono">{{ selected.code || '未编码' }}</p>
            </div>
            <div class="detail__tags">
              <span class="chip">{{ typeLabel(selected.type) }}</span>
              <span v-if="selected.isDefault" class="chip is-default">默认</span>
              <span v-if="selected.source === 'message'" class="chip">
                {{ selected.enabled === false ? '已停用' : '已启用' }}
              </span>
              <el-tag
                v-else-if="selected.status"
                :type="statusMeta(selected.status).type"
                size="small"
                effect="plain"
                round
              >
                {{ statusMeta(selected.status).label }}
              </el-tag>
            </div>
            <div v-if="selected.source === 'hiprint'" class="detail__head-acts">
              <ReButton
                text
                :show-text="false"
                icon="MagicStick"
                tip="进入设计器"
                :icon-size="26"
                @click="enterDesign"
              />
            </div>
          </header>

          <div class="detail__body">
            <!-- 消息模板预览：示例变量渲染 -->
            <div v-if="selected.source === 'message'" class="msg-preview">
              <div class="msg-preview__bar">
                <b>预览</b>
                <span>{{ previewing ? '渲染中…' : '示例变量' }}</span>
              </div>
              <div class="msg-preview__body">
                <div v-if="msgPreview?.subject" class="msg-preview__subject">{{ msgPreview.subject }}</div>
                <pre class="msg-preview__content">{{ msgPreview?.content || '输入正文后自动预览' }}</pre>
                <div v-if="msgPreview?.missing?.length" class="msg-preview__missing">
                  未取值变量：{{ msgPreview.missing.join('、') }}
                </div>
              </div>
            </div>
            <!-- 文档模板预览 -->
            <div v-else class="detail__fill">
              <TemplatePreviewView :template-id="selected.id" />
            </div>
          </div>

          <footer class="detail__foot">
            <ReButton
              text
              :show-text="false"
              icon="Delete"
              tip="删除模板"
              :icon-size="28"
              @click="removeSelected"
            />
            <span class="detail__foot-right">
              <ReButton
                text
                :show-text="false"
                icon="EditPen"
                tip="编辑模板"
                :icon-size="28"
                @click="startEdit(selected)"
              />
            </span>
          </footer>
        </div>

        <!-- 空态 -->
        <div v-else class="detail__empty">
          <span class="detail__empty-mark"><ReIcon name="Files" :size="24" /></span>
          <b>还没有选择模板</b>
          <span>从左侧选择模板查看预览；点右上角「新建模板」创建</span>
        </div>
      </section>
    </div>

    <!-- 文档模板新建 / 编辑弹窗 -->
    <ReDialog v-model="docFormVisible" :title="docFormTitle" width="520px" @confirm="onDocFormConfirm">
      <el-form :model="docForm" label-width="80px">
        <el-form-item label="模板名称" required>
          <el-input v-model="docForm.name" placeholder="例如 出库单" maxlength="64" />
        </el-form-item>
        <el-form-item label="模板副标题">
          <el-input v-model="docForm.subtitle" placeholder="例如 仓库出库单据" maxlength="128" />
        </el-form-item>
        <el-form-item label="模板语言" required>
          <el-select v-model="docForm.language" class="w-full">
            <el-option v-for="item in languageOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板类型" required>
          <el-select v-model="docForm.type" class="w-full">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属提供商">
          <ReSelect v-model="docForm.providerId" :options="providerOptions" placeholder="选择提供商（文档模板归属）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="docForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <ReButton @click="docFormVisible = false">取消</ReButton>
        <ReButton type="primary" :loading="docSaving" @click="onDocFormConfirm">确定</ReButton>
      </template>
    </ReDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 模板管理 · 模板资产页（参考设备资产版式）。
 *
 * <p>左栏直接渲染模板数据（消息模板 + 文档模板，滚动分页），消息模板只是
 * 普通类型（短信 / 邮件），不再单独占特殊入口；右栏点击出预览，右下角编辑
 * （消息模板当前页就地编辑，文档模板走弹窗 / 设计器），左下角删除。</p>
 *
 * <p>消息模板数据来自宿主后端 `/api/channels/templates`（部署级共享），
 * 文档模板来自模板后端 `/api/template/page`（独立模板服务，远程承载）。</p>
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ReButton from '@repo/ui/re-button'
import ReDialog from '@repo/ui/re-dialog'
import ReIcon from '@repo/ui/re-icon'
import ReInput from '@repo/ui/re-input'
import ReSelect from '@repo/ui/re-select'

import { templateApi } from '../api/templateApi'
import {
  messageTemplateApi,
  type MessageTemplate,
  type MessageTemplateRenderResult
} from '../api/messageTemplateApi'
import { errorText } from '../../shared/errorText'
import type { ProviderInfo, TemplateInfo } from '../types/template'
import TemplatePreviewView from '../views/TemplatePreviewView.vue'
import TemplateDesigner from '../views/TemplateDesigner.vue'
import { statusMeta, tplLanguageLabels, tplTypeLabels, typeLabel } from '../utils/i18n'

const router = useRouter()

/** 宿主传入的初始类型：message 时自动选中第一条消息模板 */
const props = defineProps<{ type?: string }>()

// ==================== 统一模板项 ====================
type TplType = 'sms' | 'email' | 'document' | 'label' | 'other'

interface TplItem {
  key: string
  id: string
  name: string
  code: string
  type: TplType
  source: 'message' | 'hiprint' | 'paper'
  isDefault?: boolean
  enabled?: boolean
  status?: string
  updatedAt?: string
  raw: MessageTemplate | TemplateInfo
}

/** 消息模板 -> 统一项 */
function toMsgItems(list: MessageTemplate[]): TplItem[] {
  return list.map(t => ({
    key: `m-${t.templateId ?? t.templateCode}`,
    id: String(t.templateId ?? ''),
    name: t.templateName,
    code: t.templateCode,
    type: t.templateKind === 'EMAIL' ? 'email' : 'sms',
    source: 'message',
    isDefault: !!t.templateDefault,
    enabled: !!t.templateEnabled,
    raw: t
  }))
}

/**
 * 规范化文档模板记录：后端模板实体用 `templateInfo*` 前缀字段，前端领域模型用
 * 短字段名（id/name/type…），这里两套都兼容。
 *
 * <p>模板接口默认落在本应用后端后，分页接口直接返回实体字段；不做这层映射，
 * 列表会显示空名称、类型全部落到「其他」，编辑弹窗也回填不出值。</p>
 */
function normalizeDoc(record: Record<string, unknown>): TemplateInfo {
  const pick = <T>(...keys: string[]): T | undefined => {
    for (const key of keys) {
      const value = record[key]
      if (value !== undefined && value !== null && value !== '') return value as T
    }
    return undefined
  }
  return {
    ...(record as unknown as TemplateInfo),
    id: String(pick<string>('id', 'templateInfoId') ?? ''),
    name: String(pick<string>('name', 'templateInfoName') ?? ''),
    subtitle: String(pick<string>('subtitle', 'templateInfoSubtitle') ?? ''),
    language: (pick<string>('language', 'templateInfoLanguage') ?? 'html') as TemplateInfo['language'],
    type: (pick<string>('type', 'templateInfoType') ?? 'other') as TemplateInfo['type'],
    status: (pick<string>('status', 'templateInfoStatus') ?? 'draft') as TemplateInfo['status'],
    source: (pick<string>('source', 'templateInfoSource') ?? 'hiprint') as TemplateInfo['source'],
    providerId: pick<string>('providerId', 'templateInfoProviderId'),
    sourceId: pick<string>('sourceId', 'templateInfoSourceId'),
    sourceCode: pick<string>('sourceCode', 'templateInfoSourceCode'),
    creator: String(pick<string>('creator', 'templateInfoCreator') ?? ''),
    modifier: pick<string>('modifier', 'templateInfoModifier'),
    createdAt: pick<string>('createdAt', 'templateInfoCreatedAt'),
    updatedAt: pick<string>('updatedAt', 'templateInfoUpdatedAt'),
    remark: pick<string>('remark', 'templateInfoRemark')
  }
}

/** 文档模板 -> 统一项 */
function toDocItems(list: TemplateInfo[]): TplItem[] {
  return list.map(raw => {
    const t = normalizeDoc(raw as unknown as Record<string, unknown>)
    return {
      key: `d-${t.id}`,
      id: String(t.id),
      name: t.name,
      code: t.sourceId ?? t.sourceCode ?? '',
      type: (t.type ?? 'other') as TplType,
      source: t.source ?? 'hiprint',
      status: t.status,
      updatedAt: t.updatedAt,
      raw: t
    }
  })
}

// ==================== 列表数据 ====================
/** 消息模板（短信 + 邮件，一次拉全量） */
const msgItems = ref<TplItem[]>([])
const msgLoading = ref(false)
const msgError = ref('')

/** 文档模板（滚动分页） */
const docItems = ref<TplItem[]>([])
const docLoading = ref(false)
const docEnd = ref(false)
const docPage = ref(1)
const docError = ref('')

/** 搜索关键词 */
const keyword = ref('')
/** 类型筛选：all / sms / email / document / other */
const typeFilter = ref<'all' | TplType>('all')
/** 左栏滚动容器（滚到底部加载更多文档模板） */
const railListRef = ref<HTMLElement | null>(null)

/** 全部模板项：消息模板在前（短信 / 邮件按排序），文档模板按更新时间倒序在后 */
const allItems = computed<TplItem[]>(() => {
  const msg = [...msgItems.value].sort((a, b) => {
    const am = a.raw as MessageTemplate
    const bm = b.raw as MessageTemplate
    if ((am.templateKind ?? '') !== (bm.templateKind ?? '')) return am.templateKind === 'SMS' ? -1 : 1
    return (am.templateSortOrder ?? 0) - (bm.templateSortOrder ?? 0)
  })
  const doc = [...docItems.value].sort((a, b) =>
    String(b.updatedAt ?? '').localeCompare(String(a.updatedAt ?? ''))
  )
  return [...msg, ...doc]
})

/** 类型筛选条（带数量） */
const typeFilters = computed<Array<{ key: 'all' | TplType; label: string; n: number }>>(() => {
  const count = (t: TplType) => allItems.value.filter(i => i.type === t).length
  const other = count('label') + count('other')
  return [
    { key: 'all', label: '全部', n: allItems.value.length },
    { key: 'sms', label: '短信', n: count('sms') },
    { key: 'email', label: '邮件', n: count('email') },
    { key: 'document', label: '文档', n: count('document') },
    { key: 'other', label: '其他', n: other }
  ]
})

/** 过滤后的列表 */
const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return allItems.value.filter(it => {
    if (typeFilter.value === 'other') {
      if (!['label', 'other'].includes(it.type)) return false
    } else if (typeFilter.value !== 'all' && it.type !== typeFilter.value) {
      return false
    }
    if (!k) return true
    return `${it.name} ${it.code}`.toLowerCase().includes(k)
  })
})

/** 左栏每次滚动加载的条数 */
const RAIL_PAGE = 30
/** 已加载条数（滚动分页，前端切片） */
const railLimit = ref(RAIL_PAGE)
/** 当前可见的模板（消息 + 文档合并后按滚动分页） */
const visibleItems = computed(() => filteredItems.value.slice(0, railLimit.value))

/** 搜索 / 类型筛选变化时回到第一屏 */
watch([keyword, typeFilter], () => {
  railLimit.value = RAIL_PAGE
})

/** 加载消息模板 */
async function loadMessages(): Promise<void> {
  msgLoading.value = true
  try {
    const list = await messageTemplateApi.list()
    msgItems.value = toMsgItems(Array.isArray(list) ? list : [])
    msgError.value = ''
  } catch (err) {
    msgError.value = errorText(err)
  } finally {
    msgLoading.value = false
  }
}

/** 加载文档模板（滚动分页；reset 时回到第一页） */
async function loadDocs(reset = false): Promise<void> {
  if (docLoading.value) return
  if (reset) {
    docPage.value = 1
    docItems.value = []
    docEnd.value = false
    docError.value = ''
  }
  docLoading.value = true
  try {
    const res = await templateApi.page({ page: docPage.value, size: 30 })
    const records = (res?.records ?? []) as TemplateInfo[]
    docItems.value = [...docItems.value, ...toDocItems(records)]
    docEnd.value = !records.length || docItems.value.length >= Number(res?.total ?? 0)
    if (!docEnd.value) docPage.value += 1
  } catch (err) {
    if (reset) docError.value = errorText(err)
    /* 滚动续载失败静默，顶部有刷新可重试 */
  } finally {
    docLoading.value = false
  }
}

/** 左栏滚动到底部时加载更多文档模板（并追加前端已加载条数） */
function onRailScroll(e: Event): void {
  const el = e.target as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    if (typeFilter.value !== 'sms' && typeFilter.value !== 'email') void loadDocs()
    if (railLimit.value < filteredItems.value.length) railLimit.value += RAIL_PAGE
  }
}

/** 刷新全部 */
const refreshing = ref(false)
async function refreshAll(): Promise<void> {
  refreshing.value = true
  try {
    await Promise.all([loadMessages(), loadDocs(true)])
  } finally {
    refreshing.value = false
  }
}

// ==================== 选中 / 预览 ====================
const selected = ref<TplItem | null>(null)
const viewMode = ref<'preview' | 'edit' | 'design'>('preview')

/** 预览用示例变量（与后端内置变量清单对齐） */
const SAMPLE_VARIABLES: Record<string, string> = {
  from: 'noreply@example.com',
  code: '123456',
  content: '您的验证码是 123456，5 分钟内有效。',
  body: '您的验证码是 123456，5 分钟内有效。请勿泄露给他人。',
  subject: '您的登录验证码',
  phone: '13800000000',
  email: 'demo@example.com'
}

/** 消息模板预览结果 */
const msgPreview = ref<MessageTemplateRenderResult | null>(null)
const previewing = ref(false)

async function renderMessagePreview(mt: MessageTemplate): Promise<void> {
  previewing.value = true
  try {
    msgPreview.value = await messageTemplateApi.render({
      kind: mt.templateKind,
      subject: mt.templateSubject ?? undefined,
      content: mt.templateContent,
      variables: SAMPLE_VARIABLES
    })
  } catch {
    msgPreview.value = null
  } finally {
    previewing.value = false
  }
}

/** 左键点击：出预览 */
async function selectItem(item: TplItem): Promise<void> {
  selected.value = item
  viewMode.value = 'preview'
  editing.value = null
  msgPreview.value = null
  if (item.source === 'message') await renderMessagePreview(item.raw as MessageTemplate)
}

// ==================== 编辑（消息模板当前页 / 文档模板弹窗） ====================
const editing = ref<MessageTemplate | null>(null)
const saving = ref(false)
const contentRef = ref<HTMLTextAreaElement | null>(null)
const variables = ref<string[]>([])

/** 右下角编辑图标：当前页打开编辑 */
function startEdit(item: TplItem): void {
  if (item.source !== 'message') {
    openDocEdit(item.raw as TemplateInfo)
    return
  }
  editing.value = { ...(item.raw as MessageTemplate) }
  viewMode.value = 'edit'
  void loadVariables(editing.value.templateKind as 'SMS' | 'EMAIL')
}

/** 新建消息模板（短信 / 邮件），直接进入当前页编辑 */
function createMessage(kind: 'SMS' | 'EMAIL'): void {
  editing.value = {
    templateKind: kind,
    templateName: kind === 'SMS' ? '新短信模板' : '新邮件模板',
    templateCode: kind === 'SMS' ? 'sms-custom' : 'email-custom',
    templateSubject: kind === 'EMAIL' ? '{{subject}}' : null,
    templateContent: kind === 'SMS' ? '{{content}}' : '{{body}}',
    templateDefault: false,
    templateEnabled: true,
    templateSortOrder: 30
  }
  selected.value = null
  msgPreview.value = null
  viewMode.value = 'edit'
  void loadVariables(kind)
}

/** 新建下拉命令 */
function onCreateCommand(command: string): void {
  if (command === 'sms') createMessage('SMS')
  else if (command === 'email') createMessage('EMAIL')
  else openDocCreate()
}

/** 在正文光标处插入 {{变量}} */
async function insertVariable(name: string): Promise<void> {
  const d = editing.value
  if (!d) return
  const token = `{{${name}}}`
  const el = contentRef.value
  if (!el) {
    d.templateContent += token
    return
  }
  const start = el.selectionStart ?? d.templateContent.length
  const end = el.selectionEnd ?? start
  d.templateContent = d.templateContent.slice(0, start) + token + d.templateContent.slice(end)
  await nextTick()
  el.focus()
  const cursor = start + token.length
  el.setSelectionRange(cursor, cursor)
}

/** 加载消息模板内置变量 */
async function loadVariables(kind: 'SMS' | 'EMAIL'): Promise<void> {
  try {
    variables.value = await messageTemplateApi.variables(kind)
  } catch {
    variables.value = kind === 'EMAIL'
      ? ['from', 'subject', 'body', 'code', 'email', 'time', 'date']
      : ['from', 'content', 'code', 'phone', 'time', 'date']
  }
}

/** 保存消息模板 */
async function saveMessage(): Promise<void> {
  const d = editing.value
  if (!d) return
  if (!d.templateName.trim() || !d.templateCode.trim()) {
    ElMessage.warning('模板名称与编码不能为空')
    return
  }
  if (!d.templateContent.trim()) {
    ElMessage.warning('模板正文不能为空')
    return
  }
  saving.value = true
  try {
    const saved = await messageTemplateApi.save({
      ...d,
      templateKind: d.templateKind,
      templateSubject: d.templateKind === 'EMAIL' ? d.templateSubject : null
    })
    ElMessage.success('模板已保存')
    await loadMessages()
    // 回预览：优先按保存返回的 templateId 定位列表项
    const item = msgItems.value.find(i => i.id === String(saved.templateId ?? ''))
    if (item) {
      editing.value = null
      viewMode.value = 'preview'
      await selectItem(item)
    } else {
      editing.value = { ...saved }
      viewMode.value = 'edit'
    }
  } catch (err) {
    ElMessage.error(errorText(err))
  } finally {
    saving.value = false
  }
}

/** 取消编辑 */
function cancelEdit(): void {
  editing.value = null
  viewMode.value = 'preview'
}

// ==================== 删除（左下角图标） ====================
async function removeSelected(): Promise<void> {
  const item = selected.value
  if (!item) {
    ElMessage.warning('未选择模板')
    return
  }
  try {
    await ElMessageBox.confirm(`删除模板「${item.name}」？`, '删除模板', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    if (item.source === 'message') await messageTemplateApi.remove(item.id)
    else await templateApi.remove(item.id)
    ElMessage.success('模板已删除')
    selected.value = null
    editing.value = null
    msgPreview.value = null
    if (item.source === 'message') await loadMessages()
    else await loadDocs(true)
  } catch (err) {
    ElMessage.error(errorText(err))
  }
}

// ==================== 文档模板：弹窗新建 / 编辑 ====================
const docFormVisible = ref(false)
const docFormTitle = ref('')
const docForm = ref<Partial<TemplateInfo>>({})
const docSaving = ref(false)
const providers = ref<ProviderInfo[]>([])
const providerOptions = computed(() => providers.value.map(p => ({ label: p.name, value: p.id })))

async function loadProviders(): Promise<void> {
  try {
    providers.value = await templateApi.providerList()
  } catch {
    providers.value = []
  }
}

function openDocCreate(): void {
  docFormTitle.value = '新建模板'
  docForm.value = { language: 'html', type: 'document', status: 'draft', source: 'hiprint' }
  docFormVisible.value = true
}

function openDocEdit(row: TemplateInfo): void {
  docFormTitle.value = '编辑模板'
  docForm.value = { ...row }
  docFormVisible.value = true
}

async function onDocFormConfirm(): Promise<void> {
  if (!docForm.value.name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  docSaving.value = true
  try {
    const payload = {
      ...docForm.value,
      status: (docForm.value.status as TemplateInfo['status']) ?? 'draft',
      source: docForm.value.source ?? 'hiprint'
    }
    if (docForm.value.id) {
      await templateApi.update(String(docForm.value.id), payload)
      ElMessage.success('更新成功')
    } else {
      await templateApi.add(payload)
      ElMessage.success('新增成功')
    }
    docFormVisible.value = false
    await loadDocs(true)
  } catch (err) {
    ElMessage.error(errorText(err))
  } finally {
    docSaving.value = false
  }
}

// ==================== 文档模板：设计器 ====================
function enterDesign(): void {
  if (!selected.value || selected.value.source !== 'hiprint') return
  viewMode.value = 'design'
}

function backFromDesign(): void {
  viewMode.value = 'preview'
}

function enterDocPreview(): void {
  viewMode.value = 'preview'
}

function onDesignerSaved(): void {
  void loadDocs(true)
  viewMode.value = 'preview'
}

// ==================== 页面级导航 / 字典 ====================
function openPrinterManage(): void {
  router.push({ name: 'TemplatePrinter' })
}

function openLogs(): void {
  router.push({ name: 'TemplateLogs' })
}

/** 条目图标：短信 / 邮件 / 文档 */
function itemIcon(item: TplItem): string {
  if (item.type === 'sms') return 'ChatDotRound'
  if (item.type === 'email') return 'Message'
  if (item.type === 'label') return 'PriceTag'
  return 'Document'
}

const languageOptions = computed(() =>
  Object.entries(tplLanguageLabels).map(([value, label]) => ({ label, value }))
)
const typeOptions = computed(() =>
  Object.entries(tplTypeLabels).map(([value, label]) => ({ label, value }))
)

// ==================== 初始化 ====================
onMounted(async () => {
  // 消息模板先行：深链 /template/message 只等消息模板就绪即选中，
  // 不被文档模板后端（独立部署，可能较慢/不可达）拖住
  const msgReady = loadMessages()
  void Promise.all([msgReady, loadDocs(), loadProviders()])
  if (props.type === 'message') {
    void msgReady.then(() => {
      const first = filteredItems.value.find(i => i.source === 'message') ?? msgItems.value[0]
      if (first) void selectItem(first)
    })
  }
})
</script>

<style lang="scss" scoped>
.tpl {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  color: var(--text-primary, var(--el-text-color-primary));
}

/* ==================== 顶部操作条 ==================== */
.tpl__bar {
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
    color: var(--text-primary);
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
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
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
.tpl__body {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

/* ==================== 左栏：模板列表 ==================== */
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

.rail__warn {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-color-warning);
}

.rail__loading {
  padding: 10px 0 12px;
  font-size: 11px;
  text-align: center;
  color: var(--text-tertiary);
}

.rail__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 3px 8px 3px 10px;
  font-size: 11px;
  color: var(--text-tertiary);
  background: var(--bg-container);
  border-top: 1px solid var(--border-subtle, var(--border-default));
}

.rail__actions {
  display: flex;
  align-items: center;
  gap: 0;
}

/* ---------- 模板条目 ---------- */
.entry {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  outline: none;
  transition: background 0.15s var(--ease-out, ease), border-color 0.15s var(--ease-out, ease);

  & + & {
    margin-top: 1px;
  }

  &:hover {
    background: var(--bg-fill);
  }

  &:focus-visible {
    box-shadow: inset 0 0 0 2px var(--el-color-primary-light-7);
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
  font-size: 13px;
  font-weight: 650;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 8px;

  &.is-email {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &.is-sms {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &.is-document {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
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
  color: var(--text-primary);
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

  &.is-online {
    background: var(--el-color-success);
  }

  &.is-offline {
    background: var(--el-color-danger);
  }

  &.is-draft {
    background: var(--el-color-warning);
  }
}

.entry__badge {
  flex: none;
  padding: 0 6px;
  font-size: 10px;
  line-height: 15px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 999px;
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
  display: inline-flex;
  align-items: center;
  padding: 0 7px;
  font-size: 11px;
  line-height: 18px;
  color: var(--text-secondary);
  background: var(--bg-fill);
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: 999px;
  white-space: nowrap;

  &.is-default {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
  }
}

/* ==================== 右栏：详情卡面 ==================== */
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

.detail__fill {
  flex: 1;
  min-height: 0;
}

/* ---------- 预览 ---------- */
.detail__preview,
.detail__edit {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.detail__head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}

.detail__title {
  min-width: 0;

  h2 {
    margin: 0;
    overflow: hidden;
    font-size: 15px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--text-primary);
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

.detail__tags {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  white-space: nowrap;
}

.detail__head-acts {
  display: flex;
  flex: none;
  align-items: center;
  gap: 2px;
}

.detail__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 14px;
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

/* ---------- 消息模板预览 ---------- */
.msg-preview {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  background: var(--bg-fill);
  border: 1px solid var(--border-subtle, var(--border-default));
  border-radius: var(--radius-md, 8px);
  overflow: hidden;
}

.msg-preview__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-container);
  border-bottom: 1px solid var(--border-subtle, var(--border-default));

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.msg-preview__body {
  flex: 1;
  padding: 14px;
  min-height: 0;
  overflow: auto;
}

.msg-preview__subject {
  margin-bottom: 10px;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  background: var(--bg-container);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
}

.msg-preview__content {
  margin: 0;
  padding: 10px 12px;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  background: var(--bg-container);
  border-radius: 6px;
  border: 1px solid var(--border-subtle, var(--border-default));
}

.msg-preview__missing {
  margin-top: 10px;
  padding: 6px 10px;
  font-size: 11px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
}

/* ---------- 消息模板编辑表单 ---------- */
.edit-form {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding: 12px 14px;
}

.edit-form__row {
  display: flex;
  gap: 10px;
}

.field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  font-size: 12px;
  color: var(--text-secondary);

  &.field--sm {
    flex: 0 0 96px;
  }

  input,
  textarea {
    box-sizing: border-box;
    width: 100%;
    padding: 6px 9px;
    font-size: 13px;
    line-height: 20px;
    color: var(--text-primary);
    background: var(--bg-container);
    border: 1px solid var(--border-default);
    border-radius: 6px;
    outline: none;
    transition: border-color 0.15s ease;

    &:focus {
      border-color: var(--el-color-primary);
    }
  }

  textarea {
    resize: vertical;
  }
}

.edit-form__content {
  min-height: 160px;
}

.edit-form__vars {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.edit-form__vars-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.var {
  padding: 1px 9px;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: var(--el-color-primary-light-8);
  }
}

.edit-form__toggle {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);

  label {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    cursor: pointer;
  }
}

.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
}
</style>
