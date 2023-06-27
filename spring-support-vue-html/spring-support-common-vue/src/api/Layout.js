export default [{
    value: '',
    label: '请选择...'
},{
    value: 'forceLayout',
    label: '力导向图布局',
    type: 'forceLayout',
    options: {
        link: {
            // 线条的距离
            distance: 50,
            // 线条的粗细
            strength: 1
        }
    },

},{
    value: 'dagreGroupLayout',
    label: '有向流程图的布局算法',
    type: 'dagreLayout',
    options: {
        rankdir: 'LR',
        nodesep: 70,
        ranksep: 80,
        controlPoints: false,
    },

},{
    value: 'grid',
    label: '网格布局',
    type: 'grid',
    options: {
        // group的渲染方法
        width: 150,
        // 布局画布总长度
        height: 100,
        // 布局相对起始点
        begin: [0, 0],
        // prevents node overlap, may overflow boundingBox if not enough space
        preventOverlap: true,
        // extra spacing around nodes when preventOverlap: true
        preventOverlapPadding: 10,
        // uses all available space on false, uses minimal space on true
        condense: false,
        //行数
        rows: undefined,
        // 列数
        cols: undefined,
        // 排序方式
        sortBy: 'degree',
        nodeSize: 30,
    },

},{
    value: 'fruchterman',
    label: '力导向布局算法',
    type: 'fruchterman',
    options: {
        // 布局画布总宽度
        width: 500,
        // 布局画布总长度
        height: 500,
        /** 停止迭代的最大迭代数 */
        // maxIteration: 1000,
        /** 布局中心 */
        center: [250, 250],
        /** 重力大小，影响图的紧凑程度 */
        gravity: 5,
        /** 速度 */
        speed: 5,
        /** 是否产生聚类力 */
        clustering: true,
        /** 聚类力大小 */
        clusterGravity: 8,
        link: {
            // 线条的距离
            distance: 50,
            // 线条的粗细
            strength: 1
        }
    },

},{
    value: 'compactBox',
    label: 'compactBox',
    options: {
        direction: 'LR', // H / V / LR / RL / TB / BT
        getHeight(d) {
            return 60;
        },
        getWidth(d) {
            return 120;
        },
        getHGap(d) {
            return 20;
        },
        getVGap(d) {
            return 80;
        }
    },

},{
    value: 'radial',
    label: '辐射状布局',
    type: 'radial',
    options: {
        // 布局画布总宽度
        width:800,
        // 布局画布总长度
        height:800,
        /** 停止迭代的最大迭代数 */
        maxIteration: 200,
        /** 布局中心 */
        center: [400, 400],
        /** 中心点，默认为数据中第一个点 */
        focusNode: '0',
        /** 每一圈半径 */
        unitRadius: 80,
        /** 默认边长度 */
        linkDistance: 100,
        /** 是否防止重叠 */
        preventOverlap: true,
        /** 节点直径 */
        nodeSize: 20,
        /** 节点间距，防止节点重叠时节点之间的最小距离（两节点边缘最短距离） */
        nodeSpacing: undefined,
        /** 是否必须是严格的 radial 布局，即每一层的节点严格布局在一个环上。preventOverlap 为 true 时生  */
        strictRadial: true,
        /** 防止重叠步骤的最大迭代次数 */
        maxPreventOverlapIteration: 200,
        link: {
            // 线条的距离
            distance: 50,
            // 线条的粗细
            strength: 1
        },
    },

}]