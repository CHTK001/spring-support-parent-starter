package com.chua.starter.spider.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.mapper.SpiderExecutionLogMapper;
import com.chua.starter.spider.service.SpiderExecutionLogService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 执行日志服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SpiderExecutionLogServiceImpl
        extends SpiderBaseServiceImpl<SpiderExecutionLogMapper, SpiderExecutionLog>
        implements SpiderExecutionLogService {

    @Override
    public List<SpiderExecutionLog> listByExecutionNo(String executionNo) {
        return list(Wrappers.<SpiderExecutionLog>lambdaQuery()
                .eq(SpiderExecutionLog::getExecutionNo, executionNo)
                .orderByAsc(SpiderExecutionLog::getStartAt));
    }
}