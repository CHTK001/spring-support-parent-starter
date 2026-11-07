<!--
  模板设计器（两模式：拖拽设计 + Vue 沙盒）

  布局：
    - 左栏顶部：「拖拽设计 / Vue 沙盒」两档切换；下方按模式变换面板：
        拖拽：组件库 / 数据源（可拖拽元素，heat-map 拖到画布）
        Vue 沙盒：文件列表（template.vue / data.json）+ 使用说明
    - 右栏随模式「整块」切换：
        拖拽：中间 WYSIWYG 编辑区（vue-plugin-hiprint 的 PrintTemplate.design）
              + 右侧元素属性面板（settingContainer）
        Vue 沙盒：代码编辑器（行号 + textarea）+ iframe 安全预览（sandboxed）+ 变量数据面板
    - 顶栏：返回 / 模板名 / （拖拽时）撤销重做、主题、预览 / 保存 / 打印

  关键点：
    - json 模板描述由 `templateApi.designer()` 拿到；拖拽模式编辑过程 onDataChanged 自动记录
    - Vue 沙盒的代码与变量通过 srcdoc 写进 `sandbox="allow-scripts"` 的 iframe，
      在沙盒内用内置迷你运行时渲染（{{}} / v-if / v-for / v-show / v-text / :style / @click / v-model），
      与宿主页面完全隔离 —— 模板代码跑不起来也不会影响主站
    - 两种模式「保存」都写回模板 content；`customDesign=false` 时只读（进入即置灰，但仍可预览/打印）
    - 依赖 jquery + vue-plugin-hiprint（皆为全局 UMD 包，仅拖拽模式用到）
-->
<template>
  <div class="designer">
    <!-- ==================== 顶栏 ==================== -->
    <header class="designer-header">
      <ReButton icon="ArrowLeft" @click="onBack">返回</ReButton>
      <ReInput v-model="templateName" class="designer-title" disabled />
      <div class="designer-actions">
        <!-- 拖拽模式专属：撤销 / 重做 / 主题 -->
        <template v-if="mode === 'drag'">
          <ReButton text @click="onUndo" :disabled="!historyUndo">撤销</ReButton>
          <ReButton text @click="onRedo" :disabled="!historyRedo">重做</ReButton>
          <ReSelect
            :data="themeOptions"
            v-model="selectedThemeId"
            @change="onThemeChange"
            placeholder="选主题"
            class="designer-theme"
            size="default"
          />
          <ReButton type="warning" plain @click="onPreview">预览</ReButton>
        </template>
        <ReButton type="primary" @click="onSave" :loading="saving" :disabled="!customDesign">保存</ReButton>
        <ReButton type="success" plain @click="onPrint" :loading="printing">打印</ReButton>
      </div>
    </header>

    <!-- ==================== 只读提示（提供商不支持自定义设计） ==================== -->
    <div v-if="!customDesign" class="designer-readonly">
      <ReIcon name="InfoFilled" :size="13" />
      该提供商不支持自定义模板设计，本模板为只读（可预览 / 打印）。
    </div>

    <!-- ==================== 主体 ==================== -->
    <div class="designer-body">
      <!-- 左侧：模式切换 + 模式面板 -->
      <aside class="designer-left" :class="{ 'is-collapsed': !leftVisible }">
        <div class="designer-switch" role="tablist">
          <button
            v-for="m in designModes"
            :key="m.key"
            type="button"
            role="tab"
            :aria-selected="mode === m.key"
            :disabled="m.key === 'sandbox' && !customDesign"
            :title="m.key === 'sandbox' && !customDesign ? '该提供商不支持自定义设计' : m.label"
            class="designer-switch__item"
            :class="{ 'is-on': mode === m.key }"
            @click="mode = m.key"
          >
            <ReIcon :name="m.icon" :size="14" />
            {{ m.label }}
          </button>
        </div>

        <!-- 拖拽：组件库 / 数据源 -->
        <template v-if="mode === 'drag'">
          <div class="designer-tabs">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              type="button"
              class="designer-tab"
              :class="{ active: dragTab === tab.key }"
              @click="dragTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>
          <div class="designer-panel" ref="libraryRef">
            <template v-if="dragTab === 'components'">
              <div
                v-for="item in componentList"
                :key="item.tid"
                class="ep-draggable-item"
                :data-tid="item.tid"
                :title="item.label"
              >
                <div class="ep-draggable-item__label">{{ item.label }}</div>
              </div>
            </template>
            <template v-else>
              <div
                v-for="item in sourceList"
                :key="item.tid"
                class="ep-draggable-item"
                :data-tid="item.tid"
                :title="item.label"
              >
                <div class="ep-draggable-item__label">{{ item.label }}</div>
              </div>
            </template>
          </div>
        </template>

        <!-- Vue 沙盒：文件列表 + 说明 -->
        <template v-else>
          <div class="designer-group">沙盒文件</div>
          <ul class="sandbox-files">
            <li class="is-active" @click="activeFile = 'template.vue'">
              <ReIcon name="Document" :size="13" /> template.vue
            </li>
            <li @click="activeFile = 'data.json'">
              <ReIcon name="Document" :size="13" /> data.json
            </li>
          </ul>
          <div class="designer-group">说明</div>
          <p class="sandbox-tip">
            左侧代码基于 Vue 模板语法编写，右侧在隔离的 iframe 内实时渲染（&#123;&#123; 插值 &#125;&#125; / v-if / v-for / v-show /
            v-text / :style / @click / v-model）。变量面板的数据会覆盖模板脚本里的默认值；「保存」把源码写回模板
            content。
          </p>
        </template>
      </aside>

      <!-- ==================== 拖拽模式：中间画布 + 右侧属性 ==================== -->
      <template v-if="mode === 'drag'">
        <main class="designer-center" ref="canvasRef">
          <div class="designer-canvas">
            <div :id="tplContainerId" class="hiprint-canvas" ref="tplRef"></div>
          </div>
        </main>
        <aside class="designer-right" v-if="settingVisible">
          <div :id="settingContainerId" ref="settingRef"></div>
        </aside>
      </template>

      <!-- ==================== Vue 沙盒：代码编辑器 + iframe 预览 + 变量面板 ==================== -->
      <div v-else class="sandbox">
        <!-- 代码编辑器 -->
        <section class="sandbox__editor">
          <div class="sandbox__bar">
            <span class="sandbox__file">{{ sandboxFileLabel }}</span>
            <span class="sandbox__badge">Vue SFC</span>
            <span class="sandbox__spacer" />
            <ReButton text :show-text="false" icon="Refresh" :icon-size="22" tip="刷新预览" @click="refreshPreview" />
          </div>
          <div class="code-editor">
            <div class="code-editor__gutter" ref="gutterRef">
              <div v-for="n in codeLineCount" :key="n" class="code-editor__ln">{{ n }}</div>
            </div>
            <textarea
              v-model="sandboxSource"
              class="code-editor__input"
              spellcheck="false"
              wrap="off"
              @scroll="syncGutter"
            ></textarea>
          </div>
        </section>

        <!-- 预览 + 变量 -->
        <section class="sandbox__side">
          <div class="sandbox__preview">
            <div class="sandbox__bar">
              <span class="sandbox__badge">实时预览</span>
              <span class="sandbox__spacer" />
              <span class="sandbox__badge is-muted">sandboxed iframe</span>
            </div>
            <iframe
              ref="frameRef"
              class="sandbox__frame"
              :srcdoc="previewDoc"
              sandbox="allow-scripts"
              title="Vue 沙盒预览"
            ></iframe>
          </div>
          <div class="sandbox__vars">
            <div class="sandbox__bar">
              <span class="sandbox__badge">注入变量</span>
              <span class="sandbox__spacer" />
              <span v-if="varsError" class="sandbox__err"><ReIcon name="WarningFilled" :size="12" />JSON 解析失败</span>
              <span v-else class="sandbox__ok">已应用 {{ appliedVarKeys.length }} 个变量</span>
            </div>
            <textarea
              v-model="sandboxVars"
              class="sandbox__vars-input"
              spellcheck="false"
              :class="{ 'is-error': varsError }"
              placeholder='{ "title": "示例", "items": ["甲","乙"] }'
            ></textarea>
          </div>
        </section>
      </div>
    </div>

    <!-- ==================== 打印机选择抽屉（拖拽模式） ==================== -->
    <ReDrawer
      v-model="printerVisible"
      :title="''"
      size="520"
      :show-window-shell="false"
      :close-on-click-modal="false"
      show-close
    >
      <RePrinter
        mode="selector"
        title="选择打印机"
        :printer="selectedPrinter"
        @print="onPrinterConfirm"
      />
    </ReDrawer>
  </div>
