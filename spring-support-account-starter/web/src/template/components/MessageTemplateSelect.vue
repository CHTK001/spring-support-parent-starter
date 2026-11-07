<template>
  <div class="msg-template-select">
    <el-select
      v-model="code"
      class="msg-template-select__control"
      :placeholder="loading ? '模板加载中…' : placeholder"
      :clearable="clearable"
      :disabled="disabled || loading"
      :filterable="filterable"
      :allow-create="allowCreate"
      :default-first-option="allowCreate"
      @change="onChange"
    >
      <el-option-group v-for="group in groups" :key="group.key" :label="group.label">
        <el-option
          v-for="item in group.items"
          :key="item.key"
          :label="item.label"
          :value="item.template.templateCode"
        >
          <span class="msg-template-select__name">{{ item.template.templateName }}</span>
          <span class="msg-template-select__code">{{ item.template.templateCode }}</span>
          <span v-if="item.template.templateDefault" class="msg-template-select__tag">默认</span>
        </el-option>
      </el-option-group>
    </el-select>

    <div v-if="loadError" class="msg-template-select__tip is-error">
      模板列表加载失败：{{ loadError }}
      <button type="button" class="msg-template-select__retry" @click="reload">重试</button>
    </div>
    <div v-else-if="!loading && !scoped.length" class="msg-template-select__tip">
      当前{{ kindLabel }}还没有模板：可到「模板管理 → 消息模板」新增，也可直接填写外部模板码。
    </div>
    <div v-else-if="allowCreate" class="msg-template-select__tip">
      可从模板管理中选择，也可直接输入外部模板码（如 SMS_123456789）。
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 消息模板选择器（账号域 / `sys_ai_channel_message_template`）。
 *
 * <p>数据源是模板管理「消息模板」页维护的那一套模板
 * （{@code GET /api/channels/templates}，部署级共享），与系统消息模板
 * （`sys_message_template`，`/v2/message/template/list`）不是同一张表，切勿混用。</p>
 *
 * <p>查询与分组维度：</p>
 * <ul>
 *   <li>类型（{@link kind}）：固定为 SMS / EMAIL 时按该种类过滤；留空则全部，并按种类分组；</li>
 *   <li>分组（{@link groupBy}）：{@code auto} 按种类分组（kind 已固定时改为按「默认 / 其他」分组），
 *       也可强制 {@code kind} / {@code default} / {@code none}；默认模板始终排在同组最前；</li>
 *   <li>启用态（{@link onlyEnabled}）：编辑回显等需要列出停用模板时传 false；</li>
 *   <li>关键字：{@link filterable} 打开后按「名称 · 编码」本地搜索。</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>
 *   &lt;MessageTemplateSelect v-model="form.templateCode" kind="SMS" allow-create @change="onPick" /&gt;
 *   &lt;MessageTemplateSelect v-model="code" @change="onPick" /&gt;
 * </pre>
 *
 * <p>{@code @change} 回传完整模板对象（手填外部码或清空时 template 为 undefined），
 * 供调用方回填标题 / 正文等字段；需要手动刷新时调用 expose 的 {@code reload()}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
import { computed, onMounted, ref, watch } from 'vue'
import { messageTemplateApi, type MessageTemplate } from '../api/messageTemplateApi'

/** 模板种类 */
type TemplateKind = 'SMS' | 'EMAIL'

/** 分组方式：auto / kind / default / none */
type GroupBy = 'auto' | 'kind' | 'default' | 'none'

/** 选项（同组内默认模板置顶） */
interface TemplateOption {
  key: string
  label: string
  template: MessageTemplate
}

/** 分组 */
interface TemplateGroup {
  key: string
  label: string
  items: TemplateOption[]
}

const props = withDefaults(
  defineProps<{
    /** 类型过滤：SMS / EMAIL；留空表示不过滤（按种类分组展示） */
    kind?: TemplateKind | ''
    /** 只列启用中的模板（默认 true；编辑回显停用模板时传 false） */
    onlyEnabled?: boolean
    /** 分组方式，默认 auto */
    groupBy?: GroupBy
    /** 允许手填选项外的模板编码（如运营商模板码），仅 filterable 时生效 */
    allowCreate?: boolean
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
    filterable?: boolean
  }>(),
  {
    kind: '',
    onlyEnabled: true,
    groupBy: 'auto',
    allowCreate: false,
    placeholder: '选择模板，或直接输入模板编码',
    clearable: true,
    disabled: false,
    filterable: true,
  },
)

const emit = defineEmits<{
  /** 选中变化：回传完整模板对象与模板编码（清空 / 手填外部码时 template 为 undefined） */
  (e: 'change', template: MessageTemplate | undefined, code?: string): void
}>()

/** 双向绑定的模板编码 */
const code = defineModel<string | undefined>({ default: '' })

