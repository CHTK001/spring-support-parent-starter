package com.chua.starter.spider.support.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderNodeExecutionLog;
import com.chua.starter.spider.support.mapper.SpiderNodeExecutionLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 节点执行日志 Repository
 */
@Repository
public class SpiderNodeExecutionLogRepository
        extends ServiceImpl<SpiderNodeExecutionLogMapper, SpiderNodeExecutionLog> {

    /** 查询指定执行记录的所有节点日志，按开始时间排序 */
    public List<SpiderNodeExecutionLog> findByRecordId(Long recordId) {
        return list(new LambdaQueryWrapper<SpiderNodeExecutionLog>()
                .eq(SpiderNodeExecutionLog::getRecordId, recordId)
                .orderByAsc(SpiderNodeExecutionLog::getStartTime));
    }

    /** 查询指定执行记录中某节点的日志 */
    public Optional<SpiderNodeExecutionLog> findByRecordIdAndNodeId(Long recordId, String nodeId) {
        return Optional.ofNullable(getOne(new LambdaQueryWrapper<SpiderNodeExecutionLog>()
                .eq(SpiderNodeExecutionLog::getRecordId, recordId)
                .eq(SpiderNodeExecutionLog::getNodeId, nodeId)
                .last("LIMIT 1")));
    }

    /** 保存或更新节点日志 */
    public void saveLog(SpiderNodeExecutionLog log) {
        saveOrUpdate(log);
    }
}
