<!--
  模板中心 · 提供商轨（左） + 多页签工作台（右）

  布局：
    - 左栏 286px 提供商轨：标题 + 数量、搜索、接入方筛选；
      条目为细线/强调条样式，展示名称、endpoint、AK（脱敏）、支持模板类型、模板数；
      整条右键：同步 / 模板管理 / 编辑配置 / 删除提供商；
      左键单击在右侧打开该提供商的详情页签。
    - 右栏 ReTabs 工作台：提供商详情、模板管理（ReTable）、模板设计器、模板预览四种页签，
      页签右键关闭 / 关闭其他 / 关闭全部。

  说明：本页没有整体顶栏，操作收在左栏头部、详情页签头部与模板管理工具栏；
  页面级导航（打印机管理 / 调用日志）收在左栏底部的图标按钮里，均带悬浮提示。
-->
<template>
  <div class="tpl">
    <!-- ==================== 左栏：提供商轨 ==================== -->
    <aside class="rail">
      <header class="rail__head">
        <div class="rail__title">
          <h1>模板中心</h1>
          <span class="rail__count">{{ providers.length }}</span>
        </div>

        <div class="rail__tools">
          <ReInput
            v-model="keyword"
            size="small"
            clearable
            prefix-icon="Search"
            placeholder="搜索名称 / endpoint"
          />
          <ReButton
            text
            :show-text="false"
            icon="Refresh"
            tip="同步全部提供商模板"
            :icon-size="28"
            :loading="syncingAll"
            :disabled="syncingAll"
            @click="syncAll"
          />
        </div>

        <div class="rail__filters">
          <button
            v-for="item in vendorFilters"
            :key="item.key"
            type="button"
            class="filter"
            :class="{ 'is-on': vendorFilter === item.key }"
            @click="vendorFilter = item.key"
          >
            {{ item.label }}
            <span class="filter__n">{{ item.count }}</span>
          </button>
        </div>
      </header>

      <div class="rail__list">
        <div v-if="loadingProviders && !providers.length" class="rail__state">加载中…</div>
        <div v-else-if="!shownProviders.length" class="rail__state">没有匹配的提供商</div>
        <template v-else>
          <div
            v-for="item in shownProviders"
            :key="item.id"
            class="entry"
            :class="{ 'is-active': isProviderActive(item.id) }"
            role="button"
            tabindex="0"
            @click="openProvider(item)"
            @keydown.enter="openProvider(item)"
            @contextmenu.prevent="openProviderMenu($event, item)"
          >
            <span class="entry__mark" :style="{ '--tpl-tint': vendorTint(item.vendor) }">
              {{ initials(item.name) }}
            </span>
            <span class="entry__main">
              <span class="entry__name-row">
                <span class="entry__name" :title="item.name">{{ item.name }}</span>
                <span class="entry__dot" :class="`is-${item.status}`" :title="statusMeta(item.status).label" />
              </span>
              <span class="entry__endpoint mono" :title="item.endpoint">{{ item.endpoint }}</span>
              <span class="entry__ak mono" :title="maskAk(item.ak ?? '')">AK {{ maskAk(item.ak ?? '') }}</span>
              <span class="entry__chips">
                <span v-for="t in item.templateTypes ?? []" :key="t" class="chip">{{ typeLabel(t) }}</span>
                <span v-if="item.customDesign" class="chip is-custom" title="支持在模板中心自定义设计模板">自定义设计</span>
              </span>
            </span>
            <span class="entry__side">
              <span class="entry__num">{{ templateCountOf(item) }}</span>
              <span class="entry__unit">模板</span>
            </span>
          </div>
        </template>
      </div>

      <footer class="rail__foot">
        <span>右键提供商：同步 · 模板管理</span>
        <span class="rail__actions">
          <ReButton
            text
            :show-text="false"
            icon="Printer"
            tip="打印机管理"
            :icon-size="26"
            @click="openPrinterManage"
          />
          <ReButton
            text
            :show-text="false"
            icon="Document"
            tip="调用日志"
            :icon-size="26"
            @click="openLogs"
          />
        </span>
      </footer>
    </aside>

    <!-- ==================== 右栏：多页签工作台 ==================== -->
    <section class="deck">
      <ReTabs
        v-model="activeTab"
        :tabs="tabs"
        :context-menu="tabContextMenu"
        :closable="true"
        :show-close-all="true"
        empty-text="从左侧选择提供商"
        @close="closeTab"
        @context-command="onTabCommand"
        @close-all="closeAllTabs"
      >
        <template #default="{ tab }">
          <!-- 提供商详情 -->
          <div v-if="isProviderTab(tab.name)" class="work">
            <article v-if="providerOf(tab.name)" class="work__sheet">
              <header class="work__head">
                <span class="work__mark" :style="{ '--tpl-tint': vendorTint(providerOf(tab.name)!.vendor) }">
                  {{ initials(providerOf(tab.name)!.name) }}
                </span>
                <div class="work__title">
                  <h2>{{ providerOf(tab.name)!.name }}</h2>
                  <p class="mono">{{ providerOf(tab.name)!.endpoint }}</p>
                </div>
                <div class="work__acts">
                  <ReButton
                    text
                    :show-text="false"
                    icon="Refresh"
                    tip="同步该提供商的模板"
                    :icon-size="30"
                    :loading="syncingProvider === providerOf(tab.name)!.id"
                    :disabled="syncingProvider === providerOf(tab.name)!.id"
                    @click="syncProvider(providerOf(tab.name)!)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="EditPen"
                    tip="编辑提供商配置"
                    :icon-size="30"
                    @click="openProviderEdit(providerOf(tab.name)!)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="Files"
                    tip="模板管理"
                    :icon-size="30"
                    @click="openProviderTemplates(providerOf(tab.name)!)"
                  />
                </div>
              </header>

              <div class="work__chips">
                <span class="chip">{{ vendorLabel(providerOf(tab.name)!.vendor) }}</span>
                <el-tag :type="statusMeta(providerOf(tab.name)!.status).type" size="small" effect="plain" round>
                  {{ statusMeta(providerOf(tab.name)!.status).label }}
                </el-tag>
                <el-tag
                  :type="providerOf(tab.name)!.customDesign ? 'success' : 'info'"
                  size="small"
                  effect="plain"
                  round
                >
                  {{ providerOf(tab.name)!.customDesign ? '支持自定义模板设计' : '不支持自定义模板设计' }}
                </el-tag>
              </div>

              <dl class="work__meta">
                <div>
                  <dt>Access Key</dt>
                  <dd class="mono">{{ maskAk(providerOf(tab.name)!.ak ?? '') }}</dd>
                </div>
                <div>
                  <dt>Secret Key</dt>
                  <dd class="mono">{{ maskSk(providerOf(tab.name)!.sk ?? '') }}</dd>
                </div>
                <div>
                  <dt>Endpoint</dt>
                  <dd class="mono">{{ providerOf(tab.name)!.endpoint }}</dd>
                </div>
                <div>
                  <dt>支持模板类型</dt>
                  <dd class="chips">
                    <span v-for="t in providerOf(tab.name)!.templateTypes ?? []" :key="t" class="chip">
                      {{ typeLabel(t) }}
                    </span>
                    <span v-if="!(providerOf(tab.name)!.templateTypes ?? []).length">—</span>
                  </dd>
                </div>
                <div>
                  <dt>模板数</dt>
                  <dd>{{ templateCountOf(providerOf(tab.name)!) }} 个</dd>
                </div>
                <div>
                  <dt>最近同步</dt>
                  <dd>{{ formatTime(providerOf(tab.name)!.lastSyncAt) }}</dd>
                </div>
                <div>
                  <dt>负责人</dt>
                  <dd>{{ providerOf(tab.name)!.owner || '—' }}</dd>
                </div>
                <div>
                  <dt>更新时间</dt>
                  <dd>{{ formatTime(providerOf(tab.name)!.updatedAt) }}</dd>
                </div>
              </dl>

              <section v-if="providerOf(tab.name)!.remark" class="work__remark">
                <h3>说明</h3>
                <p>{{ providerOf(tab.name)!.remark }}</p>
              </section>

              <p class="work__note">
                {{
                  providerOf(tab.name)!.customDesign
                    ? '该提供商支持自定义模板设计，模板可在设计器里拖拽或 Vue 沙盒编辑。'
                    : '该提供商不支持自定义模板设计，模板由提供商侧维护，这里只读并可同步、预览与打印。'
                }}
              </p>
            </article>
          </div>

          <!-- 模板管理（ReTable） -->
          <div v-else-if="isTemplatesTab(tab.name)" class="work work--fill">
            <ReTable
              :key="`tpl-table-${providerIdOf(tab.name)}`"
              :ref="el => setTableRef(providerIdOf(tab.name), el)"
              :table-id="`template-provider-${providerIdOf(tab.name)}`"
              :toolbar="false"
              :loader="params => loadProviderTemplates(providerIdOf(tab.name), params)"
              :columns="templateColumns"
              :params="{ providerId: providerIdOf(tab.name) }"
              :pagination="true"
              :layout-table="{ fill: true }"
              row-key="id"
            >
              <template #toolbar>
                <div class="tpl-bar">
                  <span class="tpl-bar__title">
                    {{ providerOf(tab.name)?.name ?? '模板管理' }}
                    <span class="tpl-bar__hint">模板管理</span>
                  </span>
                  <span class="tpl-bar__spacer" />
                  <ReButton
                    text
                    :show-text="false"
                    icon="Refresh"
                    tip="同步该提供商的模板"
                    :icon-size="28"
                    :loading="syncingProvider === providerIdOf(tab.name)"
                    @click="syncProvider(providerOf(tab.name)!)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="Plus"
                    tip="新建模板"
                    :icon-size="28"
                    @click="openCreate(providerOf(tab.name)!)"
                  />
                </div>
              </template>
              <template #name="{ row }">
                <span class="cell-name">
                  <span class="cell-name__dot" :class="`is-${row.status}`" />
                  <span>{{ row.name }}</span>
                </span>
              </template>
              <template #source="{ row }">
                <span class="chip">{{ sourceLabel(row.source) }}</span>
              </template>
              <template #type="{ row }">{{ typeLabel(row.type) }}</template>
              <template #status="{ row }">
                <el-tag :type="statusMeta(row.status).type" size="small" effect="plain" round>
                  {{ statusMeta(row.status).label }}
                </el-tag>
              </template>
              <template #updatedAt="{ row }">{{ formatTime(row.updatedAt) }}</template>
              <template #actions="{ row }">
                <span class="row-acts">
                  <ReButton
                    text
                    :show-text="false"
                    icon="MagicStick"
                    tip="进入设计器"
                    :icon-size="24"
                    :disabled="!canDesign(providerOf(tab.name), row)"
                    @click="openDesigner(row)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="View"
                    tip="预览"
                    :icon-size="24"
                    @click="openPreview(row)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="Printer"
                    tip="打印"
                    :icon-size="24"
                    @click="openPrintDialog(row)"
                  />
                  <ReButton
                    text
                    :show-text="false"
                    icon="Delete"
                    tip="删除模板"
                    :icon-size="24"
                    @click="removeTemplate(row)"
                  />
                </span>
              </template>
              <template #empty>
                <div class="page-empty">
                  <span class="page-empty__icon"><ReIcon name="Files" :size="22" /></span>
                  <b class="page-empty__title">该提供商下还没有模板</b>
                  <span class="page-empty__desc">点击右上角「同步」从提供商拉取，或「新建模板」手工创建。</span>
                </div>
              </template>
            </ReTable>
          </div>

          <!-- 设计器页签：整块工作区交给设计器（左栏切换拖拽 / Vue 沙盒） -->
          <div v-else-if="isDesignTab(tab.name)" class="work work--fill work--plain">
            <TemplateDesigner
              :template-id="tplIdOf(tab.name)"
              :custom-design="providerByTab(tab.name)?.customDesign !== false"
              @back="switchToProvider"
              @preview="openDesignPreview"
              @saved="onDesignerSaved"
            />
          </div>

          <!-- 预览页签 -->
          <div v-else-if="isPreviewTab(tab.name)" class="work work--fill work--plain">
            <TemplatePreviewView :template-id="tplIdOf(tab.name)" />
          </div>
        </template>

        <template #empty>
          <div class="deck-empty">
            <span class="deck-empty__mark">
              <ReIcon name="Connection" :size="22" />
            </span>
            <b>还没有选择提供商</b>
            <span>从左栏选择提供商查看配置，或右键直接进入同步 / 模板管理</span>
          </div>
        </template>
      </ReTabs>
    </section>

    <!-- 提供商右键菜单 -->
    <ReContextMenu ref="ctxMenu" :items="ctxItems" @select="onCtxSelect" />

    <!-- 提供商配置弹窗（新增 / 编辑） -->
    <ReDialog v-model="providerFormVisible" :title="providerFormTitle" width="560px" @confirm="onProviderFormConfirm">
      <el-form ref="providerFormRef" :model="providerForm" label-width="112px">
        <el-form-item label="提供商名称" required>
          <el-input v-model="providerForm.name" placeholder="例如 阿里云短信" maxlength="64" />
        </el-form-item>
        <el-form-item label="接入方" required>
          <ReSelect v-model="providerForm.vendor" :options="vendorOptions" placeholder="选择接入方" />
        </el-form-item>
        <el-form-item label="Access Key">
          <el-input v-model="providerForm.ak" placeholder="AK" maxlength="128" />
        </el-form-item>
        <el-form-item label="Secret Key">
          <el-input v-model="providerForm.sk" type="password" show-password placeholder="SK" maxlength="256" />
        </el-form-item>
        <el-form-item label="Endpoint" required>
          <el-input v-model="providerForm.endpoint" placeholder="例如 dysmsapi.aliyuncs.com" maxlength="200" />
        </el-form-item>
        <el-form-item label="支持模板类型" required>
          <ReSelect v-model="providerForm.templateTypes" multiple :options="typeOptions" placeholder="可多选" />
        </el-form-item>
        <el-form-item label="自定义模板设计">
          <el-switch v-model="providerForm.customDesign" />
          <span class="form-hint">开启后模板可进入设计器（拖拽 / Vue 沙盒）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="providerForm.status">
            <el-radio value="online">已启用</el-radio>
            <el-radio value="offline">已停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="providerForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <ReButton @click="providerFormVisible = false">取消</ReButton>
        <ReButton type="primary" :loading="providerSaving" @click="onProviderFormConfirm">确定</ReButton>
      </template>
    </ReDialog>

    <!-- 新建 / 编辑模板弹窗 -->
    <ReDialog v-model="formVisible" :title="formTitle" width="520px" @confirm="onFormConfirm">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" placeholder="例如 出库单" maxlength="64" />
        </el-form-item>
        <el-form-item label="模板副标题">
          <el-input v-model="form.subtitle" placeholder="例如 仓库出库单据" maxlength="128" />
        </el-form-item>
        <el-form-item label="模板语言" required>
          <el-select v-model="form.language" class="w-full">
            <el-option v-for="item in languageOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板类型" required>
          <el-select v-model="form.type" class="w-full">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <ReButton @click="formVisible = false">取消</ReButton>
        <ReButton type="primary" :loading="saving" @click="onFormConfirm">确定</ReButton>
      </template>
    </ReDialog>

    <!-- 打印参数弹窗：选好打印机和份数后，只有「确定打印」才会发起 -->
    <ReDialog v-model="printDialogVisible" title="打印" width="400px" @confirm="onPrintConfirm">
      <el-form :model="printParams" label-width="80px">
        <el-form-item label="打印机" required>
          <ReSelect v-model="printParams.printer" :options="printerOptions" placeholder="选择打印机" />
        </el-form-item>
        <el-form-item label="份数">
          <el-input-number v-model="printParams.copies" :min="1" :max="99" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <ReButton @click="printDialogVisible = false">取消</ReButton>
        <ReButton type="primary" :loading="printing" @click="onPrintConfirm">确定打印</ReButton>
      </template>
    </ReDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 模板中心页（提供商轨布局）。
 *
 * <p>左栏是提供商档案轨，右栏是 ReTabs 多页签工作台。页签分四类，靠页签名的后缀区分：
 * {@code <providerId>:provider} 提供商详情、{@code <providerId>:templates} 模板管理、
 * {@code <templateId>:design} 设计器、{@code <templateId>:preview} 预览。</p>
 *
 * <p>提供商与模板是两级数据：提供商带 AK / SK / endpoint / 支持的模板类型 / 是否支持自定义设计，
 * 模板归属于提供商并以 {@code providerId} 关联；「同步」「模板管理」都作用在提供商这一级。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ReButton from '@repo/ui/re-button'
