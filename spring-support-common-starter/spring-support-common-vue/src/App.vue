<template>
  <oss-layout></oss-layout>
</template>

<script>

import {defineComponent} from "vue";
import OssLayout from "@/components/OssLayout.vue";

export default defineComponent({
  components: {OssLayout}
})
</script>
<style scoped>
html,
body {
  height: 100%;
  width: 100%;
  margin: 0;
  padding: 0;
}

oss-layout {
  margin: 0;
  padding: 0;
}
</style>
