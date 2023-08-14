package com.chua.starter.config.protocol;

import com.chua.common.support.function.NameAware;
import com.chua.starter.config.entity.PluginMeta;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.PriorityOrdered;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 协议
 *
 * @author CH
 * @since 2022/7/30 12:07
 */
public interface ProtocolProvider extends NameAware, PriorityOrdered, InitializingBean, DisposableBean {

    /**
     * 订阅书
     *
     * @param subscribe 订阅
     * @param data      数据
     * @param dataType  数据类型
     * @param consumer  监听
     */
    void subscribe(String subscribe, String dataType, Map<String, Object> data, Consumer<Map<String, Object>> consumer);

    /**
     * 监听数据
     *
     * @param data 数据
     */
    void listener(String data);


    /**
     * 优先级
     *
     * @return 优先级
     */
    @Override
    default int getOrder() {
        return Integer.MAX_VALUE;
    }

    /**
     * 基本信息
     *
     * @param configProperties 配置
     * @param environment      environment
     * @return 基本信息
     */
    PluginMeta getMeta();
}
