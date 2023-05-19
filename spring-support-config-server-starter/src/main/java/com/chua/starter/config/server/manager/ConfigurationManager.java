package com.chua.starter.config.server.manager;

import com.chua.common.support.function.NameAware;
import com.chua.starter.config.server.protocol.ProtocolResolver;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;

/**
 * 配置管理
 *
 * @author CH
 * @since 2022/8/1 9:36
 */
public interface ConfigurationManager extends NameAware, ProtocolResolver, InitializingBean {
    /**
     * 注册数据
     *
     * @param data       数据
     * @param binderName 名称
     */
    void register(Map<String, Object> data, String binderName);

    /**
     * 注销数据
     *
     * @param binderName 名称
     */
    void unregister(String binderName);

    /**
     * 查找数据
     *
     * @param binderName 名称
     * @param profile    环境
     * @return 数据
     */
    String findValue(String binderName, String profile);
}
