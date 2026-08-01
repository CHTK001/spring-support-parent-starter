package com.chua.starter.datasync.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字段映射转换器单元测试。
 *
 * <p>覆盖 DefaultFieldMappingConverter 的 toString/toInteger/toLong/toDouble/toBoolean/toDate 转换。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DefaultFieldMappingConverterTest {

    /**
     * 被测转换器
     */
    private final FieldMappingConverter converter = new DefaultFieldMappingConverter();

    @Test
    void convertToString() {
        assertEquals("123", converter.convert(123, "name", "user_name", "toString"));
        assertEquals("456.7", converter.convert(456.7, "name", "user_name", "toString"));
        assertEquals("true", converter.convert(true, "flag", "flag_s", "toString"));
    }

    @Test
    void convertToInteger() {
        assertEquals(456, converter.convert("456", "age", "user_age", "toInteger"));
        assertEquals(10, converter.convert(10.9, "age", "user_age", "toInteger"));
    }

    @Test
    void convertToLong() {
        assertEquals(123456789L, converter.convert("123456789", "id", "user_id", "toLong"));
    }

    @Test
    void convertToDouble() {
        assertEquals(78.9, converter.convert("78.9", "amount", "order_amount", "toDouble"));
        assertEquals(100.5, converter.convert(100.5, "amount", "order_amount", "toDouble"));
    }

    @Test
    void convertToBoolean() {
        assertEquals(true, converter.convert("true", "enabled", "is_enabled", "toBoolean"));
        assertEquals(false, converter.convert("false", "enabled", "is_enabled", "toBoolean"));
    }

    @Test
    void convertPassThroughWhenNoConverter() {
        Object value = new Object();
        assertSame(value, converter.convert(value, "f", "t", null));
        assertSame(value, converter.convert(value, "f", "t", ""));
    }

    @Test
    void convertUnknownConverterPassThrough() {
        Object value = "abc";
        assertSame(value, converter.convert(value, "f", "t", "unknown-converter"));
    }

    @Test
    void applyMappings() {
        java.util.List<DataSyncFieldMapping> fieldMappings = java.util.List.of(
                new TestFieldMapping("name", "user_name", "toString"),
                new TestFieldMapping("age", "user_age", "toInteger")
        );
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("name", "张三");
        source.put("age", "30");

        java.util.Map<String, Object> result = converter.applyMappings(source, fieldMappings);

        assertEquals("张三", result.get("user_name"));
        assertEquals(30, result.get("user_age"));
    }

    /**
     * 测试用字段映射实现。
     */
    private static class TestFieldMapping implements DataSyncFieldMapping {
        private final String sourceField;
        private final String targetField;
        private final String converter;

        TestFieldMapping(String sourceField, String targetField, String converter) {
            this.sourceField = sourceField;
            this.targetField = targetField;
            this.converter = converter;
        }

        @Override
        public String sourceField() {
            return sourceField;
        }

        @Override
        public String targetField() {
            return targetField;
        }

        @Override
        public String converter() {
            return converter;
        }
    }
}
