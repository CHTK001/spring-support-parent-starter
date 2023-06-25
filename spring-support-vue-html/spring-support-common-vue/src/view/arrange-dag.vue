<template>
  <el-container style="height: 100%">
    <el-aside width="200px">
      <div v-for="item in data.options" draggable="true" class="node-container" @dragstart="(e)=>{dragstart(e, item)}"
           :ref="item">
        <dragNode :item-data="item"/>
      </div>
    </el-aside>
    <el-divider style="height: 100%" direction="vertical"/>
    <el-main>
      <div @dragover.prevent="dragover" @drop.prevent="addNode">
        <butterfly
            id="butterfly"
            :canvas-data="data.dagData"
            :canvas-conf="canvasConfig"
            @onCreateEdge="logCreateEdge"
            @onChangeEdges="logChangeEdges"
            @onDeleteEdge="logDeleteEdge"
            @onOtherEvent="logOtherEvent"
            @onLoaded="finishLoaded"
            @dblclick="lableEmit"
            key="base"
        ></butterfly>
      </div>
    </el-main>
  </el-container>
</template>

<script>
import mockData from "@/page/base/base-mockData.js";
import config from "@/config/common"
import '@/style/easy.css'
import Butterfly from "@/components/butterfly.vue";
import _ from "lodash";
import emergencyMockData from "@/page/emergency/emergency-mockData";
import dragNode from "@/page/drag/node/drag-node.vue";
import request from "axios";
import Node from "@/butterfly/coms/node";
import $ from "jquery"

const host = config.host;
export default {
  name: "arrange-dag",
  components: {Butterfly, dragNode},
  data() {
    return {
      configId: undefined,
      data: {
        options: [],
        dagData: [],
      },
      canvansRef: {},
      butterflyVue: {},
      nodeIndex: 0,
      groupIndex: 0,
      update: 0,
      canvasConfig: {
        disLinkable: true, // 可删除连线
        linkable: true,    // 可连线
        draggable: true,   // 可拖动
        zoomable: true,    // 可放大
        moveable: true,    // 可平移
        theme: {
          edge: {
            shapeType: 'Flow',
            defaultAnimate: !0,//默认开启线条动画
            isExpandWidth: !0,//默认开启线条动画
          }
        }
      },
      addEdgeEnable: false
    }
  },
  methods: {
    initial: function () {
      request.get(host + "/arrange/option").then(({data}) => {
        if (data.code === '00000') {
          this.data.options = data.data;
        }
      });
    },
    doSave: function (arrangeId) {
      alert(arrangeId)
    },
    dragover(e) {
      e.preventDefault();
    },
    dragstart(e, data) {
      e.dataTransfer.setData('data', JSON.stringify(data));
    },
    addNode(e) {
      const endpointLeft = {
        id: 'left',
        orientation: [-1, 0],
        pos: [0, 0.5]
      };
      const endpointRight = {
        id: 'right',
        orientation: [1, 0],
        pos: [0, 0.5]
      };
      let {clientX, clientY} = e;
      let coordinates = this.canvansRef.terminal2canvas([clientX, clientY]);
      let data = JSON.parse(e.dataTransfer.getData('data'));
      this.data.dagData.nodes.push({
        id: data.value,
        left: coordinates[0],
        top: coordinates[1],
        render: dragNode,
        userData: data,
        label: data.label,
        menus: !0,
        closeIcon: true,
        className: 'icon-background-color',
        iconType: "icon-bofang",
        Class: Node,
        endpoints: [
          endpointLeft,
          endpointRight
        ]
      });
    },
    lableEmit(text) {
      console.log(text);
    },
    addGroup() {
      this.mockData.groups.push({
        id: `add${this.groupIndex}`,
        left: 10 + this.groupIndex * 290,
        top: 300,
      });
      this.groupIndex++;
    },
    updateGroup() {
      this.mockData.groups[2].userData.name = `updateName${this.update}`
      this.update++;
    },
    redraw() {
      this.mockData = {nodes: [], edges: [], groups: []};
      this.$nextTick(() => {
        this.mockData = _.cloneDeep(emergencyMockData);
      })
    },
    logData() {
      console.log(mockData);
      console.log(this.canvansRef.getDataMap());
    },
    addEdge() {
      this.mockData.edges.push({
        id: '1.right-4.left',
        sourceNode: '1',
        targetNode: '4',
        source: 'right',
        target: 'left',
      })
      this.addEdgeEnable = true;
    },
    logCreateEdge(e) {
      console.log('---------CreateEdge---------');
      console.log(e);
      console.log(mockData);
      console.log(this.canvansRef.getDataMap());
      console.log('----------------');
    },
    logDeleteEdge(e) {
      console.log('---------DeleteEdge---------');
      console.log(e);
      console.log(mockData);
      console.log(this.canvansRef.getDataMap());
      console.log('----------------');
    },
    logChangeEdges(e) {
      console.log('---------ChangeEdges---------');
      console.log(e);
      console.log(mockData);
      console.log(this.canvansRef.getDataMap());
      console.log('----------------');
    },
    logOtherEvent(e) {
      // console.log(e);
    },
    finishLoaded(VueCom) {
      this.butterflyVue = VueCom;
      this.canvansRef = VueCom.canvas;
      window.butterflyVue = VueCom;
      console.log("finish");
      console.log(VueCom);
      this.canvansRef.on('system.node.click', (data, e) => {
        let event = window.event;
        if (event.button !== 2) {
          return !1;
        }
        //
        debugger
      })

    },
  },
  mounted() {
    this.configId = this.$route.query.id
    this.data.dagData = mockData;
    this.initial();

  }
}
</script>

<style>
.node-close {
  cursor: pointer;
  position: relative;
  top: -40px;
  right: -100px;
  width: 14px;
  height: 14px;
  display: block;
  z-index: 1000000;
}
</style>