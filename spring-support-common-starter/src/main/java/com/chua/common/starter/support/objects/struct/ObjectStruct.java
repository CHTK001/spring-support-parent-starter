package com.chua.common.starter.support.objects.struct;

import com.chua.common.starter.support.objects.struct.chart.BarChartData;
import com.chua.common.starter.support.objects.struct.chart.RadarChartData;
import com.chua.common.starter.support.objects.struct.chart.RelationData;
import com.chua.common.starter.support.objects.struct.chart.TreemapData;

import java.util.List;

/**
 * 对象结构化接口
 *
 * @author CH
 */
public interface ObjectStruct<T> {

    /**
     * 将对象列表转换为树形结构
     *
     * @param source 对象列表
     * @return 树形节点列表
     */
    List<TreeNode<T>> toTree(List<T> source);

    /**
     * 转换为柱状图数据
     *
     * @param tree 树形节点列表
     * @return 柱状图数据
     */
    BarChartData toBarChartData(List<TreeNode<T>> tree);

    /**
     * 转换为雷达图数据
     *
     * @param tree 树形节点列表
     * @return 雷达图数据
     */
    RadarChartData toRadarChartData(List<TreeNode<T>> tree);

    /**
     * 转换为关系图数据
     *
     * @param tree 树形节点列表
     * @return 关系图数据
     */
    RelationData toRelationData(List<TreeNode<T>> tree);

    /**
     * 转换为矩形树图数据
     *
     * @param tree 树形节点列表
     * @return 矩形树图数据
     */
    TreemapData toTreemapData(List<TreeNode<T>> tree);
}