import ReDialog from '@repo/ui/re-dialog'
import ReIcon from '@repo/ui/re-icon'
import ReInput from '@repo/ui/re-input'
import ReSelect from '@repo/ui/re-select'
import ReTable from '@repo/ui/re-table'
import ReTabs from '@repo/ui/re-tabs'
import ReContextMenu from '@repo/ui/re-context-menu'
import type { ReContextMenuItem } from '@repo/ui/re-context-menu'
import type { ReTabsContextItem, ReTabsItem } from '@repo/ui/re-tabs'
import type { ReTableColumn, ReTableLoadParams, RePageResult } from '@repo/ui/re-table'

import { templateApi } from '../api/templateApi'
import type {
  ProviderInfo,
  ProviderSyncResult,
  TemplateInfo,
  TemplateStatus,
  LogRecord,
  PrinterConfig
} from '../types/template'
import TemplatePreviewView from '../views/TemplatePreviewView.vue'
import TemplateDesigner from '../views/TemplateDesigner.vue'
import {
  tplLanguageLabels,
  tplTypeLabels,
  tplVendorLabels,
  languageLabel,
  sourceLabel,
  statusMeta,
  typeLabel
} from '../utils/i18n'

const router = useRouter()

// ==================== 提供商列表 ====================
/** 全部提供商 */
const providers = ref<ProviderInfo[]>([])
/** 提供商加载中 */
const loadingProviders = ref(false)
/** 搜索关键词 */
const keyword = ref('')
/** 接入方筛选 */
const vendorFilter = ref('all')

