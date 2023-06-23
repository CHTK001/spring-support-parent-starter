<template>
  <div class="litegraph-canvas" id="chart"></div>

</template>

<script>
import {Canvas} from "butterfly-dag"
import mockData from "@/mock/mockData"

export default {
  name: "test",
  data() {
    return {
      // mockData: {
      //   nodes: [
      //     {
      //       id: '1',
      //       label: '开始',
      //       className: 'icon-background-color',
      //       iconType: 'icon-bofang',
      //       top: 50,
      //       left: 63,
      //       Class: Node
      //     },
      //     {
      //       id: '2',
      //       label: '默认通过',
      //       className: 'icon-background-color',
      //       iconType: 'icon-rds',
      //       top: 150,
      //       left: 50,
      //       Class: Node
      //     }],
      //   edges: [
      //     {
      //       source: '1',
      //       target: '2',
      //       sourceNode: '1',
      //       targetNode: '2',
      //       arrow: true,
      //       arrowPosition: 0.5,
      //       Class: Edge
      //     }]
      // }
    }
  },
  mounted() {

    let root = document.getElementById('chart');
    const canvas = new Canvas({
      root: root,
      disLinkable: true, // 可删除连线
      linkable: true,    // 可连线
      draggable: true,   // 可拖动
      zoomable: true,    // 可放大
      moveable: true,    // 可平移
      theme: {
        edge: {
          shapeType: 'AdvancedBezier',
          arrow: true,
          arrowPosition: 0.5,  //箭头位置(0 ~ 1)
          arrowOffset: 0.0,    //箭头偏移
        }
      }
    });
    canvas.draw(mockData, () => {//mockData为从mock中获取的数据
      canvas.setGridMode(true, {
        isAdsorb: false,         // 是否自动吸附,默认关闭
        theme: {
          shapeType: 'circle',     // 展示的类型，支持line & circle
          gap: 20,               // 网格间隙
          background: 'rgba(0, 0, 0, 0.65)',     // 网格背景颜色
          circleRadiu: 1.5,        // 圆点半径
          circleColor: 'rgba(255, 255, 255, 0.8)'    // 圆点颜色
        }
      }, false);
    });
  }
}

</script>

<style scoped>

</style>