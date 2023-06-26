<template>
  <el-button type="primary" size="large" @click="doSave()" :icon="Edit" circle class="full-button"/>

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
import '@/plugins/layx/layx.min.css'
import config from "@/config/common"
import '@/style/easy.css'
import Butterfly from "@/components/butterfly.vue";
import _ from "lodash";
import emergencyMockData from "@/page/emergency/emergency-mockData";
import dragNode from "@/page/drag/node/drag-node.vue";
import request from "axios";
import Node from "@/butterfly/coms/node";
import {Edit} from "@element-plus/icons-vue";

const host = config.host;
export default {
  name: "arrange-dag",
  computed: {
    Edit() {
      return Edit
    }
  },
  components: {Butterfly, dragNode},
  data() {
    return {
      configId: undefined,
      data: {
        options: [],
        dagData: {
          nodes: [],
          edges: []
        },
      },
      mockData: {
        nodes: [],
        edges: []
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

      request.get(host + "/arrange/nodeAndEdge", {
        params: {
          arrangeId: this.configId
        }
      }).then(({data}) => {
        if (data.code === '00000') {
          layx.notice({
            type: 'success',
            message: '初始化成功'
          })
          return !1;
        }
        layx.notice({
          type: 'error',
          message: data.msg
        })
      }).catch(({data}) => {
        layx.notice({
          type: 'error',
          message: '系统错误'
        })
      });
    },
    doSave: function (arrangeId) {
      const nodes = [], edges = [];
      this.canvansRef.getDataMap().nodes.forEach(item => {
        const l = {};
        const a = ['left', 'top', 'id', 'className', 'iconType', 'label', 'menus', 'userData'];
        for (const aElement of a) {
          l[aElement] = item[aElement];
          if (_.isObject(l[aElement])) {
            l[aElement] = JSON.stringify(l[aElement]);
          }
        }
        for (const aElement of a) {
          l[aElement] = item['options'][aElement];
          if (_.isObject(l[aElement])) {
            l[aElement] = JSON.stringify(l[aElement]);
          }
        }
        l['arrangeId'] = this.configId;
        nodes.push(l);
      })

      this.canvansRef.getDataMap().edges.forEach(item => {
        const l = {};
        const a = ['left', 'top', 'id', 'source', 'target', 'targetNode', 'sourceNode', 'menus', 'userData'];
        for (const aElement of a) {
          l[aElement] = item[aElement];
          if (aElement === 'targetNode') {
            l['targetNode'] = l[aElement]['id']
          }

          if (aElement === 'sourceNode') {
            l['sourceNode'] = l[aElement]['id']
          }
          if (_.isObject(l[aElement])) {
            l[aElement] = JSON.stringify(l[aElement]);
          }
        }
        for (const aElement of a) {
          l[aElement] = item['options'][aElement];
          if (_.isObject(l[aElement])) {
            l[aElement] = JSON.stringify(l[aElement]);
          }
        }
        l['arrangeId'] = this.configId;
        edges.push(l);
      })
      const param = {
        arrangeId: this.configId,
        nodes: nodes,
        edges: edges
      }
      console.log(param);
      request.post(host + "/arrange/saveOrUpdateNode", param).then(({data}) => {
        if (data.code === '00000') {
          layx.notice({
            title: '消息提示',
            type: 'success',
            message: '保存成功'
          })
          return !1;
        }
        layx.notice({
          title: '消息提示',
          type: 'error',
          message: data.msg
        })
      }).catch(data => {
        layx.notice({
          type: 'error',
          message: '操作异常'
        })
      });
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
      let canvansRef = this.canvansRef;
      const $this = this;
      let nodes = this.data.dagData.nodes;
      nodes.push({
        id: data.value,
        left: coordinates[0],
        top: coordinates[1],
        render: dragNode,
        userData: data,
        label: data.label,
        menus: {
          del: {
            name: "删除节点", callback: function (key, opt) {
              let id = this.attr('id');
              let node = canvansRef.getNode(id);
              canvansRef.removeNode(node);
              $this.data.dagData.nodes = nodes.filter(it => it.id !== node.id)
            }
          },
        },
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
      console.log(this.canvansRef.getDataMap());
      const canvansRef = this.canvansRef;
      const $this = this;
      console.log('----------------');
      let edges = this.data.dagData.edges;
      e['menus'] = {
        del: {
          name: "删除关系", callback: function (key, opt) {
            let id = this.attr("edge-id");
            let edge = canvansRef.getEdge(id);
            canvansRef.removeEdge(edge);
            $this.data.dagData.edges = edges.filter(it => it.id !== edges.id)
          }
        },
      }
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
    },
  },
  mounted() {
    this.configId = this.$route.query.id
    this.data.dagData = this.mockData;
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

.context-menu-list {
  min-width: 10em;
}

.context-menu-item {
  padding: .2em 1.2em;
}
.policy-base-node {
  cursor: default;
}
.node-container {
  width: 200px;
  height: 45px;
}
.full-button {
  display: block;
  position: fixed;
  right: 0;
  top: 50%;
  cursor: pointer;
  z-index: 20230626;
}
</style>