/** 接入方选项 */
const vendorOptions = computed(() =>
  Object.entries(tplVendorLabels).map(([value, label]) => ({ label, value }))
)

/** 接入方筛选条（带数量） */
const vendorFilters = computed(() => [
  { key: 'all', label: '全部', count: providers.value.length },
  ...vendorOptions.value.map(v => ({
    key: v.value,
    label: v.label,
    count: providers.value.filter(p => p.vendor === v.value).length
  }))
])

/** 过滤后的提供商：关键词匹配名称 / endpoint / AK */
const shownProviders = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return providers.value.filter(p => {
    if (vendorFilter.value !== 'all' && p.vendor !== vendorFilter.value) return false
    if (!k) return true
    return (
      (p.name ?? '').toLowerCase().includes(k) ||
      (p.endpoint ?? '').toLowerCase().includes(k) ||
      (p.ak ?? '').toLowerCase().includes(k)
    )
  })
})

/** 加载提供商列表 */
async function loadProviders(): Promise<void> {
  loadingProviders.value = true
  try {
    const list = await templateApi.providerList()
    providers.value = Array.isArray(list) ? list : []
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    loadingProviders.value = false
  }
}

/** 按 ID 取提供商（tab.name 可能带 `:provider` / `:templates` 页签名后缀，先归一化） */
function providerOf(id: string): ProviderInfo | undefined {
  return providers.value.find(p => p.id === providerIdOf(id))
}

