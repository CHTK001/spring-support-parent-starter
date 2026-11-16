<!--
  模板预览（只读）
  作为工作区页签渲染（接受 templateId prop），也可作为独立路由页渲染。

  仅加载模板设计描述并以 hiprint 的 print 模式渲染，不含设计器外壳与操作控件。
  流程：
    - 加载模板设计描述；创建只读 PrintTemplate（不进入 design，仅出 print 页面）
    - 有默认打印机：点「打印」直接发；没有：弹 RePrinter selector 抽屉选机再发
  打印机（dispatched to the print engine via tpl.print）：
    - tpl.print({}, { printer: name, smartPrint: false }, cb)

  注意：预览区用 print-lock.css 铺满画纸（page-break-after），样式为只读渲染。

  @since 4.0.0.70
  @author CH
-->
<template>
  <div class="preview">
    <!-- ==================== 顶栏 ==================== -->
    <header class="preview-header">
      <ReButton icon="ArrowLeft" @click="onBack">返回</ReButton>
      <ReInput v-model="templateName" class="preview-title" disabled />
      <div class="preview-actions">
        <ReButton
          type="primary"
          :disabled="!tpl || !defaultPrinter"
          @click="onPrint"
        >
          <ReIcon name="Printer" :size="12" />
          打印
        </ReButton>
        <ReButton text type="primary" :disabled="!tpl" @click="onSelectPrinter">
          选择打印机
        </ReButton>
      </div>
    </header>

    <!-- ==================== 只读画布 ==================== -->
    <div class="preview__body" ref="bodyRef">
      <div class="preview__empty" v-if="loading">加载中...</div>
      <div
        v-else-if="!tpl"
        class="preview__empty preview__empty--error"
      >
        {{ renderError || '未加载到预览内容' }}
      </div>
      <div id="preview-hiprint" ref="tplRef" class="preview__canvas"></div>
    </div>

    <!-- ==================== 打印机选择抽屉 ==================== -->
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
 * 只读预览：从后端取模板 JSON 描述，交给 hiprint 仅出 print 页面。
 * 使用前先确保 window.jQuery + hiprint.init 已在 @packages/app 的 index.html 中注入。
 *
 * @since 4.0.0.70
 */
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import $ from 'jquery'
import { hiprint } from 'vue-plugin-hiprint'
import 'vue-plugin-hiprint/dist/print-lock.css'

import ReIcon from '@repo/ui/re-icon'
import ReButton from '@repo/ui/re-button'
import ReInput from '@repo/ui/re-input'
import ReDrawer from '@repo/ui/re-drawer'
import RePrinter from '../components/RePrinter.vue'

import { templateApi } from '../api/templateApi'
import { errorText } from '../../shared/errorText'
import type { TemplateDesignerPayload } from '../types/template'

const props = defineProps<{
  /** 模板 ID（工作区页签模式时由宿主传入，独立路由页挂回 route.params） */
  templateId?: string
}>()

const route = useRoute()
const router = useRouter()

/** 当前使用的模板 ID（prop 优先，回退到路由参数） */
const id = computed(() => props.templateId || (route.params.id as string) || '')

/** 模板名（只读显示） */
const templateName = ref('')
/** 后端存的那台默认打印机（从 isDefault 取） */
const defaultPrinter = ref('')
/** 选机抽屉回写（所选打印机名） */
const selectedPrinter = ref('')
const printerVisible = ref(false)

const template = ref<TemplateDesignerPayload | null>(null)
const bodyRef = ref<HTMLDivElement | null>(null)
const tplRef = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const saving = ref(false)
/** 无法渲染时的具体原因（内容不是 hiprint 模板 / 渲染异常），空态直接展示 */
const renderError = ref('')
/** 只读 PrintTemplate（不进入 design 模式） */
const tpl = ref<any>(null)
/** hiprint 是否已 init（全局只初始化一次） */
let hiprintInitialized = false

