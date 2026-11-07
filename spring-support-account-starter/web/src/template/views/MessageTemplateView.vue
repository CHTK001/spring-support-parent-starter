<script setup lang="ts">
/**
 * 消息模板管理（短信 / 邮件）—— 模板管理域的独立页面。
 *
 * 与渠道管理页拆开：这里管的是「渠道收到的原始消息怎么渲染成可读文本」，
 * 属于模板配置，不再是渠道页的一个按钮/抽屉。数据由 account-starter 的
 * `/api/channels/templates` 提供（部署级共享）。
 *
 * 结构：
 * - 左栏：按种类（短信 / 邮件）分组的模板清单（默认模板有角标），支持新增 / 删除；
 * - 右栏：编辑器（名称 / 编码 / 主题 / 正文 / 排序 / 启用），正文支持插入变量占位；
 * - 底部：实时预览（走后端 /render，展示渲染结果与缺失变量），保存 / 设为默认。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  messageTemplateApi,
  type MessageTemplate,
  type MessageTemplateRenderResult,
} from '../api/messageTemplateApi'

type Kind = 'SMS' | 'EMAIL'

const kind = ref<Kind>('SMS')
const templates = ref<MessageTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const variables = ref<string[]>([])

/** 当前编辑的模板（新建时无 templateId） */
const draft = ref<MessageTemplate>(emptyDraft('SMS'))
const contentRef = ref<HTMLTextAreaElement | null>(null)

/** 预览结果与缺失变量 */
const preview = ref<MessageTemplateRenderResult | null>(null)
const previewing = ref(false)
let previewTimer: ReturnType<typeof setTimeout> | null = null

/** 预览用示例变量（与后端内置变量清单对齐） */
const SAMPLE_VARIABLES: Record<string, string> = {
  from: 'noreply@example.com',
  code: '123456',
  content: '您的验证码是 123456，5 分钟内有效。',
  body: '您的验证码是 123456，5 分钟内有效。请勿泄露给他人。',
  subject: '您的登录验证码',
  phone: '13800000000',
  email: 'demo@example.com',
}

function emptyDraft(nextKind: Kind): MessageTemplate {
  return {
    templateKind: nextKind,
    templateName: nextKind === 'SMS' ? '新短信模板' : '新邮件模板',
    templateCode: nextKind === 'SMS' ? 'sms-custom' : 'email-custom',
    templateSubject: nextKind === 'EMAIL' ? '{{subject}}' : null,
    templateContent: nextKind === 'SMS' ? '{{content}}' : '{{body}}',
    templateDefault: false,
    templateEnabled: true,
    templateSortOrder: 30,
  }
}