</template>

<script setup lang="ts">
/**
 * 模板设计器：拖拽设计（hiprint） + Vue 沙盒（隔离 iframe 预览）。
 *
 * <p>作为工作区页签渲染（prop 传入 {@code templateId}），也可作为独立路由页渲染
 * （挂回 {@code route.params.id}）。离开时通过 {@code back} 事件通知宿主，保存后发 {@code saved}。</p>
 *
 * <p>拖拽模式依赖 UMD 全局：{@code window.jQuery}、{@code hiprint}、{@code defaultElementTypeProvider}。</p>
 *
 * <p>Vue 沙盒：模板源码与变量以 srcdoc 方式灌入 {@code sandbox="allow-scripts"} 的 iframe，
 * 沙盒页内自带一个轻量运行时（插值 + 常用指令），与宿主隔离 —— 模板写坏了不会影响主站。</p>
 *
 * @since 4.0.0.80
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ReButton from '@repo/ui/re-button'
import ReIcon from '@repo/ui/re-icon'
import ReInput from '@repo/ui/re-input'
import ReSelect from '@repo/ui/re-select'
import ReDrawer from '@repo/ui/re-drawer'
import RePrinter from '../components/RePrinter.vue'
import $ from 'jquery'
import { hiprint, defaultElementTypeProvider } from 'vue-plugin-hiprint'
import 'vue-plugin-hiprint/dist/print-lock.css'

import { templateApi } from '../api/templateApi'
import type { TemplateDesignerPayload } from '../types/template'
import type { Theme } from '../themes/themes'
import { THEMES, applyTheme } from '../themes/themes'

/** 工作区页签模式：宿主传入的模板 ID（独立路由页时不传） */
const props = withDefaults(
  defineProps<{
    templateId?: string
    /** 提供商是否支持自定义设计：false 时只读（不进入/禁止沙盒与保存） */
    customDesign?: boolean
  }>(),
  { customDesign: true }
)

