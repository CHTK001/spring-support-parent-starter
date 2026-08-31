package com.chua.common.support.spi.definition;

/**
 * 服务定义工具类，提供服务定义的构建和操作功能
 *
 * @author CH
 */
public class ServiceDefinitionUtils {

    private ServiceDefinitionUtils() {
    }

    /**
     * 构建服务定义的别名
     * <p>根据传入的参数生成服务定义对象，优先使用 key 作为服务名称，
     * 其次使用 {@code valueType} 的简单类名，最后回退为 "unknown"。</p>
     *
     * @param type      服务类型
     * @param group     所属分组
     * @param value     配置值
     * @param valueType 值类型
     * @param alias     别名
     * @param key       服务键名
     * @param order     排序优先级
     * @return 构建完成的 ServiceDefinition 实例
     */
    public static ServiceDefinition buildDefinitionAlias(Class<?> type, String group, Object value, Class<?> valueType, String alias, String key, int order) {
        return new ServiceDefinition(key != null ? key : (valueType != null ? valueType.getSimpleName() : "unknown"));
    }
}
