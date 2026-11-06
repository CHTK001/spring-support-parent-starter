package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.entity.CollectAlertRule;
import com.chua.starter.collect.mapper.CollectAlertRuleMapper;
import com.chua.starter.collect.service.CollectAlertRuleService;
import org.springframework.stereotype.Service;

/**
 * 采集告警规则服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class CollectAlertRuleServiceImpl extends ServiceImpl<CollectAlertRuleMapper, CollectAlertRule>
        implements CollectAlertRuleService {

    @Override
    public IPage<CollectAlertRule> pageRules(IPage<CollectAlertRule> page, CollectAlertRule condition) {
        LambdaQueryWrapper<CollectAlertRule> w = new LambdaQueryWrapper<>();
        if (condition != null) {
            w.eq(condition.getCollectFlowId() != null, CollectAlertRule::getCollectFlowId, condition.getCollectFlowId())
                    .like(condition.getCollectAlertRuleName() != null, CollectAlertRule::getCollectAlertRuleName,
                            condition.getCollectAlertRuleName())
                    .eq(condition.getAlertType() != null, CollectAlertRule::getAlertType, condition.getAlertType());
        }
        w.orderByDesc(CollectAlertRule::getCollectAlertRuleId);
        return page(page, w);
    }

    @Override
    public boolean saveOrUpdateRule(CollectAlertRule rule) {
        if (rule.getCollectAlertRuleId() == null) {
            rule.setAlertEnabled(1);
            return save(rule);
        }
        return updateById(rule);
    }
}
