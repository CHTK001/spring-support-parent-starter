package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorLimit;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.mapper.MonitorLimitMapper;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorLimitService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

@Service
public class MonitorLimitServiceImpl extends ServiceImpl<MonitorLimitMapper, MonitorLimit> implements MonitorLimitService{
    @Resource
    private MonitorServerFactory monitorServerFactory;

    @Resource
    private MonitorAppService monitorAppService;

    @Resource
    private TransactionTemplate transactionTemplate;
    @Override
    public Boolean removeBatchByIdsAndNotify(Set<String> ids) {

        return transactionTemplate.execute(status -> {
            List<MonitorLimit> monitorLimits = baseMapper.selectBatchIds(ids);
            baseMapper.deleteBatchIds(ids);
            for (MonitorLimit monitorLimit : monitorLimits) {
                List<MonitorRequest> heart = monitorServerFactory.getHeart(monitorLimit.getLimitApp());
                if(ObjectUtils.isEmpty(heart)) {
                    return true;
                }

                monitorLimit.setLimitStatus(0);
                for (MonitorRequest monitorRequest : heart) {
                    monitorAppService.upload(null, monitorRequest, Json.toJSONString(monitorLimit), "LIMIT", CommandType.REQUEST);
                }
            }
            return true;
        });
    }
}