/** 加载默认打印机（从后端打印机列表取 isDefault 标记） */
async function loadDefaultPrinter(): Promise<void> {
  try {
    const list = await templateApi.printerList()
    const found = list.find(p => p.isDefault)
    defaultPrinter.value = found?.name ?? ''
    selectedPrinter.value = found?.name ?? ''
    if (found) {
      ElMessage.info(`默认打印机：${found.name}`)
    }
  } catch (_err) {
    /* 没有打印机就手动选（抽屉兜底） */
  }
}

/** 有默认机直接发，没有弹选机抽屉 */
function onPrint(): void {
  if (!tpl.value) {
    ElMessage.warning('工作区为空')
    return
  }
  if (defaultPrinter.value) return onPrintWith(tpl.value, defaultPrinter.value)
  selectedPrinter.value = ''
  printerVisible.value = true
}

/** 弹选机抽屉 */
function onSelectPrinter(): void {
  selectedPrinter.value = ''
  printerVisible.value = true
}

/** 按指定打印机发起（把打印机名和连接参数交给底层引擎） */
function onPrintWith(tpl: any, name: string): void {
  if (!name) {
    ElMessage.warning('请选择一台打印机')
    return
  }
  if (!tpl.print) {
    ElMessage.error('打印机无法发起')
    return
  }
  saving.value = true
  try {
    tpl.print(
      {},
      { printer: name, smartPrint: false },
      () => {
        ElMessage.info(`已发送到「${name}」`)
        // 打印确实发出后才回写日志（失败时不留下「打过」的假记录）
        void templateApi.print(id.value, { printer: name, copies: 1 }).catch(() => undefined)
      }
    )
  } catch (err) {
    ElMessage.error(errorText(err))
  } finally {
    saving.value = false
  }
}

/** 选机抽屉确认：选哪台就打哪台 */
function onPrinterConfirm(next: string): void {
  printerVisible.value = false
  onPrintWith(tpl.value, next)
}

function onBack(): void {
  void router.replace('/template')
}

/**
 * 把设计描述字符串解析成 hiprint 需要的对象。
 *
 * <p>hiprint 的 {@code template} 只认对象：内部直接读 {@code t.panels}，
 * 传字符串会被当成「没有 panels」的空模板，画布渲染出空壳。</p>
 *
 * @param content 设计描述（JSON 字符串）
 * @returns 解析出的对象 / 数组；不是合法 JSON 返回 null
 */
function parseTemplateObject(content: string): Record<string, unknown> | unknown[] | null {
  try {
    const parsed: unknown = JSON.parse(content)
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown> | unknown[]) : null
  } catch {
    return null
  }
}

/**
 * 画纸上是否有可用于打印的元件。
 *
 * <p>只有画纸（panels）而 {@code printElements} 全空，等于这张模板还没设计过内容；
 * hiprint 仍然会返回一个空壳页面，画布看起来一片空白，必须先给明确空态。</p>
 *
 * @param template 解析后的设计描述
 * @returns 至少一个元件时为 true
 */
function hasPrintElements(template: Record<string, unknown> | unknown[]): boolean {
  const panels = Array.isArray(template) ? template : (template as { panels?: unknown }).panels
  if (!Array.isArray(panels)) return false
  return panels.some((panel) => {
    const elements = (panel as { printElements?: unknown })?.printElements
    return Array.isArray(elements) && elements.length > 0
  })
}

/**
 * 只读画出 print 页面：创建 PrintTemplate 并调用 getHtml 把 print 页面结构追加到预览区，
 * 不进入 design（不拿设计器外壳与拖拽控件）。
 */