/** 提供商下的模板数（后端统计优先，否则按已同步计数） */
function templateCountOf(item: ProviderInfo): number {
  return Number(item.templateCount ?? 0)
}

// ==================== 提供商同步 ====================
/** 全量同步中 */
const syncingAll = ref(false)
/** 正在同步的提供商 ID */
const syncingProvider = ref('')

/**
 * 同步单个提供商的模板。
 *
 * @param item 目标提供商
 */
async function syncProvider(item: ProviderInfo | undefined): Promise<void> {
  if (!item || syncingProvider.value) return
  syncingProvider.value = item.id
  try {
    const result: ProviderSyncResult = await templateApi.syncProvider(item.id)
    const added = Number(result?.added ?? 0)
    const updated = Number(result?.updated ?? 0)
    const failed = Number(result?.failed ?? 0)
    ElMessage.success(`「${item.name}」同步完成：新增 ${added}，更新 ${updated}，失败 ${failed}`)
    await loadProviders()
    // 同步结果也进调用日志，便于回溯
    recordLog(
      { id: item.id, name: item.name, creator: item.owner } as TemplateInfo,
      'sync',
      { added, updated, failed, providerId: item.id, endpoint: item.endpoint }
    )
    tableRefs[`${item.id}:templates`]?.reload?.()
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    syncingProvider.value = ''
  }
}

/** 同步全部提供商 */
async function syncAll(): Promise<void> {
  if (syncingAll.value || !providers.value.length) return
  syncingAll.value = true
  let added = 0
  let updated = 0
  let failed = 0
  for (const item of providers.value) {
    try {
      const r = await templateApi.syncProvider(item.id)
      added += Number(r?.added ?? 0)
      updated += Number(r?.updated ?? 0)
      failed += Number(r?.failed ?? 0)
    } catch {
      failed += 1
    }
  }
  syncingAll.value = false
  ElMessage.success(`同步完成：新增 ${added}，更新 ${updated}，失败 ${failed}`)
  await loadProviders()
  // 已打开的模板管理页签一起刷新
  providers.value.forEach(item => tableRefs[`${item.id}:templates`]?.reload?.())
}

// ==================== 提供商新增 / 编辑 / 删除 ====================
const providerFormVisible = ref(false)
const providerFormTitle = ref('')
const providerSaving = ref(false)
const providerFormRef = ref<Record<string, any> | null>(null)
/** 编辑中的提供商 ID（空=新增） */
const editingProviderId = ref('')
/** 提供商表单 */
const providerForm = ref<Partial<ProviderInfo>>({})

/** 新建提供商表单 */
function openProviderCreate(): void {
  providerFormTitle.value = '新增提供商'
  editingProviderId.value = ''
  providerForm.value = {
    name: '',
    vendor: 'aliyun',
    ak: '',
    sk: '',
    endpoint: '',
    templateTypes: [],
    customDesign: false,
    status: 'online',
    remark: ''
  }
  providerFormVisible.value = true
}

/** 编辑提供商表单 */
function openProviderEdit(item: ProviderInfo): void {
  providerFormTitle.value = '编辑提供商配置'
  editingProviderId.value = item.id
  providerForm.value = { ...item, templateTypes: [...(item.templateTypes ?? [])] }
  providerFormVisible.value = true
}

