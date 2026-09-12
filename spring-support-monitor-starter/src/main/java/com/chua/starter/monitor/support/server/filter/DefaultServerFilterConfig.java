package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilterConfig;

import java.util.Collections;
import java.util.Map;

/**
* 服务管理 —— 默认 服务端过滤器 初始化配置实现。
*
* <p>承载插件描述中的 {@code params}，在 {@code ServerFilter#init(ServerFilterConfig)}
* 阶段注入给过滤器实现。</p>
*
* @author CH
* @since 4.0.0.42
 */
public class DefaultServerFilterConfig implements ServerFilterConfig {

    /**
    * 过滤器名称
     */
    private final String filterName;
    /**
    * 初始化参数
     */
    private final Map<String, String> initParameters;
    /**
    * 所属服务器配置
     */
    private final ServerSetting serverSetting;

    /**
    * 创建配置实例
    *
    * @param filterName     过滤器名称
    * @param initParameters 初始化参数
    * @param serverSetting  所属服务器配置
     */
    public DefaultServerFilterConfig(String filterName, Map<String, String> initParameters, ServerSetting serverSetting) {
        this.filterName = filterName;
        this.initParameters = initParameters == null ? Collections.emptyMap() : initParameters;
        this.serverSetting = serverSetting;
    }

    @Override
    public String getFilterName() {
        return filterName;
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Map<String, String> getInitParameters() {
        return Collections.unmodifiableMap(initParameters);
    }

    @Override
    public ServerSetting getServerSetting() {
        return serverSetting;
    }
}
