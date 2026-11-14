<template>
  <span
    class="pl"
    :style="{ width: `${size}px`, height: `${size}px`, '--pl-color': brand.color, fontSize: `${Math.round(size * 0.92)}px` }"
    :title="brand.label"
  >
    <!-- 单色标：用遮罩染成品牌色，纯白图形也不会在白底上「消失」 -->
    <span
      v-if="src && brand.tint && available"
      class="pl__mask"
      :style="{
        width: `${size}px`,
        height: `${size}px`,
        maskImage: `url(${src})`,
        '-webkit-mask-image': `url(${src})`,
      }"
    />
    <!-- 自带底色 / 渐变的多色标：保持原色 -->
    <img
      v-else-if="src && available"
      class="pl__img"
      :src="src"
      :alt="brand.label"
      :style="{ width: `${size}px`, height: `${size}px` }"
    />
    <span v-else class="pl__letter">{{ initials }}</span>
  </span>
</template>

<script setup lang="ts">
/**
 * 厂商 logo（无底板、无边框，直接显示图形）。
 *
 * <p>两种画法：单色标（{@code tint}）用 CSS 遮罩染品牌色，深浅主题都清晰；
 * 自带底色或渐变的多色标保留原色。文件缺失或加载失败时退化成品牌色字母，
 * 不留空白。</p>
 */
import { computed, ref, watch } from 'vue';
import { providerOf, providerLogo } from '../../secretProviders';

const props = withDefaults(
  defineProps<{
    /** 厂商落库值（aliyun / tencent / …） */
    provider?: string | null;
    /** 图标边长（px） */
    size?: number;
  }>(),
  { provider: '', size: 16 },
);

/** 品牌定义（中文名 / 品牌色 / logo / 是否单色） */
const brand = computed(() => providerOf(props.provider));

/** 品牌图地址（无图时为空串） */
const src = computed(() => providerLogo(props.provider));

/** 图是否可用（缺文件 / 404 时走字母兜底，避免整块空白） */
const available = ref(true);

watch(
  src,
  (url) => {
    if (!url) {
      available.value = false;
      return;
    }
    const probe = new Image();
    probe.onload = () => {
      available.value = true;
    };
    probe.onerror = () => {
      available.value = false;
    };
    probe.src = url;
  },
  { immediate: true },
);

/** 字母兜底：中文名取首字，英文名取首字母 */
const initials = computed(() => brand.value.label.slice(0, 1).toUpperCase());
</script>

<style scoped>
.pl {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
}

.pl__mask {
  display: block;
  flex: none;
  background: var(--pl-color);
  mask-position: center;
  mask-repeat: no-repeat;
  mask-size: contain;
  -webkit-mask-position: center;
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-size: contain;
}

.pl__img {
  display: block;
  flex: none;
  object-fit: contain;
}

.pl__letter {
  font-weight: 700;
  line-height: 1;
  color: var(--pl-color);
}
</style>