/** 行为通知宿主（置于路由模式时宿主不监听到，页面内自行消化） */
const emit = defineEmits<{
  /** 返回：回到模板详情页签 */
  (e: 'back'): void
  /** 打开预览页签 */
  (e: 'preview'): void
  /** 保存成功（写回 content 后），宿主可刷新模板列表 */
  (e: 'saved'): void
}>()

/** 设计器模式：拖拽 / Vue 沙盒 */
type DesignerMode = 'drag' | 'sandbox'

const route = useRoute()
const router = useRouter()

/** 当前模板 ID（prop 优先，独立路由页挂回 route.params） */
const templateId = computed(() => props.templateId || (route.params.id as string) || '')

/** hiprint 画布容器（随模板隔离，避免多页签时 DOM 冲突） */
const tplContainerId = computed(() => `hiprint-printTemplate-${templateId.value}`)
/** 属性设置容器（随模板隔离） */
const settingContainerId = computed(() => `hiprint-setting-${templateId.value}`)

/** 模板名（只读显示） */
const templateName = ref('')

/** 左侧面板显隐 */
const leftVisible = ref(true)
const settingVisible = ref(true)

/** 当前模式 */
const mode = ref<DesignerMode>('drag')
/** 模式档位 */
const designModes: Array<{ key: DesignerMode; label: string; icon: string }> = [
  { key: 'drag', label: '拖拽设计', icon: 'Rank' },
  { key: 'sandbox', label: 'Vue 沙盒', icon: 'Monitor' }
]

// ==================== 拖拽模式（hiprint） ====================
/** 打印机抽屉 */
const printerVisible = ref(false)
/** 选中的（或欲用的）打印机名 */
const selectedPrinter = ref('')
/** 默认的打印机（后端设的默认 → 「打印」不弹抽屉，直接选它） */
const defaultPrinter = ref('')
/** 保存/打印按键态 */
const saving = ref(false)
const printing = ref(false)

/** 主题菜单 */
const themeOptions = computed(() => THEMES.map((t: Theme) => ({ label: t.name, value: t.id })))
/** 当前选中的主题 id */
const selectedThemeId = ref(THEMES[0].id)
/** 最近一次应用的主题 */
const selectedTheme = ref<Theme | null>(THEMES[0])

/** 拖拽左侧标签页 */
const dragTab = ref('components')
const tabs = [
  { key: 'components', label: '模板组件' },
  { key: 'sources', label: '数据源' }
]

/** 左侧组件列表 */
const componentList = ref<{ tid: string; label: string }[]>([
  { tid: 'text', label: '单行文本' },
  { tid: 'longText', label: '多行文本' },
  { tid: 'text2', label: '多段文本' },
  { tid: 'table', label: '表格' },
  { tid: 'barcode', label: '条形码' },
  { tid: 'qrcode', label: '二维码' },
  { tid: 'image', label: '图片' },
  { tid: 'rect', label: '矩形' },
  { tid: 'oval', label: '椭圆' },
  { tid: 'vline', label: '竖线' },
  { tid: 'hline', label: '横线' }
])

/** 数据源列表（虚拟） */
const sourceList = ref<{ tid: string; label: string }[]>([
  { tid: 'text', label: '静态文本' },
  { tid: 'longText', label: '长文本' }
])

/** 撤销 / 重做开关 */
const historyUndo = ref(false)
const historyRedo = ref(false)

/** hiprint 实例 */
const printTemplate = ref<any>(null)
const tplRef = ref<HTMLDivElement | null>(null)
const libraryRef = ref<HTMLDivElement | null>(null)
const settingRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLDivElement | null>(null)

// ==================== Vue 沙盒 ====================
/** 沙盒代码（template.vue） */
const sandboxSource = ref('')
/** 变量 JSON（data.json） */
const sandboxVars = ref('')
/** 当前查看的沙盒文件（仅左栏展示用） */
const activeFile = ref('template.vue')
/** 刷新计时器（防抖构建 srcdoc） */
let previewTimer: ReturnType<typeof setTimeout> | undefined
/** 手动刷新计数（强制重建 srcdoc） */
const previewTick = ref(0)

/** 沙盒文件标签 */
const sandboxFileLabel = computed(() => (activeFile.value === 'data.json' ? 'data.json' : 'template.vue'))
/** 代码行数 */
const codeLineCount = computed(() => Math.max(1, sandboxSource.value.split('\n').length))

/** 变量解析结果 */
const sandboxVarsParsed = computed<Record<string, unknown>>(() => {
  const text = sandboxSource.value.trim() ? '' : ''
  void text
  const raw = sandboxVars.value.trim()
  if (!raw) return {}
  try {
    const obj = JSON.parse(raw) as unknown
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? (obj as Record<string, unknown>) : {}
  } catch {
    return {}
  }
})
/** JSON 是否解析失败 */
const varsError = computed(() => {
  const raw = sandboxVars.value.trim()
  if (!raw) return false
  try {
    JSON.parse(raw)
    return false
  } catch {
    return true
  }
})
/** 已应用的变量键 */
const appliedVarKeys = computed(() => Object.keys(sandboxVarsParsed.value))

