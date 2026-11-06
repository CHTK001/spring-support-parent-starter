package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.entity.CollectFlowVersion;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.mapper.CollectFlowVersionMapper;
import com.chua.starter.collect.service.CollectFlowVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程版本服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class CollectFlowVersionServiceImpl extends ServiceImpl<CollectFlowVersionMapper, CollectFlowVersion>
        implements CollectFlowVersionService {

    private final CollectFlowMapper flowMapper;

    public CollectFlowVersionServiceImpl(CollectFlowMapper flowMapper) {
        this.flowMapper = flowMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveVersion(Integer flowId, String flowDsl, String remark) {
        int nextNo = baseMapper.maxVersionNo(flowId) + 1;
        CollectFlowVersion v = new CollectFlowVersion();
        v.setCollectFlowId(flowId);
        v.setVersionNo(nextNo);
        v.setFlowDsl(flowDsl);
        v.setVersionRemark(remark != null ? remark : "v" + nextNo);
        v.setCreateTime(LocalDateTime.now());
        save(v);
        return nextNo;
    }

    @Override
    public List<CollectFlowVersion> listByFlow(Integer flowId) {
        return list(new LambdaQueryWrapper<CollectFlowVersion>()
                .eq(CollectFlowVersion::getCollectFlowId, flowId)
                .orderByDesc(CollectFlowVersion::getVersionNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(Integer flowId, Integer versionNo) {
        CollectFlowVersion target = getOne(new LambdaQueryWrapper<CollectFlowVersion>()
                .eq(CollectFlowVersion::getCollectFlowId, flowId)
                .eq(CollectFlowVersion::getVersionNo, versionNo));
        if (target == null) {
            return false;
        }
        CollectFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            return false;
        }
        flow.setCollectFlowDsl(target.getFlowDsl());
        flow.setUpdateTime(LocalDateTime.now());
        flowMapper.updateById(flow);
        saveVersion(flowId, target.getFlowDsl(), "回滚至 v" + versionNo);
        return true;
    }
}