function renderPrintPages(content: string): void {
  renderError.value = ''
  if (!content || !content.trim()) {
    renderError.value = '该模板还没有设计内容，请先在设计器里保存一次'
    return
  }
  // 只有 hiprint 产物（JSON 数组 / 对象）能出 print 页面；短信、邮件等纯文本模板直接给明确提示
  const trimmed = content.trim()
  if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) {
    renderError.value = '该模板不是打印模板（内容为短信 / 邮件等文本），无法预览打印版面'
    return
  }
  if (!window.hiprint) {
    renderError.value = '打印引擎未就绪，请刷新页面后重试'
    return
  }
  const templateObject = parseTemplateObject(trimmed)
  if (!templateObject) {
    renderError.value = '该模板的设计内容无法解析，请在设计器里重新保存一次'
    return
  }
  if (!hasPrintElements(templateObject)) {
    renderError.value = '该模板还没有设计内容，请先在设计器里保存一次'
    return
  }
  // 全局初始化一次（避免主题/框架重建时重复）
  if (!hiprintInitialized) {
    try {
      hiprint.init({})
      hiprintInitialized = true
    } catch (_err) { /* hiprint 未注入则不阻塞（打印机用 msg 兜底） */ }
  }
  try {
    const tplInstance = new window.hiprint.PrintTemplate({
      template: templateObject,
      dataMode: 1,
      history: false
    })
    tpl.value = tplInstance
    const $container = $(tplRef.value)
    if (!$container.length) return
    // getHtml 返回的是 print-locked 的页面元素：套用只读样式、补充分页
    const $pages = tplInstance.getHtml?.() as any
    if ($pages && $pages.length) {
      $pages.removeClass('design').addClass('preview')
      $container.append($pages)
    }
  } catch (err) {
    tpl.value = null
    renderError.value = `预览渲染失败：${errorText(err)}`
    ElMessage.error(`预览渲染：${errorText(err)}`)
  }
}

onMounted(async () => {
  if (!id.value) {
    ElMessage.error('未指定模板')
    void router.replace('/template')
    return
  }
  loading.value = true
  try {
    const payload = await templateApi.designer(id.value)
    template.value = payload
    templateName.value = payload.info?.templateInfoName || payload.info?.subtitle || '未命名模板'
    await loadDefaultPrinter()
    // 只读画出打印页面
    renderPrintPages(payload.content)
    // 预览动作留痕：日志页能回看「谁在什么时候看过这张模板」
    void templateApi.log({
      templateId: id.value,
      templateName: templateName.value,
      action: 'preview',
      parameters: {}
    }).catch(() => undefined)
  } catch (err) {
    const text = errorText(err)
    // 消息模板 / 组卷模板不在 template_info 里：说清楚「为什么进不来」，别只丢一句「模板不存在」
    ElMessage.error(
      text.includes('模板不存在')
        ? '该模板不是文档打印模板（消息 / 组卷模板请在各自页面查看），无法预览打印版面'
        : text
    )
    void router.replace('/template')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  tpl.value = null
})
</script>

<style lang="scss" scoped>
.preview {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 12px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 8px;
}

.preview-title {
  flex: 1;
  text-align: center;
  font-weight: 500;
}

.preview-actions {
  display: flex;
  gap: 4px;
}

.preview__body {
  flex: 1;
  overflow: auto;
  background: var(--el-fill-color);
}

.preview__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--el-text-color-secondary);
}

.preview__empty--error {
  color: var(--el-text-color-regular);
}

.preview__canvas {
  /* 只读 print 页面容器：留出纸张间距，多页时逐张往下排 */
  padding: 16px;

  /* hiprint 的 print 页面本身是透明背景（打印用，不该有底色），
     放到灰色底上就看不出纸张边界，预览里补一层白纸 + 投影 */
  :deep(.hiprint-printPaper) {
    margin: 0 auto 16px;
    background: rgb(255 255 255);
    box-shadow: 0 1px 2px rgb(0 0 0 / 8%), 0 4px 16px rgb(0 0 0 / 6%);
  }

  :deep(.hiprint-printPaper:last-child) {
    margin-bottom: 0;
  }
}
</style>
