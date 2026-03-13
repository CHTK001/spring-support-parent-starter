package com.chua.starter.sync.data.support.sync.transformer.impl;

import com.chua.starter.sync.data.support.sync.transformer.DataTransformer;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据脱敏转换器
 * 对敏感数据进行脱敏处理
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class DataMaskingTransformer implements DataTransformer {

    @Override
    public Map<String, Object> transform(Map<String, Object> input, TransformConfig config) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, TransformConfig.MaskingRule> rules = config.getMaskingRules();
        if (rules == null || rules.isEmpty()) {
            return new HashMap<>(input);
        }

        Map<String, Object> output = new HashMap<>(input);
        
        // 应用脱敏规则
        rules.forEach((field, rule) -> {
            if (output.containsKey(field)) {
                Object value = output.get(field);
                Object maskedValue = mask(value, rule);
                output.put(field, maskedValue);
            }
        });

        return output;
    }

    /**
     * 对单个值进行脱敏
     */
    private Object mask(Object value, TransformConfig.MaskingRule rule) {
        if (value == null) {
            return null;
        }

        String str = value.toString();
        if (str.isEmpty()) {
            return str;
        }

        TransformConfig.MaskingType type = rule.getType();
        if (type == null) {
            return value;
        }

        try {
            switch (type) {
                case PHONE:
                    // 手机号脱敏: 138****1234
                    return maskPhone(str);
                    
                case EMAIL:
                    // 邮箱脱敏: ab***@example.com
                    return maskEmail(str);
                    
                case ID_CARD:
                    // 身份证脱敏: 110101********1234
                    return maskIdCard(str);
                    
                case CUSTOM:
                    // 自定义脱敏规则
                    String pattern = rule.getPattern();
                    String replacement = rule.getReplacement();
                    if (pattern != null && replacement != null) {
                        return str.replaceAll(pattern, replacement);
                    }
                    return value;
                    
                default:
                    log.warn("不支持的脱敏类型: {}", type);
                    return value;
            }
        } catch (Exception e) {
            log.error("脱敏处理失败: field={}, type={}, value={}", 
                rule.getType(), type, str, e);
            return value;
        }
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone.length() < 11) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    /**
     * 邮箱脱敏
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 2) {
            return username.charAt(0) + "***" + domain;
        }
        
        return username.substring(0, 2) + "***" + domain;
    }

    /**
     * 身份证脱敏
     */
    private String maskIdCard(String idCard) {
        if (idCard.length() < 18) {
            return idCard;
        }
        return idCard.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
    }

    /**
     * 姓名脱敏
     */
    private String maskName(String name) {
        if (name.length() <= 1) {
            return "*";
        }
        
        StringBuilder masked = new StringBuilder();
        masked.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            masked.append('*');
        }
        return masked.toString();
    }

    /**
     * 银行卡脱敏
     */
    private String maskBankCard(String bankCard) {
        if (bankCard.length() < 16) {
            return bankCard;
        }
        
        String cleaned = bankCard.replaceAll("\\s+", "");
        String masked = cleaned.replaceAll("(\\d{4})\\d+(\\d{4})", "$1 **** **** $2");
        return masked;
    }

    /**
     * 地址脱敏
     */
    private String maskAddress(String address) {
        if (address.length() <= 6) {
            return address.substring(0, address.length() / 2) + "****";
        }
        
        // 保留前6个字符，其余用****代替
        return address.substring(0, 6) + "****";
    }

    @Override
    public boolean validateConfig(TransformConfig config) {
        if (config == null) {
            log.error("转换配置不能为空");
            return false;
        }

        Map<String, TransformConfig.MaskingRule> rules = config.getMaskingRules();
        if (rules == null || rules.isEmpty()) {
            log.error("脱敏规则不能为空");
            return false;
        }

        // 验证每个脱敏规则
        for (Map.Entry<String, TransformConfig.MaskingRule> entry : rules.entrySet()) {
            String field = entry.getKey();
            TransformConfig.MaskingRule rule = entry.getValue();
            
            if (field == null || field.trim().isEmpty()) {
                log.error("脱敏字段名不能为空");
                return false;
            }
            
            if (rule == null || rule.getType() == null) {
                log.error("脱敏规则类型不能为空: field={}", field);
                return false;
            }
            
            // 自定义规则需要提供pattern和replacement
            if (TransformConfig.MaskingType.CUSTOM == rule.getType()) {
                if (rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
                    log.error("自定义脱敏规则的pattern不能为空: field={}", field);
                    return false;
                }
                if (rule.getReplacement() == null) {
                    log.error("自定义脱敏规则的replacement不能为空: field={}", field);
                    return false;
                }
            }
        }

        return true;
    }

    public String getType() {
        return "DATA_MASKING";
    }
}
