<template>
  <butterfly
      ref="butterfly"
      :canvasData="graphData"
      :canvasConf="canvasConfig"
      @onLoaded="onLoaded"
  >
  </butterfly>
</template>

<script>
import butterfly from "@/components/butterfly.vue";

export default {
  name: "test2",
  components: {butterfly}
}
</script>

<style scoped>

</style>