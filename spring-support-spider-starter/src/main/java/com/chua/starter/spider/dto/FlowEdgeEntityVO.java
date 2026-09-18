package com.chua.starter.spider.dto;

/** @作者 CH */
public class FlowEdgeEntityVO {
    /** Flowedgefrom */
    private String flowEdgeFrom;
    /** flowedge转为 */
    private String flowEdgeTo;
    /** Flowedge文本 */
    private String flowEdgeText;
    /** Flowedgecondfield */
    private String flowEdgeCondField;
    /** flowedgecondop */
    private String flowEdgeCondOp;
    /** Flowedgecond值 */
    private String flowEdgeCondValue;
    /** Flowedge优先级 */
    private Integer flowEdgePriority;

    /**
    * 获取流edge从
    *
    * @return 获取流edge从的结果
    */
    public String getFlowEdgeFrom() { return flowEdgeFrom; }
    /**
    * 设置流edge从
    *
    * @param flowEdgeFrom 流edge从
    */
    public void setFlowEdgeFrom(String flowEdgeFrom) { this.flowEdgeFrom = flowEdgeFrom; }
    /**
    * 获取流edge转为
    *
    * @return 获取流edge转为的结果
    */
    public String getFlowEdgeTo() { return flowEdgeTo; }
    /**
    * 设置流edge转为
    *
    * @param flowEdgeTo 流edge转为
    */
    public void setFlowEdgeTo(String flowEdgeTo) { this.flowEdgeTo = flowEdgeTo; }
    /**
    * 获取流edge文本
    *
    * @return 获取流edge文本的结果
    */
    public String getFlowEdgeText() { return flowEdgeText; }
    /**
    * 设置流edge文本
    *
    * @param flowEdgeText 流edge文本
    */
    public void setFlowEdgeText(String flowEdgeText) { this.flowEdgeText = flowEdgeText; }
    /**
    * 获取流edgecond字段
    *
    * @return 获取流edgecond字段的结果
    */
    public String getFlowEdgeCondField() { return flowEdgeCondField; }
    /**
    * 设置流edgecond字段
    *
    * @param flowEdgeCondField 流edgecond字段
    */
    public void setFlowEdgeCondField(String flowEdgeCondField) { this.flowEdgeCondField = flowEdgeCondField; }
    /**
    * 获取流edgecondop
    *
    * @return 获取流edgecondop的结果
    */
    public String getFlowEdgeCondOp() { return flowEdgeCondOp; }
    /**
    * 设置流edgecondop
    *
    * @param flowEdgeCondOp 流edgecondop
    */
    public void setFlowEdgeCondOp(String flowEdgeCondOp) { this.flowEdgeCondOp = flowEdgeCondOp; }
    /**
    * 获取流edgecond价值观
    *
    * @return 获取流edgecond价值观的结果
    */
    public String getFlowEdgeCondValue() { return flowEdgeCondValue; }
    /**
    * 设置流edgecond价值观
    *
    * @param flowEdgeCondValue 流edgecond价值观
    */
    public void setFlowEdgeCondValue(String flowEdgeCondValue) { this.flowEdgeCondValue = flowEdgeCondValue; }
    /**
    * 获取流edgepriority
    *
    * @return 获取流edgepriority的结果
    */
    public Integer getFlowEdgePriority() { return flowEdgePriority; }
    /**
    * 设置流edgepriority
    *
    * @param flowEdgePriority 流edgepriority
    */
    public void setFlowEdgePriority(Integer flowEdgePriority) { this.flowEdgePriority = flowEdgePriority; }
}
