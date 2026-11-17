<template>
  <!-- 用途能力条：全部用途图标+文字标签成排铺开，当前账号已标记的高亮；点击即发 toggle 事件 -->
  <div class="cap-bar" role="toolbar" aria-label="账号用途能力">
    <button
      v-for="tag in tags"
      :key="tag.key"
      type="button"
      class="cap-item"
      :class="{ 'is-on': isOn(tag) }"
      :title="tag.label"
      :aria-label="tag.label"
      :aria-pressed="isOn(tag)"
      @click="emit('toggle', tag)"
    >
      <ReIcon :name="tag.icon" :size="15" />
      <span class="cap-item__label">{{ tag.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * 账号「用途能力条」。
 *
 * <p>全部用途目录铺成一排（图标 + 文字标签，胶囊形态仿 ReTag），当前账号已标记的
 * 高亮成主色胶囊、未标记灰显可点。点击只向上发 {@code toggle} 事件，保存与刷新由
 * 宿主导（配合 {@code updateAccount} / {@code loadAll}）：本组件不碰 API 层。</p>
 *
 * <p>展示口径与左栏索引一致：{@code activeKeys} 由宿主按
 * {@code capabilityTagsOf(config, kind)} 生成（未手填标记时按种类兜底显示）。</p>
 */
import ReIcon from '@repo/ui/re-icon';
import {
  ACCOUNT_CAPABILITY_TAGS,
  type AccountCapabilityTag,
} from '../../capabilities';

const props = withDefaults(
  defineProps<{
    /** 已标记（高亮）的能力 key 集合 */
    activeKeys?: Set<string>;
    /** 展示的用途目录（默认全量；后续按账号种类出子集时换传） */
    tags?: AccountCapabilityTag[];
  }>(),
  {
    activeKeys: () => new Set<string>(),
    tags: () => ACCOUNT_CAPABILITY_TAGS,
  },
);

const emit = defineEmits<{
  (e: 'toggle', tag: AccountCapabilityTag): void;
}>();

/** 该条目是否处于高亮态 */
function isOn(tag: AccountCapabilityTag): boolean {
  return props.activeKeys.has(tag.key);
}
</script>

<style scoped>
/* 用途能力条：全部用途一排铺开，已标记的成主色胶囊，未标记灰显可点 */
.cap-bar {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 16px 10px;
  border-bottom: 1px solid var(--border-subtle, var(--border-default));
}
.cap-item {
  flex: none;
  height: 28px;
  padding: 0 10px;
  display: inline-grid;
  grid-auto-flow: column;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--bg-fill, transparent);
  color: var(--text-tertiary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition:
    background 0.15s var(--ease-out),
    color 0.15s var(--ease-out),
    border-color 0.15s var(--ease-out);
}
.cap-item:hover {
  color: var(--text-secondary);
  border-color: var(--el-color-primary);
}
.cap-item.is-on {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.cap-item__label {
  white-space: nowrap;
}
</style>