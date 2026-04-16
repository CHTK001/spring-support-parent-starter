package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import com.chua.starter.spider.support.mapper.SpiderExecutionRecordMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * 执行记录 Repository。
 *
 * @author CH
 */
@Repository
public class SpiderExecutionRecordRepository extends ServiceImpl<SpiderExecutionRecordMapper, SpiderExecutionRecord> {

    /**
     * 查询指定任务的执行记录列表，按开始时间倒序。
     */
    public List<SpiderExecutionRecord> findByTaskId(Long taskId) {
        return list(new LambdaQueryWrapper<SpiderExecutionRecord>()
                .eq(SpiderExecutionRecord::getTaskId, taskId)
                .orderByDesc(SpiderExecutionRecord::getStartTime));
    }

    /**
     * 查询指定任务最近一条执行记录。若 taskId 为 null，则查询全局最新一条。
     */
    public SpiderExecutionRecord findLatestByTaskId(Long taskId) {
        LambdaQueryWrapper<SpiderExecutionRecord> wrapper = new LambdaQueryWrapper<SpiderExecutionRecord>()
                .orderByDesc(SpiderExecutionRecord::getStartTime)
                .last("LIMIT 1");
        if (taskId != null) {
            wrapper.eq(SpiderExecutionRecord::getTaskId, taskId);
        }
        return getOne(wrapper);
    }

    /**
     * 汇总所有执行记录的 success_count。
     */
    public long sumSuccessCount() {
        return list().stream()
                .map(SpiderExecutionRecord::getSuccessCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * 汇总所有执行记录的 total_requests（URL 收集总数）。
     */
    public long sumTotalRequests() {
        return list().stream()
                .map(SpiderExecutionRecord::getTotalRequests)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }
}
