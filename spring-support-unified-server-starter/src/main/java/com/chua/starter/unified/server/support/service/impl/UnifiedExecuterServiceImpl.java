package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterMapper;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 统一执行器服务impl
 *
 * @author CH
 */
@Service
public class UnifiedExecuterServiceImpl extends ServiceImpl<UnifiedExecuterMapper, UnifiedExecuter> implements UnifiedExecuterService{


    private static final String STR_1 = "1";
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void createExecutor(BootRequest request) {
        UnifiedExecuter unifiedExecuter = baseMapper.selectOne(Wrappers.<UnifiedExecuter>lambdaQuery().eq(UnifiedExecuter::getUnifiedExecuterName, request.getAppName()));
        if(null == unifiedExecuter) {
            unifiedExecuter = new UnifiedExecuter();
            unifiedExecuter.setCreateTime(new Date());
            unifiedExecuter.setUnifiedExecuterType("0");

        }

        if(STR_1.equals(unifiedExecuter.getUnifiedExecuterType())) {
            return;
        }
        unifiedExecuter.setUnifiedExecuterName(request.getAppName());
        unifiedExecuter.setUnifiedExecuterAppname(request.getAppName());
        unifiedExecuter.setUpdateTime(new Date());
        Integer unifiedExecuterId = unifiedExecuter.getUnifiedExecuterId();
        if(null == unifiedExecuterId) {
            baseMapper.insert(unifiedExecuter);
            return;
        }
        baseMapper.updateById(unifiedExecuter);
    }
}
