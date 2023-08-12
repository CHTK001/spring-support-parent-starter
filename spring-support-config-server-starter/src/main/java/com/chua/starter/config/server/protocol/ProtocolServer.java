package com.chua.starter.config.server.protocol;

import com.chua.common.support.function.NameAware;
import com.chua.starter.config.server.entity.NotifyConfig;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfo;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.Set;

/**
 * 协议服务
 *
 * @author CH
 * @since 2022/8/1 8:58
 */
public interface ProtocolServer extends ProtocolResolver, NameAware, InitializingBean, DisposableBean {
    /**
     * 通知
     *
     * @param config   配置
     * @param keyValue 數據
     */
    void notifyClient(NotifyConfig config, String keyValue);

    /**
     * 保存數據
     *
     * @param configValue 配置
     */
    void save(ConfigurationCenterInfo configValue);

    /**
     * 刪除數據
     *
     * @param configId 配置
     */
    void deleteById(String configId);

    /**
     * 环境
     *
     * @return 环境
     */
    Set<String> profile();

    /**
     * 应用
     *
     * @return 应用
     */
    Set<String> applications();

    /**
     * 更新
     *
     * @param configValue configValue
     */
    void updateById(ConfigurationCenterInfo configValue);
}
