package com.chua.starter.sync.data.support.sync.transformer;

import com.chua.starter.sync.data.support.sync.transformer.impl.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 转换器单元测试
 */
class TransformerTest {
    
    @Test
    void testFieldMappingTransformer() {
        FieldMappingTransformer transformer = new FieldMappingTransformer();
        
        Map<String, Object> input = new HashMap<>();
        input.put("old_name", "张三");
        input.put("old_age", 25);
        
        TransformConfig config = new TransformConfig();
        Map<String, String> mappings = new HashMap<>();
        mappings.put("old_name", "new_name");
        mappings.put("old_age", "new_age");
        config.setFieldMappings(mappings);
        
        Map<String, Object> output = transformer.transform(input, config);
        
        assertEquals("张三", output.get("new_name"));
        assertEquals(25, output.get("new_age"));
        assertNull(output.get("old_name"));
    }
    
    @Test
    void testDataFilterTransformer() {
        DataFilterTransformer transformer = new DataFilterTransformer();
        
        Map<String, Object> input = new HashMap<>();
        input.put("age", 25);
        input.put("status", "active");
        
        TransformConfig config = new TransformConfig();
        TransformConfig.FilterRule rule = new TransformConfig.FilterRule();
        rule.setType(TransformConfig.FilterType.EXPRESSION);
        rule.setExpression("age > 18 && status == 'active'");
        config.setFilterRule(rule);
        
        Map<String, Object> output = transformer.transform(input, config);
        assertNotNull(output);
        
        input.put("age", 15);
        output = transformer.transform(input, config);
        assertNull(output);
    }
    
    @Test
    void testDataMaskingTransformer() {
        DataMaskingTransformer transformer = new DataMaskingTransformer();
        
        Map<String, Object> input = new HashMap<>();
        input.put("phone", "13812345678");
        input.put("email", "test@example.com");
        
        TransformConfig config = new TransformConfig();
        Map<String, TransformConfig.MaskingRule> rules = new HashMap<>();
        
        TransformConfig.MaskingRule phoneRule = new TransformConfig.MaskingRule();
        phoneRule.setType(TransformConfig.MaskingType.PHONE);
        rules.put("phone", phoneRule);
        
        TransformConfig.MaskingRule emailRule = new TransformConfig.MaskingRule();
        emailRule.setType(TransformConfig.MaskingType.EMAIL);
        rules.put("email", emailRule);
        
        config.setMaskingRules(rules);
        
        Map<String, Object> output = transformer.transform(input, config);
        
        String maskedPhone = (String) output.get("phone");
        assertTrue(maskedPhone.contains("****"));
        
        String maskedEmail = (String) output.get("email");
        assertTrue(maskedEmail.contains("***"));
    }
    
    @Test
    void testTransformerFactory() {
        TransformerFactory factory = new TransformerFactory();
        
        assertTrue(factory.hasTransformer("MAPPING"));
        assertTrue(factory.hasTransformer("FILTER"));
        assertTrue(factory.hasTransformer("MASKING"));
        assertTrue(factory.hasTransformer("SCRIPT"));
        
        DataTransformer transformer = factory.getTransformer("MAPPING");
        assertNotNull(transformer);
        assertInstanceOf(FieldMappingTransformer.class, transformer);
    }
    
    @Test
    void testTransformerChain() {
        TransformerFactory factory = new TransformerFactory();
        TransformerChain chain = new TransformerChain(factory);
        
        // 添加字段映射转换器
        TransformConfig mappingConfig = new TransformConfig();
        Map<String, String> mappings = new HashMap<>();
        mappings.put("old_name", "name");
        mappingConfig.setFieldMappings(mappings);
        chain.addTransformer("MAPPING", mappingConfig);
        
        // 添加数据脱敏转换器
        TransformConfig maskingConfig = new TransformConfig();
        Map<String, TransformConfig.MaskingRule> rules = new HashMap<>();
        TransformConfig.MaskingRule phoneRule = new TransformConfig.MaskingRule();
        phoneRule.setType(TransformConfig.MaskingType.PHONE);
        rules.put("phone", phoneRule);
        maskingConfig.setMaskingRules(rules);
        chain.addTransformer("MASKING", maskingConfig);
        
        Map<String, Object> input = new HashMap<>();
        input.put("old_name", "张三");
        input.put("phone", "13812345678");
        
        Map<String, Object> output = chain.execute(input);
        
        assertEquals("张三", output.get("name"));
        assertTrue(((String) output.get("phone")).contains("****"));
    }
    
    @Test
    void testTransformConfigParser() {
        TransformConfigParser parser = new TransformConfigParser();
        
        String json = "{\"type\":\"MAPPING\",\"fieldMappings\":{\"old\":\"new\"}}";
        TransformConfig config = parser.parse(json);
        
        assertEquals("MAPPING", config.getType());
        assertNotNull(config.getFieldMappings());
        assertEquals("new", config.getFieldMappings().get("old"));
        
        assertTrue(parser.validate(json));
        assertFalse(parser.validate("invalid json"));
    }
}
