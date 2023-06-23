import Node from '@/butterfly/coms/node';
import Edge from '@/butterfly/coms/edge';

const endpoints = [
  {
    id: 'right',
    orientation: [1, 0],
    pos: [0, 0.5]
  },
  {
    id: 'left',
    orientation: [-1, 0],
    pos: [0, 0.5]
  }
];

export default {
  groups: [
  ],
  nodes: [
    {
      id: '1',
      label: '开始',
      className: 'icon-background-color',
      iconType: 'icon-bofang',
      top: 50,
      left: 63,
      endpoints: endpoints,
      Class: Node
    },
    {
      id: '2',
      label: '默认通过',
      className: 'icon-background-color',
      iconType: 'icon-rds',
      top: 150,
      left: 50,
      endpoints: endpoints,
      Class: Node
    },
    // {
    //   id: '1',
    //   group: '1',
    //   top: 40,
    //   left: 20,
    //   endpoints: endpoints,
    // },
    // {
    //   id: '2',
    //   group: '2',
    //   top: 40,
    //   left: 20,
    //   endpoints: endpoints,
    //   render: baseNode
    // },
    // {
    //   id: '3',
    //   group: '3',
    //   top: 40,
    //   left: 50,
    //   endpoints: endpoints,
    //   render: `<div>测试节点3</div>`
    // },
    // {
    //   id: '4',
    //   top: 200,
    //   left: 390,
    //   endpoints: endpoints,
    //   render: `<el-button type="primary">节点4</el-button>`
    //   // 可以用任何Ui库（安装即可）,用element组件要先安装element-ui
    // }
  ],
  edges: [
    // {
    //   id: '1.right-2.left',
    //   sourceNode: '1',
    //   targetNode: '2',
    //   source: 'right',
    //   target: 'left',
    //   render: baseLabel
    // },
    // {
    //   id: '2.right-3.left',
    //   sourceNode: '2',
    //   targetNode: '3',
    //   source: 'right',
    //   target: 'left',
    //   render: '<div>测试label</div>'
    // },
    {
      id: '1-2',
      source: '1',
      target: '2',
      sourceNode: 'left',
      targetNode: 'right',
      arrow: true,
      arrowPosition: 0.5,
      Class: Edge
    },
  ],
};