/** 提供商表单提交 */
async function onProviderFormConfirm(): Promise<void> {
  const form = providerForm.value
  if (!form.name?.trim()) {
    ElMessage.warning('请输入提供商名称')
    return
  }
  if (!form.endpoint?.trim()) {
    ElMessage.warning('请输入 endpoint')
    return
  }
  if (!(form.templateTypes ?? []).length) {
    ElMessage.warning('请至少选择一个支持的模板类型')
    return
  }
  providerSaving.value = true
  try {
    if (editingProviderId.value) {
      const updated = await templateApi.updateProvider(editingProviderId.value, form)
      const index = providers.value.findIndex(p => p.id === editingProviderId.value)
      if (index >= 0) providers.value[index] = { ...providers.value[index], ...(updated ?? form) }
      // 详情页签标题跟随改名
      const tab = tabs.value.find(t => t.name === `${editingProviderId.value}:provider`)
      if (tab && form.name) tab.label = form.name
      ElMessage.success('已保存提供商配置')
    } else {
      await templateApi.addProvider(form)
      ElMessage.success('已新增提供商')
      await loadProviders()
    }
    providerFormVisible.value = false
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    providerSaving.value = false
  }
}

/** 删除提供商：连同该提供商下的页签一起关闭 */
async function removeProvider(item: ProviderInfo): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除提供商「${item.name}」？其下的 ${templateCountOf(item)} 个模板将不再显示。`,
      '删除提供商',
      { type: 'warning' }
    )
    await templateApi.removeProvider(item.id)
    ElMessage.success('已删除提供商')
    tabs.value = tabs.value.filter(t => t.name !== `${item.id}:provider` && t.name !== `${item.id}:templates`)
    if (!tabs.value.some(t => t.name === activeTab.value)) activeTab.value = tabs.value[0]?.name ?? ''
    await loadProviders()
  } catch (err) {
    if (String(err) !== 'Error: cancel') ElMessage.error(String(err))
  }
}

// ==================== 右键菜单 ====================
const ctxMenu = ref<InstanceType<typeof ReContextMenu> | null>(null)
/** 当前右键的提供商 */
const ctxProvider = ref<ProviderInfo | null>(null)

/** 菜单项：同步 / 模板管理 / 编辑配置 / 删除 */
const ctxItems = computed<ReContextMenuItem[]>(() => {
  const p = ctxProvider.value
  if (!p) return []
  return [
    { label: '同步模板', command: 'sync', icon: 'Refresh', disabled: p.status !== 'online' },
    { label: '模板管理', command: 'templates', icon: 'Files' },
    { label: '编辑配置', command: 'config', icon: 'EditPen' },
    { separator: true },
    { label: '删除提供商', command: 'remove', icon: 'Delete', danger: true }
  ]
})

/** 在鼠标位置弹出提供商菜单 */
function openProviderMenu(event: MouseEvent, item: ProviderInfo): void {
  ctxProvider.value = item
  ctxMenu.value?.open(event.clientX, event.clientY)
}

/** 菜单命令分发 */
function onCtxSelect(command: string): void {
  const item = ctxProvider.value
  if (!item) return
  switch (command) {
    case 'sync':
      void syncProvider(item)
      break
    case 'templates':
      openProviderTemplates(item)
      break
    case 'config':
      openProviderEdit(item)
      break
    case 'remove':
      void removeProvider(item)
      break
  }
}

// ==================== 右侧工作台 ====================
/** 当前激活的页签 name */
const activeTab = ref('')
/** 页签列表 */
const tabs = ref<ReTabsItem[]>([])
/** 页签右键菜单 */
const tabContextMenu: ReTabsContextItem[] = [
  { key: 'close', label: '关闭' },
  { key: 'close-other', label: '关闭其他' },
  { key: 'close-all', label: '关闭全部' }
]
/** 模板管理页签里的 ReTable 实例（按页签名索引，供同步后刷新） */
const tableRefs = reactive<Record<string, { reload?: () => void } | null>>({})

/** 登记模板表格实例 */
function setTableRef(providerId: string, el: unknown): void {
  tableRefs[`${providerId}:templates`] = (el ?? null) as { reload?: () => void } | null
}

/** 页签名判断：`<providerId>:provider` */
function isProviderTab(name: string): boolean { return name.endsWith(':provider') }
/** 页签名判断：`<providerId>:templates` */
function isTemplatesTab(name: string): boolean { return name.endsWith(':templates') }
/** 页签名判断：`<templateId>:design` */
function isDesignTab(name: string): boolean { return name.endsWith(':design') }
/** 页签名判断：`<templateId>:preview` */
function isPreviewTab(name: string): boolean { return name.endsWith(':preview') }

/** 页签名里的提供商 ID */
function providerIdOf(name: string): string { return name.replace(/:provider$/, '').replace(/:templates$/, '') }
/** 页签名里的模板 ID */
function tplIdOf(name: string): string { return name.replace(/:design$/, '').replace(/:preview$/, '') }

/** 左栏高亮：当前页签所属的提供商 */
function isProviderActive(providerId: string): boolean {
  return activeTab.value === `${providerId}:provider` || activeTab.value === `${providerId}:templates`
}

/** 打开（或切换到）提供商详情页签 */
function openProvider(item: ProviderInfo): void {
  const name = `${item.id}:provider`
  const existing = tabs.value.find(t => t.name === name)
  if (existing) {
    existing.label = item.name
    activeTab.value = name
    return
  }
  tabs.value.push({ name, label: item.name, hint: '提供商', icon: 'Connection', status: item.status, closable: true })
  activeTab.value = name
}

/** 打开（或切换到）模板管理页签 */
function openProviderTemplates(item: ProviderInfo): void {
  if (!item) return
  const name = `${item.id}:templates`
  const existing = tabs.value.find(t => t.name === name)
  if (existing) {
    existing.label = item.name
    activeTab.value = name
    return
  }
  tabs.value.push({ name, label: item.name, hint: '模板管理', icon: 'Files', status: item.status, closable: true })
  activeTab.value = name
}

/** 关闭页签 */
function closeTab(name: string): void {
  tabs.value = tabs.value.filter(t => t.name !== name)
  delete tableRefs[name]
  if (activeTab.value === name) activeTab.value = tabs.value[tabs.value.length - 1]?.name ?? ''
}

/** 关闭全部页签 */
function closeAllTabs(): void {
  tabs.value = []
  activeTab.value = ''
  Object.keys(tableRefs).forEach(key => delete tableRefs[key])
}

/** 页签右键命令 */
function onTabCommand(payload: { key: string; tab: ReTabsItem }): void {
  const tab = payload.tab
  if (!tab) return
  switch (payload.key) {
    case 'close':
      closeTab(tab.name)
      break
    case 'close-other':
      tabs.value = [tab]
      activeTab.value = tab.name
      Object.keys(tableRefs).forEach(key => {
        if (key !== tab.name) delete tableRefs[key]
      })
      break
    case 'close-all':
      closeAllTabs()
      break
  }
}

// ==================== 模板管理数据 ====================
/** 模板表格列 */
const templateColumns: ReTableColumn[] = [
  { prop: 'name', label: '模板名称', minWidth: 200, slot: 'name', showOverflowTooltip: true },
  { prop: 'sourceId', label: '编码', width: 170, showOverflowTooltip: true },
  { prop: 'source', label: '来源', width: 100, slot: 'source', align: 'center' },
  { prop: 'type', label: '类型', width: 96, slot: 'type', align: 'center' },
  { prop: 'status', label: '状态', width: 100, slot: 'status', align: 'center' },
  { prop: 'updatedAt', label: '更新时间', width: 150, slot: 'updatedAt' },
  { prop: 'actions', label: '操作', width: 156, slot: 'actions', align: 'center', fixed: 'right' }
]

/**
 * 模板管理表格数据源：按提供商分页取模板。
 *
 * @param providerId 提供商 ID
 * @param params ReTable 传入的分页与查询参数
 */
async function loadProviderTemplates(providerId: string, params: ReTableLoadParams): Promise<RePageResult> {
  try {
    const res = await templateApi.page({
      providerId,
      page: params.page,
      size: params.pageSize
    })
    return {
      total: Number(res?.total ?? 0),
      records: (res?.records ?? []) as unknown as Record<string, unknown>[]
    }
  } catch (err) {
    ElMessage.error(String(err))
    return { total: 0, records: [] }
  }
}

/** 是否允许该模板进入设计器：模板来源为绘制，且提供商支持自定义设计 */
function canDesign(provider: ProviderInfo | undefined, row: Record<string, any>): boolean {
  return provider?.customDesign !== false && row?.source === 'hiprint'
}

// ==================== 新建 / 编辑模板 ====================
const formVisible = ref(false)
const formTitle = ref('')
const editingId = ref('')
const formRef = ref<Record<string, any> | null>(null)
const form = ref<Partial<TemplateInfo>>({})
const saving = ref(false)
/** 新建模板归属的提供商 */
const formProvider = ref<ProviderInfo | null>(null)

/** 新建模板（归属当前提供商） */
function openCreate(provider: ProviderInfo | undefined): void {
  if (!provider) return
  formProvider.value = provider
  formTitle.value = `新建模板 · ${provider.name}`
  editingId.value = ''
  form.value = {
    language: 'html',
    type: (provider.templateTypes?.[0] as TemplateInfo['type']) ?? 'document',
    status: 'draft'
  }
  formVisible.value = true
}

/** 编辑模板信息 */
function openEdit(row: TemplateInfo): void {
  formTitle.value = '编辑模板'
  editingId.value = row.id
  form.value = { ...row }
  formVisible.value = true
}

/** 新建 / 编辑模板提交 */
async function onFormConfirm(): Promise<void> {
  if (!form.value.name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: editingId.value,
      name: form.value.name,
      subtitle: form.value.subtitle,
      language: form.value.language,
      type: form.value.type,
      status: (form.value.status as TemplateStatus) ?? 'draft',
      remark: form.value.remark,
      // 编辑时保留原来源；新建走「绘制」（hiprint），后端新增时也会兜底成 hiprint
      source: form.value.source ?? 'hiprint',
      providerId: form.value.providerId ?? formProvider.value?.id
    }
    if (editingId.value) {
      await templateApi.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await templateApi.add(payload)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    if (form.value.providerId) tableRefs[`${form.value.providerId}:templates`]?.reload?.()
    await loadProviders()
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    saving.value = false
  }
}

/** 删除模板 */
async function removeTemplate(row: Record<string, any>): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」？`, '删除', { type: 'warning' })
    await templateApi.remove(String(row.id))
    ElMessage.success('删除成功')
    const providerId = String(row.providerId ?? '')
    if (providerId) tableRefs[`${providerId}:templates`]?.reload?.()
    await loadProviders()
  } catch (err) {
    if (String(err) !== 'Error: cancel') ElMessage.error(String(err))
  }
}

