package com.chua.starter.monitor.util;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.spi.definition.ServiceDefinition;
import com.chua.starter.monitor.annotation.SpiMeta;
import com.chua.starter.monitor.annotation.SpiParam;
import com.chua.starter.monitor.pojo.sync.SpiInfo;
import com.chua.starter.monitor.pojo.sync.SpiParameter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * SPI 参数解析器
 * 支持从注解和反射自动解析 SPI 参数定义
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
public class SpiParameterResolver {

    /**
     * 参数缓存
     */
    private static final Map<String, List<SpiParameter>> PARAM_CACHE = new HashMap<>();

    /**
     * SPI 信息缓存
     */
    private static final Map<String, SpiInfo> INFO_CACHE = new HashMap<>();

    /**
     * 解析 SPI 实现类的参数定义
     *
     * @param spiClass SPI 接口类
     * @param spiName  SPI 名称
     * @return 参数列表
     */
    public static <T> List<SpiParameter> resolveParameters(Class<T> spiClass, String spiName) {
        String cacheKey = spiClass.getName() + "#" + spiName;

        if (PARAM_CACHE.containsKey(cacheKey)) {
            return PARAM_CACHE.get(cacheKey);
        }

        List<SpiParameter> parameters = new ArrayList<>();

        try {
            ServiceProvider<T> provider = ServiceProvider.of(spiClass);
            ServiceDefinition definition = provider.getDefinition(spiName);
            Class<?> implClass = definition != null ? definition.getImplClass() : null;

            if (implClass != null) {
                parameters = resolveFromClass(implClass);
            }
        } catch (Exception e) {
            log.warn("解析 SPI 参数失败, class: {}, name: {}", spiClass.getName(), spiName, e);
        }

        PARAM_CACHE.put(cacheKey, parameters);
        return parameters;
    }

    /**
     * 从类中解析参数定义
     *
     * @param clazz 类
     * @return 参数列表
     */
    public static List<SpiParameter> resolveFromClass(Class<?> clazz) {
        List<SpiParameter> parameters = new ArrayList<>();

        Set<String> processedFields = new HashSet<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || processedFields.contains(field.getName())) {
                    continue;
                }

                processedFields.add(field.getName());

                SpiParam annotation = field.getAnnotation(SpiParam.class);
                if (annotation != null) {
                    SpiParameter param = createParameterFromAnnotation(field, annotation);
                    parameters.add(param);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        parameters.sort(Comparator.comparingInt(p -> p.getOrder() != null ? p.getOrder() : 100));

        return parameters;
    }

    /**
     * 从注解创建参数定义
     *
     * @param field      字段
     * @param annotation 注解
     * @return 参数定义
     */
    private static SpiParameter createParameterFromAnnotation(Field field, SpiParam annotation) {
        SpiParameter param = new SpiParameter();

        String name = annotation.name().isEmpty() ? field.getName() : annotation.name();
        param.setName(name);

        String label = annotation.label().isEmpty() ? name : annotation.label();
        param.setLabel(label);

        param.setDescription(annotation.description());

        String type = annotation.type();
        if (type.isEmpty() || "string".equals(type)) {
            type = inferType(field.getType(), annotation);
        }
        param.setType(type);

        param.setRequired(annotation.required());

        param.setSensitive(annotation.sensitive() || "password".equals(type));

        if (!annotation.defaultValue().isEmpty()) {
            param.setDefaultValue(parseDefaultValue(annotation.defaultValue(), type, field.getType()));
        }

        if (!annotation.placeholder().isEmpty()) {
            param.setPlaceholder(annotation.placeholder());
        }

        if (!annotation.options().isEmpty()) {
            param.setOptions(parseOptions(annotation.options()));
        }

        if (!annotation.pattern().isEmpty()) {
            param.setValidation(annotation.pattern());
        }

        if (annotation.min() != Double.MIN_VALUE) {
            param.setMin(annotation.min());
        }
        if (annotation.max() != Double.MAX_VALUE) {
            param.setMax(annotation.max());
        }

        if (!annotation.group().isEmpty()) {
            param.setGroup(annotation.group());
        }

        param.setOrder(annotation.order());

        if (!annotation.dependsOn().isEmpty()) {
            param.setDependsOn(parseDependsOn(annotation.dependsOn()));
        }

        return param;
    }

