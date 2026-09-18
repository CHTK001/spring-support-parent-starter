package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.spider.dto.DefinitionStatsVO;
import com.chua.starter.spider.dto.ExecutionRecordVO;
import com.chua.starter.spider.dto.PageResultVO;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.mapper.SpiderExecutionLogMapper;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* 执行日志服务实现。
*
* <p>执行历史按 execution_no 聚合自 {@code spider_execution_log}：通过
* 最小(启动_at)/最大(结束_at) 与 SUM(状态) 规则推导每条执行的起止时间与最终状态，
* 分页结果不含节点级明细，仅返回概要记录。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Service
public class SpiderExecutionLogServiceImpl
        extends SpiderBaseServiceImpl<SpiderExecutionLogMapper, SpiderExecutionLog>
        implements SpiderExecutionLogService {

    /**
    * 状态：成功
    */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /**
    * 状态：失败
    */
    private static final String STATUS_FAIL = "FAIL";
    /**
    * 状态：运行中
    */
    private static final String STATUS_RUNNING = "RUNNING";

    @Override
    /** 列表by执行no */
    public List<SpiderExecutionLog> listByExecutionNo(String executionNo) {
        return list(Wrappers.<SpiderExecutionLog>lambdaQuery()
                .eq(SpiderExecutionLog::getExecutionNo, executionNo)
                .orderByAsc(SpiderExecutionLog::getStartAt));
    }

    @Override
    /** page执行records */
    public PageResultVO<ExecutionRecordVO> pageExecutionRecords(long pageNo, long pageSize, String definitionCode, String status) {
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        long safePageNo = pageNo <= 0 ? 1 : pageNo;

        LambdaQueryWrapper<SpiderExecutionLog> countQuery = Wrappers.<SpiderExecutionLog>lambdaQuery()
                .select(SpiderExecutionLog::getExecutionNo)
                .isNotNull(SpiderExecutionLog::getExecutionNo)
                .ne(SpiderExecutionLog::getExecutionNo, "");
        /**
    * apply过滤器。
    * @param countQuery 数量查询
    * @param definitionCode definition编码
    * @param status 状态
    */
        applyFilters(countQuery, definitionCode, status);
        List<SpiderExecutionLog> distinctLogs = baseMapper.selectList(countQuery);
        long total = distinctLogs == null ? 0L : distinctLogs.size();

        PageResultVO<ExecutionRecordVO> result = new PageResultVO<>();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        result.setTotal(total);
        result.setTotalPages((total + safePageSize - 1) / safePageSize);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            result.setData(Collections.emptyList());
            return result;
        }

        List<String> executionNos = new ArrayList<>(distinctLogs.size());
        for (SpiderExecutionLog log : distinctLogs) {
            String no = log.getExecutionNo();
            if (no != null) {
                executionNos.add(no);
            }
        }
        executionNos.sort(Collections.reverseOrder());

        long start = (safePageNo - 1) * safePageSize;
        long end = Math.min(start + safePageSize, executionNos.size());
        List<String> pagedNos = executionNos.subList((int) start, (int) end);

        QueryWrapper<SpiderExecutionLog> detailQuery = new QueryWrapper<>();
        detailQuery.select(
                "execution_no",
                "MIN(definition_code) AS definition_code",
                "MIN(flow_id) AS flow_id",
                "MIN(start_at) AS start_at",
                "MAX(end_at) AS end_at",
                "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_cnt",
                "SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) AS fail_cnt",
                "SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running_cnt",
                "COUNT(1) AS total_cnt")
                .in("execution_no", pagedNos)
                .groupBy("execution_no");
        List<Map<String, Object>> summaries = baseMapper.selectMaps(detailQuery);

        Map<String, Map<String, Object>> summaryMap = new HashMap<>();
        for (Map<String, Object> row : summaries) {
            summaryMap.put(ObjectUtils.toString(row.get("execution_no")), row);
        }

        List<ExecutionRecordVO> records = new ArrayList<>(pagedNos.size());
        for (String no : pagedNos) {
            Map<String, Object> row = summaryMap.get(no);
            ExecutionRecordVO vo = new ExecutionRecordVO();
            vo.setExecutionNo(no);
            if (row != null) {
                vo.setExecutionStatus(deriveOverallStatus(row));
                vo.setStartedAt(MapUtils.parseLocalDateTimeSafe("start_at", row));
                vo.setFinishedAt(MapUtils.parseLocalDateTimeSafe("end_at", row));
            }
            records.add(vo);
        }

        result.setRecords(records);
        result.setData(records);
        return result;
    }

    @Override
    /** aggregatestats */
    public List<DefinitionStatsVO> aggregateStats() {
        QueryWrapper<SpiderExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "definition_code",
                "execution_no",
                "status",
                "start_at",
                "end_at")
                .isNotNull("definition_code")
                .ne("definition_code", "")
                .orderByDesc("start_at");
        List<Map<String, Object>> rows = baseMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, DefinitionStatsVO> bucket = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = ObjectUtils.toString(row.get("definition_code"));
            if (code == null || code.isEmpty()) {
                continue;
            }
            DefinitionStatsVO stat = bucket.computeIfAbsent(code, k -> {
                DefinitionStatsVO v = new DefinitionStatsVO();
                v.setDefinitionCode(k);
                return v;
            });
            stat.setTotalRuns(stat.getTotalRuns() + 1);
            String s = ObjectUtils.toString(row.get("status"));
            if (STATUS_SUCCESS.equals(s)) {
                stat.setSuccessCount(stat.getSuccessCount() + 1);
            } else if (STATUS_FAIL.equals(s)) {
                stat.setFailCount(stat.getFailCount() + 1);
            } else if (STATUS_RUNNING.equals(s)) {
                stat.setRunningCount(stat.getRunningCount() + 1);
            }
            if (stat.getLatestExecutionNo() == null) {
                stat.setLatestExecutionNo(ObjectUtils.toString(row.get("execution_no")));
            }
        }
        return new ArrayList<>(bucket.values());
    }

    /**
    * 推导整体执行状态：任一节点 失败 则整体 失败；否则若有 RUNNING 则 RUNNING；全 成功 则 成功。
    *
    * @param row 聚合行
    * @return 状态字符串
    */
    private String deriveOverallStatus(Map<String, Object> row) {
        long fail = NumberUtils.toLong(ObjectUtils.defaultIfStringNull(row.get("fail_cnt"), "0"), 0L);
        long running = NumberUtils.toLong(ObjectUtils.defaultIfStringNull(row.get("running_cnt"), "0"), 0L);
        if (fail > 0) {
            return STATUS_FAIL;
        }
        if (running > 0) {
            return STATUS_RUNNING;
        }
        return STATUS_SUCCESS;
    }

    /**
    * 在 lambda查询包装器 上叠加 definition编码 与 状态 过滤。
    *
    * @param wrapper        查询条件
    * @param definitionCode 爬虫编码（可选）
    * @param status         执行状态（可选）
    */
    private void applyFilters(LambdaQueryWrapper<SpiderExecutionLog> wrapper, String definitionCode, String status) {
        if (definitionCode != null && !definitionCode.isEmpty()) {
            wrapper.eq(SpiderExecutionLog::getDefinitionCode, definitionCode);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SpiderExecutionLog::getStatus, status);
        }
    }
}