// ==================== 设计器 / 预览页签 ====================
/** 该模板归属的提供商 ID（模板上带了就用，否则退回当前激活页签的提供商） */
function providerIdForTemplate(row: TemplateInfo): string {
  if (row.providerId) return String(row.providerId)
  const name = activeTab.value
  if (isTemplatesTab(name) || isProviderTab(name)) return providerIdOf(name)
  return ''
}

/** 预览页签 */
function addPreviewTab(row: TemplateInfo): void {
  const id = String(row.id)
  const name = `${id}:preview`
  const providerId = providerIdForTemplate(row)
  const existing = tabs.value.find(t => t.name === name)
  if (existing) {
    activeTab.value = name
    return
  }
  tabs.value.push({
    name,
    label: row.name,
    hint: '预览',
    icon: 'View',
    closable: true,
    // 记住归属提供商，设计器 / 预览面板据此反查 customDesign
    badge: null
  })
  tabProviders[name] = providerId
  activeTab.value = name
}

/** 设计器页签 */
function addDesignTab(row: TemplateInfo): void {
  const id = String(row.id)
  const name = `${id}:design`
  const providerId = providerIdForTemplate(row)
  const existing = tabs.value.find(t => t.name === name)
  if (existing) {
    activeTab.value = name
    return
  }
  tabs.value.push({ name, label: row.name, hint: '设计', icon: 'MagicStick', closable: true })
  tabProviders[name] = providerId
  activeTab.value = name
}

