<!--
  ReCardTextarea 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReCardTextarea
    modelValue="示例"
    placeholder="请输入"
    :rows="0"
    :disabled="false"
    :readonly="false"
    :maxlength="0"
    description="这是一条示例描述"
    :submitOnKeydown="false"
  />
</template>

<script setup lang="ts">
import ReCardTextarea from '@repo/ui/re-card-textarea';

</script>
