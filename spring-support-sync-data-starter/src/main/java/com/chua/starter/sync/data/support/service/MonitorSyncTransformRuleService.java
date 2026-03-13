package com.chua.starter.sync.data.support.service;

import com.chua.starter.sync.data.support.entity.MonitorSyncTransformRule;

import java.util.List;

/**
 * 转换规则服务接口
 */
public interface MonitorSyncTransformRuleService {
    
    /**
     * 创建转换规则
     */
    Long createRule(MonitorSyncTransformRule rule);
    
    /**
     * 更新转换规则
     */
    boolean updateRule(MonitorSyncTransformRule rule);
    
    /**
     * 删除转换规则
     */
    boolean deleteRule(Long ruleId);
    
    /**
     * 根据ID查询规则
     */
    MonitorSyncTransformRule getRuleById(Long ruleId);
    
    /**
     * 查询所有规则
     */
    List<MonitorSyncTransformRule> listAllRules();
    
    /**
     * 根据类型查询规则
     */
    List<MonitorSyncTransformRule> listRulesByType(String ruleType);
    
    /**
     * 验证规则配置
     */
    boolean validateRule(MonitorSyncTransformRule rule);
    
    /**
     * 测试规则转换
     */
    java.util.Map<String, Object> testRule(Long ruleId, java.util.Map<String, Object> input);
    
    /**
     * 查询所有规则列表
     */
    List<MonitorSyncTransformRule> list();
    
    /**
     * 保存规则
     */
    boolean save(MonitorSyncTransformRule rule);
    
    /**
     * 根据ID更新规则
     */
    boolean updateById(MonitorSyncTransformRule rule);
    
    /**
     * 根据ID删除规则
     */
    boolean removeById(Long ruleId);
}
