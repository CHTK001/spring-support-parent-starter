package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectAlertRule;

/**
 * 采集告警规则服务。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectAlertRuleService extends IService<CollectAlertRule> {

    /**
     * 分页列表。
     *
     * @param page      分页对象
     * @param condition 查询条件
     * @return 规则分页
     */
    IPage<CollectAlertRule> pageRules(IPage<CollectAlertRule> page, CollectAlertRule condition);

    /**
     * 保存或更新。
     *
     * @param rule 规则
     * @return 是否成功
     */
    boolean saveOrUpdateRule(CollectAlertRule rule);
}