/** 拉取当前种类的模板清单 */
async function loadTemplates() {
  loading.value = true
  errorText.value = ''
  try {
    templates.value = await messageTemplateApi.list(kind.value)
    if (!draft.value.templateId && draft.value.templateKind !== kind.value) {
      draft.value = emptyDraft(kind.value)
    }
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 拉取该种类的内置变量清单 */
async function loadVariables() {
  try {
    variables.value = await messageTemplateApi.variables(kind.value)
  } catch {
    variables.value = kind.value === 'EMAIL'
      ? ['from', 'subject', 'body', 'code', 'email', 'time', 'date']
      : ['from', 'content', 'code', 'phone', 'time', 'date']
  }
}

/** 选择模板进入编辑 */
function edit(template: MessageTemplate) {
  draft.value = { ...template }
  schedulePreview()
}

/** 新建（保持当前种类） */
function createNew() {
  draft.value = emptyDraft(kind.value)
  preview.value = null
}

/** 切换种类：重置编辑器并重载清单 */
async function switchKind(next: Kind) {
  if (kind.value === next) return
  kind.value = next
  draft.value = emptyDraft(next)
  preview.value = null
  await Promise.all([loadTemplates(), loadVariables()])
}

/** 在正文光标处插入 {{变量}} */
async function insertVariable(name: string) {
  const token = `{{${name}}}`
  const el = contentRef.value
  if (!el) {
    draft.value.templateContent += token
    return
  }
  const start = el.selectionStart ?? draft.value.templateContent.length
  const end = el.selectionEnd ?? start
  const content = draft.value.templateContent
  draft.value.templateContent = content.slice(0, start) + token + content.slice(end)
  await nextTick()
  el.focus()
  const cursor = start + token.length
  el.setSelectionRange(cursor, cursor)
}

/** 保存（新增或更新） */
async function save() {
  if (saving.value) return
  if (!draft.value.templateName.trim() || !draft.value.templateCode.trim()) {
    ElMessage.warning('模板名称与编码不能为空')
    return
  }
  if (!draft.value.templateContent.trim()) {
    ElMessage.warning('模板正文不能为空')
    return
  }
  saving.value = true
  try {
    const saved = await messageTemplateApi.save({
      ...draft.value,
      templateKind: kind.value,
      templateSubject: kind.value === 'EMAIL' ? draft.value.templateSubject : null,
    })
    draft.value = { ...saved }
    ElMessage.success('模板已保存')
    await loadTemplates()
    schedulePreview()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

/** 设为默认模板 */
async function setDefault(template: MessageTemplate) {
  if (!template.templateId) {
    ElMessage.warning('请先保存模板')
    return
  }
  try {
    const updated = await messageTemplateApi.setDefault(template.templateId)
    if (draft.value.templateId === template.templateId) {
      draft.value = { ...updated }
    }
    ElMessage.success('已设为默认模板')
    await loadTemplates()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '设置失败')
  }
}

/** 删除模板 */
async function remove(template: MessageTemplate) {
  if (!template.templateId) return
  try {
    await ElMessageBox.confirm(`删除模板「${template.templateName}」？`, '删除模板', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await messageTemplateApi.remove(template.templateId)
    ElMessage.success('模板已删除')
    if (draft.value.templateId === template.templateId) {
      createNew()
    }
    await loadTemplates()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** 预览（防抖，避免输入时频繁请求） */
function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => void runPreview(), 450)
}

async function runPreview() {
  const content = draft.value.templateContent
  if (!content.trim()) {
    preview.value = null
    return
  }
  previewing.value = true
  try {
    preview.value = await messageTemplateApi.render({
      kind: kind.value,
      subject: kind.value === 'EMAIL' ? draft.value.templateSubject ?? undefined : undefined,
      content,
      variables: SAMPLE_VARIABLES,
    })
  } catch {
    preview.value = null
  } finally {
    previewing.value = false
  }
}

/** 页面挂载即初始化（独立页，不再由弹窗 v-model 驱动） */
onMounted(() => {
  draft.value = emptyDraft(kind.value)
  preview.value = null
  void Promise.all([loadTemplates(), loadVariables()]).then(() => schedulePreview())
})

watch(
  () => [draft.value.templateContent, draft.value.templateSubject],
  () => schedulePreview(),
)

onBeforeUnmount(() => {
  if (previewTimer) clearTimeout(previewTimer)
})

/** 当前种类的默认模板（页头展示用） */
const defaultName = computed(() => templates.value.find((t) => t.templateDefault)?.templateName ?? '未设置')
</script>

<template>
  <div class="msg-tpl" data-page="message-template">
    <!-- 页头 -->
    <header class="msg-tpl__head">
      <div>
        <h3>消息模板</h3>
        <span>短信 / 邮件模板的自定义与选择 · 收信时套用默认模板渲染（部署级共享）</span>
      </div>
      <span class="msg-tpl__default">当前默认：{{ defaultName }}</span>
    </header>

    <div class="mtp">
      <aside class="mtp__aside">
        <div class="mtp__tabs">
          <button type="button" :class="{ 'is-active': kind === 'SMS' }" @click="switchKind('SMS')">
            短信
          </button>
          <button type="button" :class="{ 'is-active': kind === 'EMAIL' }" @click="switchKind('EMAIL')">
            邮件
          </button>
        </div>
        <button type="button" class="mtp__new" @click="createNew">＋ 新建模板</button>

        <div v-if="loading" class="mtp__empty">加载中…</div>
        <div v-else-if="!templates.length" class="mtp__empty">该种类暂无模板</div>
        <div v-else class="mtp__list">
          <button
            v-for="t in templates"
            :key="t.templateId"
            type="button"
            class="mtp__item"
            :class="{ 'is-active': t.templateId === draft.templateId, 'is-off': t.templateEnabled === false }"
            @click="edit(t)"
          >
            <span class="mtp__item-main">
              <b>{{ t.templateName }}</b>
              <i>{{ t.templateCode }}</i>
            </span>
            <span v-if="t.templateDefault" class="mtp__badge">默认</span>
          </button>
        </div>
        <p v-if="errorText" class="mtp__error">{{ errorText }}</p>
      </aside>

      <section class="mtp__editor">
        <div class="mtp__row">
          <label class="mtp__field">
            <span>模板名称</span>
            <input v-model="draft.templateName" type="text" placeholder="如：验证码优先" />
          </label>
          <label class="mtp__field">
            <span>模板编码</span>
            <input v-model="draft.templateCode" type="text" placeholder="同种类内唯一" />
          </label>
          <label class="mtp__field mtp__field--sm">
            <span>排序</span>
            <input v-model.number="draft.templateSortOrder" type="number" min="0" />
          </label>
        </div>

        <label v-if="kind === 'EMAIL'" class="mtp__field">
          <span>邮件主题</span>
          <input v-model="draft.templateSubject" type="text" placeholder="支持 {{变量}}" />
        </label>

        <div class="mtp__field">
          <span>模板正文（{{ kind === 'SMS' ? '短信' : '邮件' }}，支持 &#123;&#123;变量&#125;&#125; 占位）</span>
          <textarea
            ref="contentRef"
            v-model="draft.templateContent"
            class="mtp__content"
            rows="7"
            spellcheck="false"
          />
        </div>

        <div class="mtp__vars">
          <span class="mtp__vars-label">插入变量</span>
          <button
            v-for="name in variables"
            :key="name"
            type="button"
            class="mtp__var"
            @click="insertVariable(name)"
          >{{ name }}</button>
        </div>

        <div class="mtp__toggle">
          <label>
            <input v-model="draft.templateEnabled" type="checkbox" />
            启用
          </label>
          <label>
            <input v-model="draft.templateDefault" type="checkbox" />
            设为默认
          </label>
        </div>

        <div class="mtp__preview">
          <div class="mtp__preview-head">
            <b>预览</b>
            <span>{{ previewing ? '渲染中…' : '示例变量' }}</span>
          </div>
          <div class="mtp__preview-body">
            <div v-if="preview?.subject" class="mtp__preview-subject">{{ preview.subject }}</div>
            <pre class="mtp__preview-content">{{ preview?.content || '输入正文后自动预览' }}</pre>
            <div v-if="preview?.missing?.length" class="mtp__missing">
              未取值变量：{{ preview.missing.join('、') }}
            </div>
          </div>
        </div>

        <div class="mtp__footer">
          <button
            v-if="draft.templateId"
            type="button"
            class="mtp__btn is-danger"
            @click="remove(draft)"
          >删除</button>
          <button
            v-if="draft.templateId && !draft.templateDefault"
            type="button"
            class="mtp__btn"
            @click="setDefault(draft)"
          >设为默认</button>
          <button type="button" class="mtp__btn is-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存模板' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.msg-tpl {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  gap: 14px;
  padding: 4px 0 8px;
}
.msg-tpl__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex: none;
}
.msg-tpl__head h3 {
  margin: 0 0 2px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}
.msg-tpl__head span {
  font-size: 12px;
  color: var(--text-secondary, #475569);
}
.msg-tpl__default {
  flex: none;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--el-fill-color-light, #f1f5f9);
  color: var(--text-secondary, #475569);
}
.mtp {
  display: flex;
  gap: 14px;
  flex: 1;
  min-height: 0;
}
.mtp__aside {
  flex: none;
  width: 216px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 12px;
  border-right: 1px solid var(--border-default, #e2e9f3);
  min-height: 0;
}
.mtp__tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 8px;
  background: var(--el-fill-color-light, #f1f5f9);
}
.mtp__tabs button {
  flex: 1;
  border: 0;
  background: transparent;
  border-radius: 6px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--text-secondary, #475569);
  cursor: pointer;
  transition: background .15s ease, color .15s ease;
}
.mtp__tabs button.is-active {
  background: var(--bg-container, #fff);
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}
.mtp__new {
  border: 1px dashed var(--border-default, #e2e9f3);
  background: transparent;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--text-secondary, #475569);
  cursor: pointer;
  transition: border-color .15s ease, color .15s ease;
}
.mtp__new:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
}
.mtp__empty {
  padding: 18px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary, #94a3b8);
}
.mtp__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  min-height: 0;
  flex: 1;
}
.mtp__item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  padding: 7px 9px;
  text-align: left;
  cursor: pointer;
  transition: background .15s ease, border-color .15s ease;
}
.mtp__item:hover {
  background: var(--el-fill-color-lighter, #f8fafc);
}
.mtp__item.is-active {
  background: color-mix(in srgb, var(--el-color-primary, #409eff) 8%, transparent);
  border-color: color-mix(in srgb, var(--el-color-primary, #409eff) 30%, transparent);
}
.mtp__item.is-off {
  opacity: .55;
}
.mtp__item-main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}
.mtp__item-main b {
  font-size: 12px;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mtp__item-main i {
  font-size: 11px;
  font-style: normal;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--text-tertiary, #94a3b8);
}
.mtp__badge {
  flex: none;
  font-size: 10px;
  color: var(--el-color-primary, #409eff);
  border: 1px solid color-mix(in srgb, var(--el-color-primary, #409eff) 35%, transparent);
  border-radius: 4px;
  padding: 0 5px;
}
.mtp__error {
  margin: 0;
  font-size: 11px;
  color: var(--el-color-danger, #f56c6c);
}
.mtp__editor {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.mtp__row {
  display: flex;
  gap: 10px;
}
.mtp__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--text-secondary, #475569);
}
.mtp__field--sm {
  flex: none;
  width: 76px;
}
.mtp__field input,
.mtp__content {
  border: 1px solid var(--border-default, #e2e9f3);
  border-radius: 6px;
  padding: 5px 8px;
  font-size: 12px;
  color: var(--text-primary, #0f172a);
  background: var(--bg-container, #fff);
  outline: none;
  font-family: inherit;
}
.mtp__field input:focus,
.mtp__content:focus {
  border-color: var(--el-color-primary, #409eff);
}
.mtp__content {
  resize: vertical;
  min-height: 120px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.6;
}
.mtp__vars {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.mtp__vars-label {
  font-size: 11px;
  color: var(--text-tertiary, #94a3b8);
}
.mtp__var {
  border: 1px solid var(--border-default, #e2e9f3);
  background: var(--bg-elevated, #f8fafc);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--text-secondary, #475569);
  cursor: pointer;
  transition: border-color .15s ease, color .15s ease;
}
.mtp__var:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
}
.mtp__toggle {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-secondary, #475569);
}
.mtp__toggle label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.mtp__preview {
  border: 1px solid var(--border-default, #e2e9f3);
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg-elevated, #f8fafc);
}
.mtp__preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 7px 10px;
  border-bottom: 1px solid var(--border-default, #e2e9f3);
}
.mtp__preview-head b {
  font-size: 12px;
  color: var(--text-primary, #0f172a);
}
.mtp__preview-head span {
  font-size: 11px;
  color: var(--text-tertiary, #94a3b8);
}
.mtp__preview-body {
  padding: 9px 10px;
}
.mtp__preview-subject {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
  margin-bottom: 6px;
}
.mtp__preview-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-secondary, #475569);
  font-family: inherit;
}
.mtp__missing {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-color-warning, #e6a23c);
}
.mtp__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex: none;
}
.mtp__btn {
  border: 1px solid var(--el-border-color, #dcdfe6);
  background: var(--el-fill-color-blank, #fff);
  color: var(--el-text-color-regular, #606266);
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all .15s ease;
}
.mtp__btn:hover {
  border-color: var(--el-color-primary-light-5, #a0cfff);
  color: var(--el-color-primary, #409eff);
}
.mtp__btn.is-primary {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary, #409eff);
  color: #fff;
}
.mtp__btn.is-danger {
  margin-right: auto;
  border-color: color-mix(in srgb, var(--el-color-danger, #f56c6c) 45%, transparent);
  color: var(--el-color-danger, #f56c6c);
}
.mtp__btn.is-primary:disabled {
  opacity: .55;
  cursor: not-allowed;
}
</style>
