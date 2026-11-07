<!--
  打印机管理页（系统菜单 · 系统管理 · 打印机管理）

  这里不重复造车：打印机的接入 / 设默认 / 添加 / 编辑 / 删除 / 发现，全部来自
  独立组件 `RePrinter`（page 模式），页面只提供页面级的外壳（标题 + 工日条）。

  路由：/template/printer （visible = 1，侧边栏可见）
  @since 4.0.0.60
  @author CH
-->
<template>
  <div class="printer">
    <!-- ==================== 打印机管理（页面外壳） ==================== -->
    <RePrinter ref="printerRef" mode="page" title="打印机管理" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import RePrinter from '../components/RePrinter.vue'

/** 页面外壳：RePrinter（page 模式）是完整本体，ref 保留以备后续的刷新按钮使用 */
const printerRef = ref<InstanceType<typeof RePrinter> | null>(null)
</script>

<style lang="scss" scoped>
.printer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
  overflow: hidden;
}
</style>