/** 沙盒预览文档（srcdoc）：内置轻量运行时 + 模板源码 + 变量，全部在 sandboxed iframe 内执行 */
const previewDoc = computed(() => buildSandboxDoc(sandboxSource.value, sandboxVarsParsed.value, previewTick.value))

/** 编辑器滚动与行号同步 */
function syncGutter(): void {
  const input = document.querySelector('.code-editor__input') as HTMLTextAreaElement | null
  const gutter = gutterRef.value
  if (input && gutter) gutter.scrollTop = input.scrollTop
}

/** 手动刷新预览 */
function refreshPreview(): void {
  previewTick.value += 1
}

/** 沙盒代码变化后防抖刷新预览 */
watch(sandboxSource, () => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => {
    previewTick.value += 1
  }, 400)
})

// ==================== 加载模板 ====================
onMounted(async () => {
  const id = templateId.value
  if (!id) {
    ElMessage.error('未指定模板')
    router.replace('/template')
    return
  }

  try {
    const payload: TemplateDesignerPayload = await templateApi.designer(id)
    templateName.value = payload.info.name || payload.info.subtitle || '未命名模板'
    // Vue 沙盒默认源码：模板已有的 content（可能为 hiprint JSON），为空则给一份开箱即用的示例
    const rawContent = toContentString(payload.content)
    const isHiprintJson = /^[\{\[]/.test(rawContent.trim())
    mode.value = payload.mode === 'sandbox' || !isHiprintJson ? 'sandbox' : 'drag'
    sandboxSource.value = rawContent.trim() ? rawContent : STARTER_SANDBOX_SOURCE
    sandboxVars.value = STARTER_SANDBOX_VARS

    // 先取默认的打印机（后端的打印机列表里有 isDefault 标记），让「打印」直接选它
    await loadDefaultPrinter()
    // 拖拽模式才初始化 hiprint 画布（Vue 沙盒模式完全用不到 hiprint，不初始化）
    if (mode.value === 'drag') await createDesign(rawContent || '')
  } catch (err) {
    ElMessage.error(String(err))
    router.replace('/template')
  }
})

/** 把后端 content 统一成字符串（可能是 JSON 对象 / 字符串 / 空） */
function toContentString(content: unknown): string {
  if (content == null) return ''
  if (typeof content === 'string') return content
  try {
    return JSON.stringify(content, null, 2)
  } catch {
    return String(content)
  }
}

/** 加载默认的打印机 */
async function loadDefaultPrinter(): Promise<void> {
  try {
    const list = await templateApi.printerList()
    const found = list.find(p => p.isDefault)
    defaultPrinter.value = found?.name ?? ''
    selectedPrinter.value = found?.name ?? ''
    if (found) {
      ElMessage.info(`已选默认打印机：${found.name}`)
    }
  } catch (_err) {
    /* 没有打印机就手动选（抽屉兜底） */
  }
}

/** hiprint 框架是否已经过 init */
let hiprintInitialized = false
/** 左侧面板的拖拽元素是否已经注册 */
let draggedItemsRegistered = false

/**
 * 创建 PrintTemplate 并设计画布（首次进入 / 主题变更后重建画布都走它）。
 *
 * @param template hiprint JSON 描述（主题变更后这里是应用了主题的描述）
 */
async function createDesign(template: string): Promise<void> {
  // 1. 把 jquery 挂到 window 上（hiprint UMD 版用 window.jQuery）
  if (!window.jQuery) window.jQuery = $
  if (!window.$) window.$ = $

  // 2. 全局初始化 hiprint（可拖拽元素提供者）——只全量初始化一次
  if (!hiprintInitialized) {
    hiprint.init({
      providers: [new defaultElementTypeProvider()]
    })
    hiprintInitialized = true
  }

  // 3. 把左侧的 .ep-draggable-item 注册为可拖拽元素——只注册一次
  if (!draggedItemsRegistered) {
    buildDraggableItems()
    draggedItemsRegistered = true
  }

  // 4. 创建 PrintTemplate 并进入设计器
  const tpl = new hiprint.PrintTemplate({
    template,
    settingContainer: '#' + settingContainerId.value,
    paginationContainer: '.hiprint-printPagination',
    providers: [new defaultElementTypeProvider()],
    fontList: [
      { title: '微软雅黑', value: 'Microsoft YaHei' },
      { title: '黑体', value: 'STHeitiSC-Light' },
      { title: '宋体', value: 'SimSun' },
      { title: '仿宋', value: 'FangSong' },
      { title: '楷体', value: 'STKaiti' },
      { title: 'Cursive', value: 'cursive' }
    ],
    dataMode: 1,
    history: true,
    onDataChanged: () => {
      printTemplate.value = tpl
      historyUndo.value = true
      historyRedo.value = true
    },
    onUpdateError: (e: any) => {
      ElMessage.error('模板更新失败：' + String(e))
    }
  })

  // 5. 进入设计器（渲染画布 + 属性面板 + 工具条 + 分页容器）
  tpl.design('#' + tplContainerId.value)
  printTemplate.value = tpl
}

/** 把左侧面板的 .ep-draggable-item 注册到 hiprint 元素管理 */
function buildDraggableItems(): void {
  const panel = libraryRef.value
  if (!panel) return
  const items = panel.querySelectorAll('.ep-draggable-item') as NodeListOf<HTMLElement>
  const $items = $(items) as any
  if (window.hiprint?.PrintElementTypeManager?.buildByHtml) {
    window.hiprint.PrintElementTypeManager.buildByHtml($items)
  }
}

// ==================== 操作 ====================
function onBack(): void {
  emit('back')
}

/** 撤销（拖拽） */
function onUndo(): void {
  printTemplate.value?.undo()
  ElMessage.info('撤销')
}

/** 重做（拖拽） */
function onRedo(): void {
  printTemplate.value?.redo()
  ElMessage.info('重做')
}

/** 保存：拖拽写 getJson()，沙盒写模板源码，都落到 content */
async function onSave(): Promise<void> {
  if (!props.customDesign) {
    ElMessage.warning('该提供商不支持自定义设计')
    return
  }
  saving.value = true
  try {
    let content: string
    if (mode.value === 'drag') {
      const tpl = printTemplate.value
      if (!tpl) {
        ElMessage.warning('工作区为空')
        saving.value = false
        return
      }
      content = tpl.getJson()
      await templateApi.update(templateId.value, {
        content,
        name: templateName.value
      })
    } else {
      if (!sandboxSource.value.trim()) {
        ElMessage.warning('模板代码不能为空')
        saving.value = false
        return
      }
      content = sandboxSource.value
      await templateApi.update(templateId.value, {
        content,
        name: templateName.value
      })
    }
    ElMessage.success('保存成功')
    emit('saved')
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    saving.value = false
  }
}

/** 打开只读预览页签（仅拖拽模式的 hiprint 模板） */
function onPreview(): void {
  if (mode.value !== 'drag') {
    ElMessage.info('Vue 沙盒请直接看右侧实时预览')
    return
  }
  const tpl = printTemplate.value
  if (!tpl) {
    ElMessage.warning('工作区为空')
    return
  }
  emit('preview')
}

/** 打印：拖拽走 hiprint 引擎，沙盒直接打印 iframe 里的渲染结果 */
async function onPrint(): Promise<void> {
  if (mode.value === 'sandbox') {
    const frame = frameRef.value
    if (frame?.contentWindow) {
      printing.value = true
      try {
        frame.contentWindow.focus()
        frame.contentWindow.print()
        ElMessage.success('已发送打印任务')
      } catch (err) {
        ElMessage.error(String(err))
      } finally {
        printing.value = false
      }
    } else {
      ElMessage.warning('预览尚未就绪')
    }
    return
  }
  const tpl = printTemplate.value
  if (!tpl) {
    ElMessage.warning('工作区为空')
    return
  }
  if (defaultPrinter.value) return onPrintWith(tpl, defaultPrinter.value)
  selectedPrinter.value = ''
  printerVisible.value = true
}

/** 按指定打印机发起（把打印机名和连接参数交给底层引擎） */
async function onPrintWith(tpl: any, name: string): Promise<void> {
  if (!name) {
    ElMessage.warning('请选择一台打印机')
    return
  }
  printing.value = true
  try {
    tpl.print({}, { printer: name, smartPrint: false }, () => {
      ElMessage.info(`已发送到「${name}」`)
    })
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    printing.value = false
  }
}

/** 选机抽屉确认 */
function onPrinterConfirm(name: string): void {
  printerVisible.value = false
  void onPrintWith(printTemplate.value, name)
}

/** 选主题：应用主题 → 写入设计描述 → 重建画布 */
async function onThemeChange(id: string): Promise<void> {
  if (!id) return
  const t = THEMES.find(item => item.id === id) ?? THEMES[0]
  if (t.id === selectedThemeId.value) return
  selectedThemeId.value = t.id
  const tpl = printTemplate.value
  if (!tpl) {
    ElMessage.warning('工作区为空')
    return
  }
  try {
    const json = tpl.getJson()
    const themed = applyTheme(json, t)
    selectedTheme.value = t
    await rebuildDesign(themed)
    ElMessage.success(`主题「${t.name}」已应用（已写进设计描述）`)
  } catch (err) {
    ElMessage.error(String(err))
  }
}

/** 重建画布（主题变更时） */
async function rebuildDesign(template: string): Promise<void> {
  printTemplate.value = null
  await createDesign(template)
}

// ==================== Vue 沙盒预览文档构建 ====================
const gutterRef = ref<HTMLDivElement | null>(null)
const frameRef = ref<HTMLIFrameElement | null>(null)

/** 从 SFC 源码里抽取指定块内容（如 template / script） */
function extractBlock(code: string, tag: string): string {
  const m = code.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'))
  return m ? m[1] : ''
}

/** 安全嵌入一段 JS 字符串（转义结束标签，防止提前闭合） */
function embedJs(value: string): string {
  return JSON.stringify(value).replace(/<\/script/gi, '<\\/script')
}

/**
 * 构建 iframe 的 srcdoc：
 * 模板源码 + 变量被嵌进沙盒页，页内自带轻量 Vue 风格运行时（{{}} / v-if / v-for / v-show / v-text / :style / @click / v-model），
 * iframe 只有 sandbox=allow-scripts，与宿主完全隔离。
 */
function buildSandboxDoc(code: string, vars: Record<string, unknown>, _tick: number): string {
  const templateBlock = extractBlock(code, 'template')
  const scriptBlock = embedJs(extractBlock(code, 'script'))
  const rawEmbed = embedJs(code)
  const hasTemplate = templateBlock.trim().length > 0

  return `<!doctype html>
<html><head><meta charset="utf-8" />
<style>
  html,body{margin:0;padding:12px;font-family:"Microsoft YaHei",ui-sans-serif,sans-serif;color:#1f2937}
  .raw{white-space:pre-wrap;word-break:break-all;background:#fff8f0;border:1px solid #ffe1b8;border-radius:6px;padding:10px;font-size:12px;line-height:1.6}
  .err{position:fixed;left:0;right:0;bottom:0;background:#fde8e8;color:#b91c1c;border-top:1px solid #f5c2c2;padding:6px 12px;font-size:12px;z-index:9}
</style></head>
<body>
<div id="__app__"></div>
<script>
(function(){
'use strict';
var __template = ${embedJs(hasTemplate ? templateBlock : `<pre class="raw">{{ __raw }}</pre>`)};
var __script = ${scriptBlock};
var __raw = ${rawEmbed};
var __external = ${safeVarsJson(vars)};
var __err__ = null;
function __eval(expr, scope) {
  try { return new Function('ctx', 'with(ctx||{}){ return (' + expr + ') }')(scope); }
  catch (e) { return undefined; }
}
function __fmt(v){ return v === null || v === undefined ? '' : String(v); }
function __childScope(parent, extra) {
  var s = {};
  for (var k in parent) { if (Object.prototype.hasOwnProperty.call(parent, k)) s[k] = parent[k]; }
  for (var k in extra) { if (Object.prototype.hasOwnProperty.call(extra, k)) s[k] = extra[k]; }
  return s;
}
// ---- 解析模板代码里的组件脚本（export default { data, methods }）----
var cmpt = {};
if (__script) {
  var src = __script.replace(/export\\s+default/, 'window.__cmpt__ =');
  try { new Function('window', src)(window); } catch (e) { __err__ = String(e); }
  cmpt = window.__cmpt__ || {};
}
var base = (typeof cmpt.data === 'function') ? (cmpt.data() || {}) : ((cmpt.data && typeof cmpt.data === 'object') ? cmpt.data : {});
for (var k in __external) { if (Object.prototype.hasOwnProperty.call(__external, k)) base[k] = __external[k]; }
var ctx = {};
for (var k in base) { ctx[k] = base[k]; }
for (var k in (cmpt.methods || {})) { ctx[k] = cmpt.methods[k]; }
ctx.__raw = __raw;
function __interp(node, scope) {
  if (node.nodeType === 3) {
    if (node.nodeValue.indexOf('{{') >= 0) {
      node.nodeValue = node.nodeValue.replace(/\\{\\{\\s*([\\s\\S]+?)\\s*\\}\\}/g, function (m, e) {
        return __fmt(__eval(e, scope));
      });
    }
    return;
  }
  if (node.nodeType !== 1) return;
  var el = node;
  var attrs = el.attributes ? Array.prototype.slice.call(el.attributes) : [];
  var ifAttr = null, forAttr = null, modelAttr = null;
  for (var i = 0; i < attrs.length; i++) {
    var n = attrs[i].name;
    if (n === 'v-if' || n === 'v-else-if' || n === 'v-else') ifAttr = ifAttr || n;
    if (n === 'v-for') forAttr = n;
    if (n === 'v-model') modelAttr = n;
  }
  /** 按属性名取值（attrs 是数组，不能用属性名当下标） */
  function attrValue(name) {
    var v = el.getAttribute(name);
    return v === null ? '' : v;
  }
  // v-for：复制节点逐项渲染
  if (forAttr) {
    var forSpec = attrValue(forAttr).replace(/\\s+/g, ' ').trim();
    var parts = forSpec.split(/\\s+(?:in|of)\\s+/);
    var listExpr = parts[1], left = parts[0] || 'item';
    var leftVars = left.replace(/[\\(\\)]/g, '').split(',').map(function(s){ return s.trim(); });
    var valueVar = leftVars[0] || 'item';
    var indexVar = leftVars[1] || 'index';
    if (listExpr === undefined) return;
    var list = __eval(listExpr, scope) || [];
    var frag = document.createDocumentFragment();
    try {
      for (var li = 0; li < list.length; li++) {
        var clone = el.cloneNode(true);
        clone.removeAttribute('v-for');
        var extra = {};
        extra[valueVar] = list[li];
        extra[indexVar] = li;
        // 克隆节点自身就是待渲染元素，用 __interp 处理它（__render 会重复插入整段模板 → 无限递归）
        __interp(clone, __childScope(scope, extra));
        frag.appendChild(clone);
      }
    } catch (e) { __err__ = __err__ || String(e); }
    el.parentNode.replaceChild(frag, el);
    return;
  }
  // v-if / v-else-if / v-else 链：拉取整条链，命中一个分支，移除其余
  if (ifAttr) {
    var chain = [el];
    var nxt = el.nextSibling;
    while (nxt && nxt.nodeType === 1 && (nxt.getAttribute && (nxt.hasAttribute('v-else') || nxt.hasAttribute('v-else-if')))) {
      chain.push(nxt);
      nxt = nxt.nextSibling;
    }
    var chosen = null;
    var hit = false;
    for (var ci = 0; ci < chain.length; ci++) {
      var cnode = chain[ci];
      var cond = cnode.getAttribute('v-if') || cnode.getAttribute('v-else-if');
      var pass = false;
      if (cond) {
        pass = !!__eval(cond, scope);
        if (pass && !hit) { chosen = cnode; hit = true; }
      } else if (cnode.hasAttribute('v-else')) {
        pass = !hit;
        if (pass && !chosen) { chosen = cnode; hit = true; }
      }
      void pass;
    }
    for (var cj = 0; cj < chain.length; cj++) {
      if (chain[cj] !== chosen) { chain[cj].parentNode.removeChild(chain[cj]); }
    }
    if (!chosen) { return; }
    el = chosen;
    el.removeAttribute('v-if'); el.removeAttribute('v-else-if'); el.removeAttribute('v-else');
  }
  // v-show
  var showAttr = el.getAttribute('v-show');
  if (showAttr) { el.style.display = __eval(showAttr, scope) ? '' : 'none'; el.removeAttribute('v-show'); }
  // v-text
  var textAttr = el.getAttribute('v-text');
  if (textAttr) { el.textContent = __fmt(__eval(textAttr, scope)); el.removeAttribute('v-text'); }
  // v-model（简单输入/文本域/复选）
  if (modelAttr) {
    var path = attrValue(modelAttr).trim();
    var isCheck = el.type === 'checkbox';
    if (isCheck) {
      if (__eval(path, scope)) el.checked = true;
      el.addEventListener('change', function(){ var assign = '__value = ' + path + '__wrap'; });
      el.addEventListener('change', function(){ try { scope[path] = el.checked; } catch (e) {} });
    } else {
      el.addEventListener('input', function(){ try { scope[path] = el.value; } catch (e) {} });
    }
    el.removeAttribute('v-model');
  }
  // :xxx / v-bind:xxx
  for (var b = 0; b < attrs.length; b++) {
    var bn = attrs[b].name;
    if (bn.charAt(0) === ':') {
      var prop = bn.slice(1), val = attrs[b].value;
      if (prop === 'style') {
        var st = __eval(val, scope);
        if (st && typeof st === 'object') {
          for (var sk in st) { if (Object.prototype.hasOwnProperty.call(st, sk)) try { el.style[sk] = st[sk]; } catch (e) {} }
        } else if (st) { el.setAttribute('style', __fmt(st)); }
      } else {
        el.setAttribute(prop, __fmt(__eval(val, scope)));
      }
      el.removeAttribute(bn);
    } else if (bn.indexOf('v-bind:') === 0) {
      var bp = bn.slice('v-bind:'.length);
      if (bp === 'style') {
        var s2 = __eval(attrs[b].value, scope);
        if (s2 && typeof s2 === 'object') { for (var sk2 in s2) { if (Object.prototype.hasOwnProperty.call(s2, sk2)) try { el.style[sk2] = s2[sk2]; } catch (e) {} } }
      } else { el.setAttribute(bp, __fmt(__eval(attrs[b].value, scope))); }
      el.removeAttribute(bn);
    } else if (bn.charAt(0) === '@' || bn.indexOf('v-on:') === 0) {
      var evt = bn.charAt(0) === '@' ? bn.slice(1) : bn.slice('v-on:'.length);
      var handler = attrs[b].value;
      el.addEventListener(evt, function(){ try { __eval(handler, scope); } catch (e) { __err__ = __err__ || String(e); } });
      el.removeAttribute(bn);
    }
  }
  // 递归子节点
  var kids = Array.prototype.slice.call(el.childNodes);
  for (var d = 0; d < kids.length; d++) __interp(kids[d], scope);
}
function __render(root, scope) {
  var doc = root.ownerDocument;
  var box = doc.createElement('div');
  box.innerHTML = __template;
  var kids = Array.prototype.slice.call(box.childNodes);
  for (var i = 0; i < kids.length; i++) { __interp(kids[i], scope); root.appendChild(kids[i]); }
}
var app = document.getElementById('__app__');
try { __render(app, ctx); } catch (e) { __err__ = __err__ || String(e); }
if (__err__) {
  var ebox = document.createElement('div');
  ebox.className = 'err';
  ebox.textContent = '运行时错误：' + __err__;
  document.body.appendChild(ebox);
}
})();
<\/script>
</body></html>`
}

/** 安全序列化变量 JSON（解析失败回退空对象） */
function safeVarsJson(vars: Record<string, unknown>): string {
  try {
    return JSON.stringify(vars)
  } catch {
    return '{}'
  }
}

/** Vue 沙盒的示例源码（content 为空时给用户开箱即用） */
const STARTER_SANDBOX_SOURCE = `<template>
  <div class="card">
    <h3>{{ title }}</h3>
    <p class="sub">{{ message }}</p>
    <ul>
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
    <span class="btn">共 {{ items.length }} 项</span>
  </div>
</template>

<script>
export default {
  data() {
    return {
      title: '示例模板',
      message: '在左侧改代码，右侧实时更新',
      items: ['项目甲', '项目乙', '项目丙']
    }
  }
}
<\/script>`

/** Vue 沙盒的示例变量（覆盖脚本默认值） */
const STARTER_SANDBOX_VARS = `{
  "title": "销售出库单",
  "message": "由变量面板注入的数据",
  "items": ["无线键盘 x2", "显示器支架 x1", "网线 x5"]
}`

onBeforeUnmount(() => {
  if (previewTimer) clearTimeout(previewTimer)
  // 清理 jQuery 与 hiprint 在全局态（页面内的单个设计器，随页面卸载）
  printTemplate.value = null
})
</script>

<style lang="scss" scoped>
.designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color-page);
  overflow: hidden;
}

