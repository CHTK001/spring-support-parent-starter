<!--
  ReInputNumber 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReInputNumber
    :layout="'default'"
    :min="0"
    :max="0"
    :step="0"
    :stepStrictly="false"
    :precision="0"
    placeholder="请输入"
    :disabled="false"
    :readonly="false"
    :controls="false"
    :controlsPosition="'right'"
    :size="'large'"
    prefix="¥"
    suffix="元"
  />
</template>

<script setup lang="ts">
import ReInputNumber from '@repo/ui/re-input-number';

</script>
