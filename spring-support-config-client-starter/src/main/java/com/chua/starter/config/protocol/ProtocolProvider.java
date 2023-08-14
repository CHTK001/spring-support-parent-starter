package com.chua.starter.config.protocol;

import com.chua.common.support.function.NameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.List;

/**
 * 协议
 *
 * @author CH
 * @since 2022/7/30 12:07
 */
public interface ProtocolProvider extends NameAware, PriorityOrdered, InitializingBean, DisposableBean {
    /**
     * 注册配置
     *
     * @param environment 环境
     * @return 配置
     */
    List<PropertiesPropertySource> register(ConfigurableEnvironment environment);

    /**
     * 订阅书
     *
     * @param subscribe 订阅
     * @param dataType  数据类型
     * @param <T>       类型
     * @return 结果
     */
    <T> String subscribe(String subscribe, String dataType);

    /**
     * 监听数据
     *
     * @param data 数据
     */
    void listener(String data);

    /**
     * 注册数据
     *
     * @param environment the environment to post-process
     */
    default void postProcessEnvironment(ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        List<PropertiesPropertySource> register = null;
        try {
            register = register(environment);
        } catch (Exception ignored) {
        }
        if(null == register) {
            return;
        }
        for (PropertiesPropertySource propertiesPropertySource : register) {
            propertySources.addFirst(propertiesPropertySource);
        }
    }

    /**
     * 优先级
     * @return 优先级
     */
    @Override
    default int getOrder() {
        return Integer.MAX_VALUE;
    }
}