/** 设计器 / 预览页签 -> 提供商 ID（页签名里没有提供商，单独记一份） */
const tabProviders = reactive<Record<string, string>>({})

/**
 * 反查页签对应的提供商：设计 / 预览页签走 tabProviders，
 * 提供商 / 模板管理页签直接由页签名解析。
 */
function providerByTab(tabName: string): ProviderInfo | undefined {
  const pid = tabProviders[tabName] ?? providerIdOf(tabName)
  return providers.value.find(p => p.id === pid)
}

/** 设计器「返回」：回到该模板所属提供商的模板管理页签 */
function switchToProvider(): void {
  const pid = tabProviders[activeTab.value] ?? ''
  const item = providers.value.find(p => p.id === pid)
  if (item) openProviderTemplates(item)
}

/** 从设计器页签打开预览页签 */
function openDesignPreview(): void {
  const tab = tabs.value.find(t => t.name === activeTab.value)
  if (!tab || !isDesignTab(tab.name)) return
  addPreviewTab({ id: tplIdOf(tab.name), name: tab.label ?? '', providerId: tabProviders[tab.name] } as TemplateInfo)
}

/** 设计器保存后刷新模板列表与提供商统计 */
function onDesignerSaved(): void {
  const pid = tabProviders[activeTab.value] ?? ''
  if (pid) tableRefs[`${pid}:templates`]?.reload?.()
  void loadProviders()
}

/** 预览模板 */
function openPreview(row: TemplateInfo | Record<string, any>): void {
  recordLog(row as TemplateInfo, 'preview', {})
  addPreviewTab(row as TemplateInfo)
}

/** 进入设计器（仅绘制来源且提供商支持自定义设计） */
function openDesigner(row: TemplateInfo | Record<string, any>): void {
  recordLog(row as TemplateInfo, 'design', {})
  addDesignTab(row as TemplateInfo)
}

// ==================== 打印 ====================
/** 当前准备打印的模板 */
const printRow = ref<TemplateInfo | null>(null)
const printDialogVisible = ref(false)
const printing = ref(false)
const printers = ref<PrinterConfig[]>([])
const printParams = reactive<Record<string, any>>({ printer: '', copies: 1 })
const printerOptions = computed(() => printers.value.map(p => ({ label: p.name, value: p.name })))

/** 加载打印机列表 */
async function loadPrinters(): Promise<void> {
  try {
    printers.value = await templateApi.printerList()
  } catch {
    /* 没有打印机就手动选，不影响其他操作 */
  }
}

/** 打开打印参数弹窗 */
async function openPrintDialog(row: TemplateInfo | Record<string, any>): Promise<void> {
  printRow.value = row as TemplateInfo
  if (printers.value.length === 0) await loadPrinters()
  printParams.printer = printers.value.find(p => p.isDefault)?.name ?? ''
  printParams.copies = 1
  printDialogVisible.value = true
}

/** 确认打印：用户点击确定后才请求 */
async function onPrintConfirm(): Promise<void> {
  const row = printRow.value
  if (!row) {
    ElMessage.warning('未指定模板')
    return
  }
  if (!printParams.printer) {
    ElMessage.warning('请选择打印机')
    return
  }
  printing.value = true
  try {
    await templateApi.print(String(row.id), { printer: printParams.printer, copies: printParams.copies })
    recordLog(row, 'print', { printer: printParams.printer, copies: printParams.copies })
    printDialogVisible.value = false
    ElMessage.success(`已发送到「${printParams.printer}」`)
  } catch (err) {
    ElMessage.error(String(err))
  } finally {
    printing.value = false
  }
}

/**
 * 调用日志：预览 / 设计 / 打印 / 同步 归集到日志页。
 *
 * <p>同步是提供商级操作，没有模板可挂，这里借提供商自身作为 {@code templateId} / {@code templateName}，
 * 并在参数里带上 providerId 便于过滤。</p>
 */
function recordLog(
  row: TemplateInfo,
  action: LogRecord['action'],
  parameters: Record<string, any>
): void {
  const record: LogRecord = {
    id: String(Date.now()),
    templateId: String(row.id),
    templateName: row.name,
    action,
    parameters,
    time: new Date().toISOString(),
    ip: '127.0.0.1',
    operator: row.creator
  }
  templateApi.log(record).catch(() => {
    /* 日志只负责记录，失败不影响主操作 */
  })
}

// ==================== 页面入口 ====================
function openPrinterManage(): void {
  router.push({ name: 'TemplatePrinter' })
}

function openLogs(): void {
  router.push({ name: 'TemplateLogs' })
}

// ==================== 字典 / 展示 ====================
/** 语言下拉选项 */
const languageOptions = computed(() =>
  Object.entries(tplLanguageLabels).map(([value, label]) => ({ label, value }))
)
/** 类型下拉选项 */
const typeOptions = computed(() =>
  Object.entries(tplTypeLabels).map(([value, label]) => ({ label, value }))
)

/** 接入方显示文本 */
function vendorLabel(vendor: string): string {
  return tplVendorLabels[vendor] ?? vendor
}

/** 接入方强调色 */
function vendorTint(vendor: string): string {
  if (vendor === 'aliyun') return 'var(--el-color-warning)'
  if (vendor === 'tencent') return 'var(--el-color-primary)'
  return 'var(--el-color-success)'
}

/** AK 脱敏：保留前 6 位与后 4 位 */
function maskAk(ak: string): string {
  if (!ak) return '—'
  if (ak.length <= 10) return ak
  return `${ak.slice(0, 6)}••••${ak.slice(-4)}`
}

