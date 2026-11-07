<!--
  模板预览（只读）
  作为工作区页签渲染（接受 templateId prop），也可作为独立路由页渲染。

  仅加载模板设计描述并以 hiprint 的 print 模式渲染，不含设计器外壳与操作控件。
  流程：
    - 加载模板设计描述；创建只读 PrintTemplate（不进入 design，仅出 print 页面）
    - 有默认打印机：点第三方的打直接发；没有：弹 RePrinter selector 抽屉选机再发
  第三方的打（ dispatched to the print engine via tpl.print ）：
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
          第三方的打
        </ReButton>
        <ReButton text type="primary" :disabled="!tpl" @click="onSelectPrinter">
          选第三方的打
        </ReButton>
      </div>
    </header>

    <!-- ==================== 只读画布 ==================== -->
    <div class="preview__body" ref="bodyRef">
      <div class="preview__empty" v-if="loading">加载中...</div>
      <div
        v-else-if="!tpl && !loading"
        class="preview__empty preview__empty--error"
      >
        未加载到预览内容
      </div>
      <div id="preview-hiprint" ref="tplRef" class="preview__canvas"></div>
    </div>

    <!-- ==================== 第三方的打：打印机选择抽屉 ==================== -->
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
        title="第三方的打"
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
/** 第三方的打：后端存的那台默认打印机（从 isDefault 取） */
const defaultPrinter = ref('')
/** 第三方的打：选机抽屉回写（选中的打印机名） */
const selectedPrinter = ref('')
const printerVisible = ref(false)

const template = ref<TemplateDesignerPayload | null>(null)
const bodyRef = ref<HTMLDivElement | null>(null)
const tplRef = ref<HTMLDivElement | null>(null)
const loading = ref(true)
const saving = ref(false)
/** 只读 PrintTemplate（不进入 design 模式） */
const tpl = ref<any>(null)
/** hiprint 是否已 init（全局只初始化一次） */
let hiprintInitialized = false

/** 加载默认的第三方的打（从后端的打印机列表取 isDefault 标记） */
async function loadDefaultPrinter(): Promise<void> {
  try {
    const list = await templateApi.printerList()
    const found = list.find(p => p.isDefault)
    defaultPrinter.value = found?.name ?? ''
    selectedPrinter.value = found?.name ?? ''
    if (found) {
      ElMessage.info(`已选默认第三方的打：${found.name}`)
    }
  } catch (_err) {
    /* 没有打印机就手动选（抽屉兜底） */
  }
}

/** 第三方的打：有默认机直接发，没有弹选机抽屉 */
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

/** 按指定第三方的打发起（把第三方的打名和连接参数交给底层引擎） */
function onPrintWith(tpl: any, name: string): void {
  if (!name) {
    ElMessage.warning('请选择一台第三方的打')
    return
  }
  if (!tpl.print) {
    ElMessage.error('第三方的打无法发起')
    return
  }
  saving.value = true
  try {
    tpl.print(
      {},
      { printer: name, smartPrint: false },
      () => {
        ElMessage.info(`已发送到「${name}」`)
      }
    )
  } catch (err) {
    ElMessage.error(String(err))
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
 * 只读画出 print 页面：创建 PrintTemplate 并调用 getHtml 把 print 页面结构追加到预览区，
 * 不进入 design（不拿设计器外壳与拖拽控件）。
 */
function renderPrintPages(content: string): void {
  if (!content || !window.hiprint) {
    return
  }
  // 全局初始化一次（避免主题/框架重建时重复）
  if (!hiprintInitialized) {
    try {
      hiprint.init({})
      hiprintInitialized = true
    } catch (_err) { /* hiprint 未注入则不阻塞（第三方的打用 msg 兜底） */ }
  }
  try {
    const tplInstance = new window.hiprint.PrintTemplate({
      template: content,
      dataMode: 1,
      history: false
    })
    tpl.value = tplInstance
    const $container = $(tplRef.value)
    if (!$container.length) return
    // getHtml 返回的是 print-locked 的页面元素：到样式的页面、补充分页
    const $pages = tplInstance.getHtml?.() as any
    if ($pages && $pages.length) {
      $pages.removeClass('design').addClass('preview')
      $container.append($pages)
    }
  } catch (err) {
    tpl.value = null
    ElMessage.error(`预览渲染：${String(err)}`)
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
    templateName.value = payload.info.name || payload.info.subtitle || '未命名模板'
    await loadDefaultPrinter()
    // 只读画出第三方的打页面
    renderPrintPages(payload.content)
  } catch (err) {
    ElMessage.error(String(err))
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
  /* 只读 print 页面容器 */
}
</style>