/* ==================== 顶栏 ==================== */
.designer-header {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 12px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 8px;
}

.designer-title {
  flex: 1;
  text-align: center;
  font-weight: 500;
}

.designer-actions {
  display: flex;
  gap: 4px;
}

.designer-theme {
  width: 140px;
}

/* 只读提示条 */
.designer-readonly {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 12px;
  color: var(--el-color-warning-dark-2, #b88230);
  background: var(--el-color-warning-light-9, #fdf6ec);
  border-bottom: 1px solid var(--el-color-warning-light-7, #f5dab1);
}

/* ==================== 主体 ==================== */
.designer-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

/* 左侧面板 */
.designer-left {
  display: flex;
  flex: 0 0 240px;
  flex-direction: column;
  min-width: 200px;
  overflow: hidden;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  transition: width 0.2s;
}

.designer-left.is-collapsed {
  width: 0;
  min-width: 0;
  border-right: none;
}

/* 模式切换 */
.designer-switch {
  display: flex;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.designer-switch__item {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 38px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--el-color-primary);
  }

  &.is-on {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9, #ecf5ff);
    box-shadow: inset 0 -2px 0 var(--el-color-primary);
  }

  &:disabled {
    color: var(--el-text-color-placeholder, #c0c4cc);
    cursor: not-allowed;
  }
}

/* 拖拽面板标签页 */
.designer-tabs {
  display: flex;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}

.designer-tab {
  flex: 1;
  text-align: center;
  line-height: 36px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--el-text-color-regular);

  &.active {
    color: var(--el-color-primary);
    border-bottom: 2px solid var(--el-color-primary);
  }
}

.designer-panel {
  flex: 1;
  overflow: auto;
  padding: 12px;
}

.ep-draggable-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  margin-bottom: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  cursor: move;
  user-select: none;
  transition: border-color 0.2s, background 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.ep-draggable-item__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

/* 沙盒左栏 */
.designer-group {
  padding: 12px 12px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.sandbox-files {
  margin: 0;
  padding: 0 8px;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    font-size: 12px;
    color: var(--el-text-color-regular);
    border-radius: 6px;
    cursor: pointer;
    font-family: var(--el-font-family-mono, ui-monospace, monospace);

    &:hover {
      background: var(--el-fill-color-light);
    }

    &.is-active {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9, #ecf5ff);
    }
  }
}

.sandbox-tip {
  margin: 0;
  padding: 0 12px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

/* ==================== 拖拽画布 ==================== */
.designer-center {
  flex: 1;
  overflow: auto;
  position: relative;
  background: var(--el-fill-color);
}

.designer-canvas {
  padding: 24px;
  display: flex;
  justify-content: center;
}

.hiprint-canvas {
  width: 800pt;
  min-height: 1200pt;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

/* 拖拽右侧属性面板 */
.designer-right {
  flex: 0 0 320px;
  min-width: 280px;
  overflow: auto;
  border-left: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

/* ==================== Vue 沙盒右栏 ==================== */
.sandbox {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: var(--el-bg-color-page);
}

.sandbox__editor {
  display: flex;
  flex: 1 1 46%;
  flex-direction: column;
  min-width: 260px;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.sandbox__side {
  display: flex;
  flex: 1 1 54%;
  flex-direction: column;
  min-width: 260px;
  min-height: 0;
}

.sandbox__preview {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background: var(--el-fill-color-lighter, var(--el-fill-color-light));
}

.sandbox__frame {
  flex: 1;
  width: 100%;
  border: none;
  background: #fff;
}

.sandbox__vars {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  height: 168px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.sandbox__bar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sandbox__file {
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-weight: 600;
}

.sandbox__badge {
  padding: 0 7px;
  font-size: 10px;
  line-height: 17px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-radius: 999px;

  &.is-muted {
    color: var(--el-text-color-placeholder, #c0c4cc);
    background: var(--el-fill-color-light);
  }
}

.sandbox__spacer {
  flex: 1;
}

.sandbox__ok {
  font-size: 11px;
  color: var(--el-color-success);
}

.sandbox__err {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--el-color-danger);
}

/* 代码编辑器：行号 + textarea */
.code-editor {
  display: flex;
  flex: 1;
  min-height: 0;
  background: #fff;
}

.code-editor__gutter {
  flex: 0 0 44px;
  padding: 10px 6px 10px 0;
  overflow: hidden;
  text-align: right;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-right: 1px solid var(--el-border-color-lighter);
  user-select: none;
}

.code-editor__ln {
  height: 20px;
}

.code-editor__input {
  flex: 1;
  padding: 10px 12px;
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12.5px;
  line-height: 20px;
  color: var(--el-text-color-primary);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  white-space: pre;
  tab-size: 2;
}

.sandbox__vars-input {
  flex: 1;
  padding: 8px 12px;
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-primary);
  background: transparent;
  border: none;
  outline: none;
  resize: none;

  &.is-error {
    color: var(--el-color-danger);
  }
}
</style>