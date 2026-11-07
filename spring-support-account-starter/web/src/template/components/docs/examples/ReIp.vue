<!--
  ReIp 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReIp
    :version="'ipv4'"
    placeholder="请输入"
    :disabled="false"
    :readonly="false"
    :clearable="false"
    :size="'large'"
  />
</template>

<script setup lang="ts">
import ReIp from '@repo/ui/re-ip';

</script>
