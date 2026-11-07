const fs=require('fs');
const t='<template>\n  <ReTable :layout="layout" :layouts="layouts" />\n</template>\n\n<script setup lang="ts">\nimport { ref } from "vue";\nconst layout = ref("table");\nconst layouts = ref(["table"]);\n</script>\n';
fs.writeFileSync('D:/ch/project/vue-support-parent-starter/pages/template/src/test_ref/A.vue',t);
console.log('written');
