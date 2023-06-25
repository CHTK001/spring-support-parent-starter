<template>
  <div class="drag-node">
    <div class="policy-base-node" :id="itemData.value"><span
        class="icon-box icon-background-color"><i class="iconfont icon-rds"></i></span><span
        class="text-box">{{ itemData.label }}</span></div>
  </div>
</template>

<script>
import {markRaw} from 'vue';
import {ElInput} from 'element-plus';
// import { toRefs, toRef, ref } from 'vue';

export default {
  name: "drag-node",
  components: {
    ElInput: markRaw(ElInput),
  },
  props: {
    itemData: {
      type: Object,
    },
    canvasNode: {
      type: Object
    }
  },
  methods: {},
  // setup(props) {
  //   const input = ref('ssd')
  //   debugger;
  //   console.log({
  //     userData: toRefs(props.itemData.userData),
  //   });
  //   return {
  //     userData: toRefs(props.itemData.userData),
  //     input,
  //   }
  // }
};
</script>

<style scoped>
.drag-node {
  width: 200px;
  box-shadow: 0 2px 3px 0 rgba(0, 112, 204, 0.06);;
  background-color: #fff;
  border-radius: 0 0 5px 5px;
  /* overflow: hidden; */
}

.drag-header {
  padding: 5px;
  border-radius: 5px 5px 0 0;
  color: #FFF;
  background-color: #F66902;
  border: 1px solid #aaa;
}

.drag-content {
  color: #222;
  padding: 10px;
  border: 1px solid #D9D9D9;
  border-top: none;
  border-radius: 0 0 5px 5px;
}

.drag-history {
  padding: 5px 0;
  font-size: 12px;
  border-top: 1px solid #eee;
  margin-top: 10px;
}

</style>
