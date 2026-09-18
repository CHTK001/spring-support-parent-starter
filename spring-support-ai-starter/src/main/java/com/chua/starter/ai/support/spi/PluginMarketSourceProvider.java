package com.chua.starter.ai.support.spi;

/**
* 插件市场源 SPI（在线市场）。
*
* <p>每个在线插件市场源一个实现，通过 {@code @Spi("source-key")} 注册。
* 当前插件市场暂无数据源，预留接口。开关状态持久化在
* {@code sys_ai_global_setting} 的 {@code plugins} 分组。</p>
*
* @since 4.0.0.43
* @author CH
 */
public interface PluginMarketSourceProvider {

    /**
    * 市场源标识。
    *
    * @return 源标识
    */
    String source();

    /**
    * 市场源显示名称。
    *
    * @return 显示名称
    */
    String displayName();

    /**
    * 市场源主页地址。
    *
    * @return 外部 URL
    */
    String url();

    /**
    * 市场类型：plugin。
    *
    * @return 市场类型
    */
    String marketType();

    /**
    * 默认是否启用。
    *
    * @return true 表示默认启用
    */
    default boolean defaultEnabled() {
        return true;
    }
}
