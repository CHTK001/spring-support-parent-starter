package com.chua.starter.spider.service;

import com.chua.starter.spider.dto.DefinitionStatsVO;
import com.chua.starter.spider.dto.ExecutionRecordVO;
import com.chua.starter.spider.dto.PageResultVO;
import com.chua.starter.spider.entity.SpiderExecutionLog;

import java.util.List;

/**
* 执行日志服务。
*
* <p>提供节点级执行日志的 CRUD，并按执行号（traceId）查询一次执行的全链路，
* 同时支持执行历史的分页聚合与按爬虫维度的执行统计。</p>
*
* @since 4.0.0.42
* @author CH
 */
public interface SpiderExecutionLogService extends SpiderBaseService<SpiderExecutionLog> {

    /**
    * 按执行号查询全链路节点日志（按开始时间升序）。
    *
    * @param executionNo 执行号（追踪标识）
    * @return 节点日志列表
    */
    List<SpiderExecutionLog> listByExecutionNo(String executionNo);

    /**
    * 分页查询执行记录（按 execution_no 聚合）。
    *
    * @param pageNo        页码（从 1 开始）
    * @param pageSize      每页条数
    * @param definitionCode 爬虫编码（可选，空表示全部）
    * @param status        执行状态（可选，空表示全部）
    * @return 执行记录分页结果
    */
    PageResultVO<ExecutionRecordVO> pageExecutionRecords(long pageNo, long pageSize, String definitionCode, String status);

    /**
    * 按爬虫编码聚合执行统计。
    *
    * @return 统计列表
    */
    List<DefinitionStatsVO> aggregateStats();
}
