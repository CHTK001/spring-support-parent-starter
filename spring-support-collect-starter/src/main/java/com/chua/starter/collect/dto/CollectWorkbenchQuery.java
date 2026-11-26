package com.chua.starter.collect.dto;

/**
 * 采集工作台查询，筛选在数据库分页之前执行。
 *
 * @param keyword 名称或编码关键词
 * @param categoryId 分类，0 表示未分类
 * @param status 自动调度状态
 * @param lastRunStatus 最近运行状态，NONE 表示从未执行
 * @author CH
 */
public record CollectWorkbenchQuery(String keyword, Integer categoryId, Integer status, String lastRunStatus) {
}