    /**
     * 推断参数类型
     *
     * @param fieldType  字段类型
     * @param annotation 注解
     * @return 参数类型
     */
    private static String inferType(Class<?> fieldType, SpiParam annotation) {
        if (!annotation.type().isEmpty()) {
            return annotation.type();
        }

        if (fieldType == boolean.class || fieldType == Boolean.class) {
            return "boolean";
        } else if (fieldType == int.class || fieldType == Integer.class ||
                fieldType == long.class || fieldType == Long.class ||
                fieldType == double.class || fieldType == Double.class ||
                fieldType == float.class || fieldType == Float.class) {
            return "number";
        } else if (fieldType == Date.class || fieldType.getName().contains("Date")) {
            return "date";
        } else if (List.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            return "multiselect";
        } else if (Map.class.isAssignableFrom(fieldType)) {
            return "keyvalue";
        }

        return "string";
    }

    /**
     * 解析默认值
     *
     * @param value     字符串值
     * @param type      参数类型
     * @param fieldType 字段类型
     * @return 解析后的值
     */
    private static Object parseDefaultValue(String value, String type, Class<?> fieldType) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            switch (type) {
                case "number":
                    if (fieldType == int.class || fieldType == Integer.class) {
                        return Integer.parseInt(value);
                    } else if (fieldType == long.class || fieldType == Long.class) {
                        return Long.parseLong(value);
                    } else if (fieldType == double.class || fieldType == Double.class) {
                        return Double.parseDouble(value);
                    } else if (fieldType == float.class || fieldType == Float.class) {
                        return Float.parseFloat(value);
                    }
                    return Double.parseDouble(value);
                case "boolean":
                    return Boolean.parseBoolean(value);
                default:
                    return value;
            }
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 解析可选值
     *
     * @param options 可选值字符串（格式: label:value,label:value）
     * @return 可选值列表
     */
    private static List<Map<String, Object>> parseOptions(String options) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (options == null || options.isEmpty()) {
            return result;
        }

        String[] parts = options.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split(":");
            Map<String, Object> option = new HashMap<>();
            if (kv.length == 2) {
                option.put("label", kv[0].trim());
                option.put("value", kv[1].trim());
            } else {
                option.put("label", kv[0].trim());
                option.put("value", kv[0].trim());
            }
            result.add(option);
        }

        return result;
    }

    /**
     * 解析依赖条件
     *
     * @param dependsOn 依赖条件字符串（格式: fieldName=value）
     * @return 依赖条件映射
     */
    private static Map<String, Object> parseDependsOn(String dependsOn) {
        Map<String, Object> result = new HashMap<>();

        if (dependsOn == null || dependsOn.isEmpty()) {
            return result;
        }

        String[] parts = dependsOn.split("=");
        if (parts.length == 2) {
            result.put("field", parts[0].trim());
            result.put("value", parts[1].trim());
        }

        return result;
    }

    /**
     * 解析 SPI 元信息
     *
     * @param spiClass SPI 接口类
     * @param spiName  SPI 名称
     * @param type     SPI 类型
     * @return SPI 信息
     */
    public static <T> SpiInfo resolveSpiInfo(Class<T> spiClass, String spiName, String type) {
        String cacheKey = spiClass.getName() + "#" + spiName;

        if (INFO_CACHE.containsKey(cacheKey)) {
            return INFO_CACHE.get(cacheKey);
        }

        SpiInfo info = new SpiInfo();
        info.setName(spiName);
        info.setType(type);
        info.setAvailable(true);

        try {
            ServiceProvider<T> provider = ServiceProvider.of(spiClass);
            ServiceDefinition definition = provider.getDefinition(spiName);
            Class<?> implClass = definition != null ? definition.getImplClass() : null;

            if (implClass != null) {
                info.setClassName(implClass.getName());

                SpiMeta meta = implClass.getAnnotation(SpiMeta.class);
                if (meta != null) {
                    if (!meta.displayName().isEmpty()) {
                        info.setDisplayName(meta.displayName());
                    }
                    if (!meta.description().isEmpty()) {
                        info.setDescription(meta.description());
                    }
                    if (!meta.icon().isEmpty()) {
                        info.setIcon(meta.icon());
                    }
                    if (!meta.color().isEmpty()) {
                        info.setColor(meta.color());
                    }
                    info.setOrder(meta.order());
                }
            }
        } catch (Exception e) {
            log.warn("解析 SPI 信息失败, class: {}, name: {}", spiClass.getName(), spiName, e);
        }

        INFO_CACHE.put(cacheKey, info);
        return info;
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        PARAM_CACHE.clear();
        INFO_CACHE.clear();
    }
}