/** 已加载的模板（按后端顺序：种类、默认优先、排序号、更新时间） */
const templates = ref<MessageTemplate[]>([])
const loading = ref(false)
const loadError = ref('')

/** 类型过滤后的模板 */
const scoped = computed(() =>
  templates.value.filter((item) => {
    if (props.onlyEnabled && item.templateEnabled === false) return false
    if (!props.kind) return true
    return normalizeKind(item.templateKind) === props.kind
  }),
)

/** 类型文案（空类型时为「模板」） */
const kindLabel = computed(() => {
  if (props.kind === 'SMS') return '短信模板'
  if (props.kind === 'EMAIL') return '邮件模板'
  return '模板'
})

/** 生效的分组方式：auto 视类型是否已固定而定 */
const effectiveGroupBy = computed<Exclude<GroupBy, 'auto'>>(() => {
  if (props.groupBy !== 'auto') return props.groupBy
  return props.kind ? 'default' : 'kind'
})

/** 选项标签：名称 · 编码（filterable 按该文本匹配） */
function labelOf(template: MessageTemplate): string {
  const name = (template.templateName ?? '').trim()
  const codeText = (template.templateCode ?? '').trim()
  if (!name) return codeText
  if (!codeText) return name
  return `${name} · ${codeText}`
}

/** 组内排序：默认模板优先，其次沿用后端顺序 */
function orderOf(items: MessageTemplate[]): MessageTemplate[] {
  return [...items].sort((a, b) => Number(Boolean(b.templateDefault)) - Number(Boolean(a.templateDefault)))
}

/** 渲染用的分组与选项 */
const groups = computed<TemplateGroup[]>(() => {
  const list = scoped.value
  const mode = effectiveGroupBy.value
  const result: TemplateGroup[] = []

  if (mode === 'none') {
    if (!list.length) return result
    result.push({
      key: 'all',
      label: kindLabel.value,
      items: list.map(toOption),
    })
    return result
  }

  if (mode === 'kind') {
    const buckets: Array<{ kind: TemplateKind; label: string }> = [
      { kind: 'SMS', label: '短信模板' },
      { kind: 'EMAIL', label: '邮件模板' },
    ]
    for (const bucket of buckets) {
      const items = list.filter((item) => normalizeKind(item.templateKind) === bucket.kind)
      if (!items.length) continue
      result.push({
        key: bucket.kind,
        label: bucket.label,
        items: orderOf(items).map(toOption),
      })
    }
    return result
  }

  // mode === 'default'：默认模板 / 其他模板
  const defaults = list.filter((item) => item.templateDefault)
  const others = list.filter((item) => !item.templateDefault)
  if (defaults.length) {
    result.push({ key: 'default', label: '默认模板', items: orderOf(defaults).map(toOption) })
  }
  if (others.length) {
    result.push({
      key: 'other',
      label: defaults.length ? '其他模板' : kindLabel.value,
      items: orderOf(others).map(toOption),
    })
  }
  return result
})

/** 模板转选项 */
function toOption(template: MessageTemplate): TemplateOption {
  return {
    key: `${normalizeKind(template.templateKind)}:${template.templateCode}`,
    label: labelOf(template),
    template,
  }
}

/** 归一化种类（后端可能返回小写） */
function normalizeKind(kind?: string): string {
  return (kind ?? '').trim().toUpperCase()
}

/** 拉取模板列表（kind 固定时由后端过滤，减少传输） */
async function reload(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const res = await messageTemplateApi.list(props.kind || undefined)
    templates.value = Array.isArray(res) ? res : []
  } catch (e) {
    templates.value = []
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

/** 选中变化：回传完整模板对象 */
function onChange(value: string | number | (string | number)[] | null | undefined): void {
  const picked = typeof value === 'string' ? value.trim() : ''
  const template = picked ? templates.value.find((item) => item.templateCode === picked) : undefined
  emit('change', template, picked || undefined)
}

/** 类型固定值切换时重新拉取 */
watch(
  () => props.kind,
  () => void reload(),
)

onMounted(() => void reload())

defineExpose({ reload })
</script>

<style scoped>
.msg-template-select {
  width: 100%;
}
.msg-template-select__control {
  width: 100%;
}
.msg-template-select__name {
  margin-right: 8px;
}
.msg-template-select__code {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--text-tertiary, #94a3b8);
}
.msg-template-select__tag {
  margin-left: 6px;
  font-size: 10px;
  color: var(--el-color-primary, #409eff);
  border: 1px solid color-mix(in srgb, var(--el-color-primary, #409eff) 35%, transparent);
  border-radius: 4px;
  padding: 0 4px;
}
.msg-template-select__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary, #94a3b8);
}
.msg-template-select__tip.is-error {
  color: var(--el-color-danger, #f56c6c);
}
.msg-template-select__retry {
  margin-left: 6px;
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
  text-decoration: underline;
}
</style>
