package com.chua.starter.script.adaptor;

import com.chua.starter.script.configuration.ScriptProperties;
import com.chua.starter.script.watchdog.Watchdog;
import org.springframework.beans.factory.config.BeanDefinition;

import java.util.List;

/**
 * 定义
 *
 * @author CH
 */
public interface Adaptor {

    /**
     * 定义
     *
     * @param config   配置
     * @param watchdog watchdog
     * @return 定义
     */
    List<BeanDefinition> analysis(ScriptProperties.Config config, Watchdog watchdog);
}
