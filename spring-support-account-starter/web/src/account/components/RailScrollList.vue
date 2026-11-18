<template>
  <div class="rail-scroll-list">
    <div ref="viewport" class="rail-scroll-list__viewport" @scroll.passive="onScroll">
      <slot />
    </div>
    <footer v-if="footText" class="rail-scroll-list__foot">{{ footText }}</footer>
  </div>
</template>

<script setup lang="ts">
/**
 * 左栏滚动分页列表（双栏 / 多栏页面左侧数据列通用）。
 *
 * <p>统一「滚动到接近底部 → 追加下一页」的触底监听与底部提示文案。
 * 首页数据不满一屏时视口不可滚动，组件会自动代为追加，直到可滚动或加载完毕。</p>
 * 数据来源（服务端分页追加或前端切片）由调用方决定，组件不感知数据形状：
 * 调用方把列表内容放进默认插槽，触底时收到 <code>load-more</code> 事件再追加。</p>
 *
 * <p>底部提示按 <code>loading / hasMore / itemCount</code> 统一生成：
 * 加载中 →「加载中…」；还有更多 →「已加载 X（/ Y）条，滚动加载更多」；
 * 全部加载完 → <code>finishedText</code>（默认「已全部加载（N 条）」）；空列表 →「—」。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 是否加载中（首屏或追加）；追加期间不重复触发触底 */
    loading?: boolean
    /** 是否还有更多数据；false 且有数据时显示加载完提示 */
    hasMore?: boolean
    /** 当前已加载数量（用于底部文案） */
    itemCount?: number
    /** 服务端总数；提供时「已加载」文案带分母 */
    total?: number
    /** 触底提前量（像素）：距底部不足该值即触发加载 */
    threshold?: number
    /** 还有更多时的提示语 */
    moreText?: string
    /** 全部加载完的提示语；默认「已全部加载（N 条）」 */
    finishedText?: string
  }>(),
  {
    loading: false,
    hasMore: false,
    itemCount: 0,
    total: undefined,
    threshold: 40,
    moreText: '滚动加载更多',
    finishedText: '',
  },
)

const emit = defineEmits<{ (e: 'loadMore'): void }>()

/** 滚动视口：调用方可通过模板引用拿到它做行定位 / 回到顶部 */
const viewport = ref<HTMLElement | null>(null)

/** 滚动到接近底部时通知调用方追加下一页（passive 监听，不阻塞滚动） */
function onScroll(event: Event): void {
  if (!props.hasMore || props.loading || auto.pending) return
  const el = event.target as HTMLElement
  if (el.scrollHeight - el.scrollTop - el.clientHeight > props.threshold) return
  emit('loadMore')
}

/**
 * 内容不足以滚动时的自动续载。
 *
 * <p>第一页数据不满一屏（或恰好填满）时视口不可滚动，用户永远到不了「触底」，
 * 后续页会一直出不来；这里仿照 Element Plus 无限滚动指令的做法：
 * 只要还有更多且当前不是加载中，就代用户触发一次追加，直到内容可滚动或加载完毕。
 * 通过 <code>auto</code> 状态防止同屏内重复触发与空页空转。</p>
 */
const auto = { lastCount: -1, pending: false }

function maybeAutoLoad(): void {
  if (auto.pending || props.loading || !props.hasMore) return
  const el = viewport.value
  if (!el || el.scrollHeight - el.clientHeight > props.threshold) return
  if (props.itemCount === auto.lastCount) return
  auto.lastCount = props.itemCount
  auto.pending = true
  emit('loadMore')
}

watch(
  () => [props.loading, props.itemCount, props.hasMore] as const,
  ([loading, count]) => {
    if (loading) return
    auto.pending = false
    if (count !== auto.lastCount) void nextTick(maybeAutoLoad)
  },
)

let autoObserver: ResizeObserver | null = null

onMounted(() => {
  autoObserver = new ResizeObserver(() => maybeAutoLoad())
  if (viewport.value) autoObserver.observe(viewport.value)
  void nextTick(maybeAutoLoad)
})

onBeforeUnmount(() => {
  autoObserver?.disconnect()
  autoObserver = null
})

/** 底部提示文案 */
const footText = computed<string>(() => {
  if (props.loading && props.itemCount > 0) return '加载中…'
  if (!props.itemCount) return '—'
  if (props.hasMore) {
    return props.total != null
      ? `已加载 ${props.itemCount} / ${props.total} 条，${props.moreText}`
      : `已加载 ${props.itemCount} 条，${props.moreText}`
  }
  return props.finishedText || `已全部加载（${props.itemCount} 条）`
})

defineExpose({
  /** 滚动视口元素 */
  viewport,
})
</script>

<style scoped>
.rail-scroll-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.rail-scroll-list__viewport {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.rail-scroll-list__foot {
  flex: none;
  padding: 4px 10px;
  font-size: 11px;
  color: var(--text-tertiary);
  border-top: 1px solid var(--border-subtle, var(--border-default));
}
</style>