/** SK 脱敏：只露后 4 位 */
function maskSk(sk: string): string {
  if (!sk) return '—'
  return `••••••••${sk.slice(-4)}`
}

/** 名称首字：作为提供商轨的色块标记 */
function initials(name: string): string {
  return (name || '?').trim().slice(0, 1)
}

/**
 * 时间格式化：2026-10-31 14:22。
 *
 * @param value 后端时间字段
 */
function formatTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// ==================== 初始化 ====================
onMounted(async () => {
  await loadProviders()
})
</script>

<style lang="scss" scoped>
.tpl {
  display: flex;
  gap: 10px;
  height: 100%;
  min-height: 0;
  color: var(--el-text-color-primary);
}

/* ==================== 左栏提供商轨 ==================== */
.rail {
  display: flex;
  flex: 0 0 286px;
  flex-direction: column;
  width: 286px;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.rail__head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 12px 9px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.rail__title {
  display: flex;
  align-items: baseline;
  gap: 8px;

  h1 {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
    letter-spacing: 0.01em;
  }
}

.rail__count {
  padding: 0 7px;
  font-size: 11px;
  font-weight: 600;
  line-height: 17px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.rail__tools {
  display: flex;
  align-items: center;
  gap: 2px;

  :deep(.el-input) {
    flex: 1;
    min-width: 0;
  }
}

.rail__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  font-size: 11px;
  line-height: 18px;
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

.rail__list {
  flex: 1;
  min-height: 0;
  padding: 6px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.rail__state {
  padding: 26px 0;
  font-size: 12px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.rail__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 3px 8px 3px 12px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter, var(--el-fill-color-light));
  border-top: 1px solid var(--el-border-color-lighter);
}

.rail__actions {
  display: flex;
  align-items: center;
  gap: 0;
}

/* ---------- 提供商条目 ---------- */
.entry {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 8px 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  outline: none;

  & + & {
    margin-top: 1px;
  }

  &::before {
    content: '';
    position: absolute;
    top: 10px;
    bottom: 10px;
    left: 0;
    width: 2px;
    background: transparent;
    border-radius: 2px;
    transition: background 0.15s ease;
  }

  &:hover {
    background: var(--el-fill-color-light);
  }

  &:focus-visible {
    box-shadow: inset 0 0 0 2px var(--el-color-primary-light-7);
  }

  &.is-active {
    background: var(--el-color-primary-light-9);

    &::before {
      background: var(--el-color-primary);
    }

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
  font-size: 13px;
  font-weight: 650;
  color: var(--tpl-tint);
  background: color-mix(in srgb, var(--tpl-tint) 10%, transparent);
  border-radius: 8px;
}

.entry__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.entry__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.entry__name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color 0.15s ease;
}

.entry__endpoint,
.entry__ak {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.entry__endpoint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.entry__ak {
  font-size: 10.5px;
  color: var(--el-text-color-placeholder);
}

.entry__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 3px;
}

.entry__dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-online {
    background: var(--el-color-success);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--el-color-success) 16%, transparent);
  }

  &.is-offline {
    background: var(--el-color-info);
  }
}

.entry__side {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  padding-top: 2px;
}

.entry__num {
  font-size: 13px;
  font-weight: 650;
  line-height: 1.2;
}

.entry__unit {
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.chip {
  padding: 0 6px;
  font-size: 10.5px;
  line-height: 17px;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  white-space: nowrap;
}

.chip.is-custom {
  color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 10%, var(--el-bg-color));
  border-color: color-mix(in srgb, var(--el-color-primary) 35%, transparent);
  font-weight: 600;
}

/* ==================== 右栏工作台 ==================== */
.deck {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.deck-empty {
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

.deck-empty__mark {
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

.work {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: var(--el-bg-color-page);
  padding: 18px 20px 24px;
}

/* 模板管理 / 设计器 / 预览：页签内容自己占满，不再加内边距 */
.work--fill {
  padding: 0;
  gap: 0;
  overflow: hidden;
}

/* 设计器 / 预览：白底，与页面其余工作区区分 */
.work--plain {
  background: var(--el-bg-color);
}

.work__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.work__mark {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 42px;
  height: 42px;
  font-size: 17px;
  font-weight: 700;
  color: var(--tpl-tint);
  background: color-mix(in srgb, var(--tpl-tint) 12%, var(--el-bg-color));
  border-radius: 11px;
}

.work__title {
  flex: 1;
  min-width: 0;

  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 650;
    letter-spacing: 0.01em;
  }

  p {
    margin: 3px 0 0;
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.work__acts {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
}

.work__chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.work__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1px;
  margin: 0;
  overflow: hidden;
  background: var(--el-border-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;

  > div {
    padding: 10px 12px;
    background: var(--el-bg-color);
  }

  dt {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 3px 0 0;
    font-size: 13px;
    font-weight: 550;
    word-break: break-all;
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.work__remark {
  padding: 12px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;

  h3 {
    margin: 0 0 6px;
    font-size: 11px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-regular);
    white-space: pre-wrap;
  }
}

.work__note {
  margin: auto 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

/* ---------- 模板管理工具栏（ReTable 的 #toolbar 插槽） ---------- */
.tpl-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 6px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.tpl-bar__title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding-left: 4px;
  font-size: 13px;
  font-weight: 600;
}

.tpl-bar__hint {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.tpl-bar__spacer {
  flex: 1;
}

.cell-name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cell-name__dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-online {
    background: var(--el-color-success);
  }

  &.is-offline {
    background: var(--el-color-info);
  }

  &.is-draft {
    background: var(--el-color-warning);
  }
}

.row-acts {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

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

.form-hint {
  margin-left: 8px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.mono {
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
}

@media (max-width: 900px) {
  .rail {
    flex-basis: 240px;
    width: 240px;
  }
}
</style>
