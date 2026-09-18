package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
* 聚合协议服务（protocol-server 分组）全局设置提供者 SPI。
*
* <p>定义 {@code protocol-server} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
* 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。聚合协议服务用于对外提供 API 接口，
* 支持 OpenAI / Claude / Gemini 三协议端点，供其它软件对接使用。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Spi("protocol-server")
public class ProtocolServerGlobalSettingProvider implements GlobalSettingProvider {

    /**
    * 配置分组ID
    */
    private static final String GROUP = "protocol-server";

    /**
    * 分组显示名称
    */
    private static final String GROUP_NAME = "聚合协议服务";

    /**
    * 配置分组ID。
    *
    * @return protocol-server
    */
    @Override
    public String group() {
        return GROUP;
    }

    /**
    * 配置分组显示名称。
    *
    * @return 聚合协议服务
    */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
    * 聚合协议服务配置项。
    *
    * @return 配置项列表
    */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("enabled", "false", "BOOLEAN", "是否启用聚合协议服务（独立端口，提供 OpenAI/Claude/Gemini 三协议端点）", 1),
                new SettingItem("port", "8090", "NUMBER", "聚合协议服务端口（OpenAI/Claude/Gemini 三协议统一入口）", 2),
                new SettingItem("host", "0.0.0.0", "STRING", "聚合协议服务绑定地址", 3)
        );
    }
}
