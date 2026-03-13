package com.chua.starter.sync.data.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncTransformRule;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTransformRuleMapper;
import com.chua.starter.sync.data.support.service.MonitorSyncTransformRuleService;
import com.chua.starter.sync.data.support.sync.transformer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 转换规则服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSyncTransformRuleServiceImpl implements MonitorSyncTransformRuleService {
    
    private final MonitorSyncTransformRuleMapper transformRuleMapper;
    private final TransformerFactory transformerFactory;
    private final TransformConfigParser configParser;
    
    @Override
    @CacheEvict(value = "transformRules", allEntries = true)
    public Long createRule(MonitorSyncTransformRule rule) {
        if (!validateRule(rule)) {
            throw new IllegalArgumentException("转换规则配置无效");
        }
        
        transformRuleMapper.insert(rule);
        log.info("创建转换规则成功: {}", rule.getRuleId());
        return rule.getRuleId();
    }
    
    @Override
    @CacheEvict(value = "transformRules", allEntries = true)
    public boolean updateRule(MonitorSyncTransformRule rule) {
        if (!validateRule(rule)) {
            throw new IllegalArgumentException("转换规则配置无效");
        }
        
        int rows = transformRuleMapper.updateById(rule);
        log.info("更新转换规则: {}, 影响行数: {}", rule.getRuleId(), rows);
        return rows > 0;
    }
    
    @Override
    @CacheEvict(value = "transformRules", allEntries = true)
    public boolean deleteRule(Long ruleId) {
        int rows = transformRuleMapper.deleteById(ruleId);
        log.info("删除转换规则: {}, 影响行数: {}", ruleId, rows);
        return rows > 0;
    }
    
    @Override
    @Cacheable(value = "transformRules", key = "#ruleId")
    public MonitorSyncTransformRule getRuleById(Long ruleId) {
        return transformRuleMapper.selectById(ruleId);
    }
    
    @Override
    @Cacheable(value = "transformRules", key = "'all'")
    public List<MonitorSyncTransformRule> listAllRules() {
        return transformRuleMapper.selectList(null);
    }
    
    @Override
    public List<MonitorSyncTransformRule> listRulesByType(String ruleType) {
        LambdaQueryWrapper<MonitorSyncTransformRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonitorSyncTransformRule::getRuleType, ruleType);
        return transformRuleMapper.selectList(wrapper);
    }
    
    @Override
    public boolean validateRule(MonitorSyncTransformRule rule) {
        if (rule == null || rule.getRuleType() == null || rule.getRuleConfig() == null) {
            return false;
        }
        
        try {
            // 检查转换器类型是否支持
            if (!transformerFactory.hasTransformer(rule.getRuleType())) {
                log.warn("不支持的转换器类型: {}", rule.getRuleType());
                return false;
            }
            
            // 验证配置格式
            if (!configParser.validate(rule.getRuleConfig())) {
                log.warn("转换规则配置格式无效");
                return false;
            }
            
            // 解析配置
            TransformConfig config = configParser.parse(rule.getRuleConfig());
            
            // 使用转换器验证配置
            DataTransformer transformer = transformerFactory.getTransformer(rule.getRuleType());
            return transformer.validateConfig(config);
            
        } catch (Exception e) {
            log.error("验证转换规则失败", e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> testRule(Long ruleId, Map<String, Object> input) {
        MonitorSyncTransformRule rule = getRuleById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("转换规则不存在: " + ruleId);
        }
        
        try {
            TransformConfig config = configParser.parse(rule.getRuleConfig());
            DataTransformer transformer = transformerFactory.getTransformer(rule.getRuleType());
            
            return transformer.transform(input, config);
            
        } catch (Exception e) {
            log.error("测试转换规则失败: {}", ruleId, e);
            throw new TransformException("测试转换规则失败", e);
        }
    }
    
    @Override
    public List<MonitorSyncTransformRule> list() {
        return listAllRules();
    }
    
    @Override
    public boolean save(MonitorSyncTransformRule rule) {
        if (rule.getRuleId() == null) {
            createRule(rule);
            return true;
        } else {
            return updateRule(rule);
        }
    }
    
    @Override
    public boolean updateById(MonitorSyncTransformRule rule) {
        return updateRule(rule);
    }
    
    @Override
    public boolean removeById(Long ruleId) {
        return deleteRule(ruleId);
    }
}
