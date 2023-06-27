<template>
  <div :class="className">
    <div class="butterfly-vue-container" ref="canvas-dag"></div>
  </div>
</template>

<script>
import '@/assets/index.less';
import '@/assets/iconfont.less';
import 'butterfly-dag/dist/index.css';
import {Canvas, Tips} from 'butterfly-dag';
import {defaultOptions} from '@/butterfly/util/default-data.js';
import {processEdge, processGroups, processNodes,} from '@/butterfly/util/process.js';

import $ from "jquery";
import recalc from '@/butterfly/util/re-calc.js';
import relayout from '@/butterfly/util/re-layout.js';

document.body.onselectstart = document.body.oncontextmenu = function () {
  return false;
}
export default {
  name: "Butterfly",
  emits: ['onCreateEdge', 'onDeleteEdge', 'onChangeEdges', 'onOtherEvent', 'onLoaded'],
  props: {
    layout: {
      type: Object,
      default: {
        type: 'dagreLayout',
        options: {
          rankdir: 'LR',
          nodesep: 70,
          ranksep: 80,
          controlPoints: false,
        }
      }
    },
    className: {
      type: String,
      default: 'butterfly',
    },
    baseCanvas: {
      type: Function,
      default: Canvas,
    },
    menu: {
      type: Function
    },
    canvasConf: {
      type: Object,
      default: () => {
        return defaultOptions;
      },
    },
    canvasData: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      onEvent: {},
      canvas: null,
      nodes: this.canvasData ? this.canvasData.nodes : [],
      groups: this.canvasData ? this.canvasData.groups : {},
      edges: this.canvasData ? this.canvasData.edges : [],
    };
  },
  methods: {
// 初始化
    initCanvas() {
      const root = this.$refs["canvas-dag"];
      if (!root) {
        console.warn("当前canvas没有绑定dom节点，无法渲染");
        return !1;
      } else {
        this.canvasConf.root = root;
        this.canvasConf.theme = {
          edge: {
            shapeType: 'AdvancedBezier',
            isExpandWidth: false,
            arrow: true
          }
        };
        this.canvasConf.layout = this.layout;
        this.canvas = new this.baseCanvas(this.canvasConf);
        this.canvas.setMinimap(true);
      }
    },

// 更新画布信息
    updateCavans() {
      if (!this.canvas) {
        console.warn("当前canvas为null，初始化存在问题");
        return;
      }

      const oldNodes = this.canvas.nodes;
      const oldEdges = this.canvas.edges;
      const oldGroups = this.canvas.groups;

      processGroups(this.canvas, this.groups, oldGroups, this);
      processNodes(this.canvas, this.nodes, oldNodes, this);
      processEdge(this.canvas, this.edges, oldEdges, this);
    },

// 重新计算节点和边的位置
    re() {
      if (!this.canvas) {
        console.warn("当前canvas为null，初始化存在问题");
        return;
      }

      recalc(this.canvas);
      relayout(this.canvas);
    },

// 重绘所有节点
    redraw() {
      const oldNodes = this.canvas.nodes;
      const oldEdges = this.canvas.edges;
      const oldGroups = this.canvas.groups;

      processEdge(this.canvas, [], oldEdges, this);
      processNodes(this.canvas, [], oldNodes, this);
      processGroups(this.canvas, [], oldGroups, this);

      processGroups(this.canvas, this.groups, oldGroups, this);
      processNodes(this.canvas, this.nodes, oldNodes, this);
      processEdge(this.canvas, this.edges, oldEdges, this);
      this.re();
    },

    onCreateEdge(data) {
      let link = data.links[0];

      if (link) {
        let edgeInfo = {
          id: `${link.sourceNode.id}.${link.sourceEndpoint.id}-${link.targetNode.id}.${link.targetEndpoint.id}`,
          sourceEndpointId: link.sourceEndpoint.id,
          sourceNodeId: link.sourceNode.id,
          targetEndpointId: link.targetEndpoint.id,
          targetNodeId: link.targetNode.id,
        };
        this.edges.push({
          id: `${edgeInfo.sourceNodeId}.${edgeInfo.sourceEndpointId}-${edgeInfo.targetNodeId}.${edgeInfo.targetEndpointId}`,
          sourceNode: edgeInfo.sourceNodeId,
          targetNode: edgeInfo.targetNodeId,
          source: edgeInfo.sourceEndpointId,
          target: edgeInfo.targetEndpointId,
        });
        this.$emit("onCreateEdge", edgeInfo);
        if(edgeInfo.menus) {
          $.contextMenu({
            selector: `.policy-base-edge-${edgeInfo.id.replaceAll(".", "_")}`,
            items: edgeInfo.menus
          });
        }
      }
    },

    onDeleteEdge(data) {
      let link = data.links[0];

      if (link) {
        let edgeInfo = {
          id: link.id,
          sourceEndpointId: link.sourceEndpoint.id,
          sourceNodeId: link.sourceNode.id,
          targetEndpointId: link.targetEndpoint.id,
          targetNodeId: link.targetNode.id,
        };
        let index = this.edges.findIndex((item) => {
          return item.id === link.id;
        });
        this.edges.splice(index, 1);
        this.$emit("onDeleteEdge", edgeInfo);
      }
    },

    on(name, func) {
      this.onEvent[name] = func;
    },
    onChangeEdges(data) {
      let addLinkData = data.addLinks[0];
      let delLinkData = data.delLinks[0];

      if (addLinkData && delLinkData) {
        let edgeInfo = {
          addLink: {
            id: `${addLinkData.sourceNode.id}.${addLinkData.sourceEndpoint.id}-${addLinkData.targetNode.id}.${addLinkData.targetEndpoint.id}`,
            sourceEndpointId: addLinkData.sourceEndpoint.id,
            sourceNodeId: addLinkData.sourceNode.id,
            targetEndpointId: addLinkData.targetEndpoint.id,
            targetNodeId: addLinkData.targetNode.id,
          },
          delLinks: {
            id: `${delLinkData.sourceNode.id}.${delLinkData.sourceEndpoint.id}-${delLinkData.targetNode.id}.${delLinkData.targetEndpoint.id}`,
            sourceEndpointId: delLinkData.sourceEndpoint.id,
            sourceNodeId: delLinkData.sourceNode.id,
            targetEndpointId: delLinkData.targetEndpoint.id,
            targetNodeId: delLinkData.targetNode.id,
          },
          info: data.info,
        };

        let index = this.edges.findIndex((item) => {
          return item.id === edgeInfo.delLinks.id;
        });
        this.edges.splice(index, 1);

        this.edges.push({
          id: `${addLinkData.sourceNode.id}.${addLinkData.sourceEndpoint.id}-${addLinkData.targetNode.id}.${addLinkData.targetEndpoint.id}`,
          sourceNode: edgeInfo.addLink.sourceNodeId,
          targetNode: edgeInfo.addLink.targetNodeId,
          source: edgeInfo.addLink.sourceEndpointId,
          target: edgeInfo.addLink.targetEndpointId,
        });

        this.$emit("onChangeEdges", edgeInfo);
      }
    },

    onOtherEvent(data) {
      this.$emit("onOtherEvent", data);
    },
  },

  watch: {
    layout(val) {
      this.canvas.autoLayout(val.type, val.options);
    },
    groups: {
      handler() {
        this.updateCavans();
        this.re();
      },
      deep: true
    },
    nodes: {
      handler() {
        this.updateCavans();
        this.re();
      },
      deep: true
    },
    edges: {
      handler() {
        this.updateCavans();
        this.re();
      },
      deep: true
    },
    canvasData: {
      handler() {
        this.nodes = this.canvasData.nodes;
        this.groups = this.canvasData.groups;
        this.edges = this.canvasData.edges;
      },
      deep: true
    }
  },
  mounted: function () {
    this.initCanvas();

    if (!this.canvas) {
      console.warn("当前canvas为null，初始化存在问题");
      return;
    }

    this.updateCavans();

    this.re();

    this.$emit("onLoaded", this);

    this.canvas.on("events", (data) => {
      console.log(data.type)
      if (data.type === "link:connect") {
        this.onCreateEdge(data);
      } else if (data.type === "links:delete" && data.links.length > 0) {
        this.onDeleteEdge(data);
      } else if (data.type === "link:reconnect") {
        this.onChangeEdges(data);
      } else {
        if (data.type === 'drag:end') {
          let {dragGroup, dragNode} = data;

          if (dragGroup !== null) {
            let groupIndex = this.groups.findIndex((item) => {
              return item.id === dragGroup.id;
            })
            if (groupIndex !== -1) {
              this.groups[groupIndex].left = dragGroup.left;
              this.groups[groupIndex].top = dragGroup.top;
            }
            this.canvasData.groups = this.groups;
          }

          if (dragNode !== null && Array.isArray(this.nodes)) {
            let nodeIndex = this.nodes.findIndex((item) => {
              return item.id === dragNode.id;
            })
            if (nodeIndex !== -1) {
              this.nodes[nodeIndex].left = dragNode.left;
              this.nodes[nodeIndex].top = dragNode.top;
            }
            this.canvasData.nodes = this.nodes;
          }

        }
        this.onOtherEvent(data);
      }
    });
// window.canvas = this.canvas;
  },
};
</script>

<style scope>
.butterfly-vue {
  min-height: 500px;
  min-width: 500px;
  width: 100%;
  height: 100%;
  display: block;
  position: relative;
}

.butterfly-vue-container {
  height: 100%;
  width: 100%;
  position: absolute;
  display: block;
}

.butterfly-node {
  position: absolute;
  user-select: none;
}
</style>