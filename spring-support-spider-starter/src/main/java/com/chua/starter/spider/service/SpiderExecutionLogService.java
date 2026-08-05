package com.chua.starter.spider.service;

import com.chua.starter.spider.entity.SpiderExecutionLog;

import java.util.List;

/**
 * 执行日志服务。
 *
 * <p>提供节点级执行日志的 CRUD，并按执行号（traceId）查询一次执行的全链路。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SpiderExecutionLogService extends SpiderBaseService<SpiderExecutionLog> {

    /**
     * 按执行号查询全链路节点日志（按开始时间升序）。
     *
     * @param executionNo 执行号（traceId）
     * @return 节点日志列表
     */
    List<SpiderExecutionLog> listByExecutionNo(String executionNo);
}