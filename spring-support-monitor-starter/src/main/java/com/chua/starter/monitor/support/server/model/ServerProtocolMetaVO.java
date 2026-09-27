package com.chua.starter.monitor.support.server.model;

import java.util.List;

/**
 * 协议元数据 —— 前端协议下拉的数据源。
 *
 * @param code            协议编码
 * @param alias           协议别名（小写，提交时用）
 * @param desc            协议描述
 * @param defaultPort     默认端口，SPI 动态发现项为空
 * @param defaultEngine   默认引擎
 * @param requiresBackend 是否必须提供后端地址
 * @param spi             是否来自 Server SPI 动态发现
 * @param params          SPI 参数模板，内置协议为空
 * @author CH
 * @since 4.0.0.42
 */
public record ServerProtocolMetaVO(
        String code,
        String alias,
        String desc,
        Integer defaultPort,
        String defaultEngine,
        boolean requiresBackend,
        boolean spi,
        List<ServerPluginParamVO> params
) {

    /**
     * 规范构造器：非空时对 SPI 参数模板做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。内置协议由
     * {@code ServerManageServiceImpl#protocols()} 显式传 null（对应「SPI 动态发现项为空」语义），
     * 此处保留 null 语义，只在非空时拷贝。</p>
     *
     * @param params SPI 参数模板，可为 null
     */
    public ServerProtocolMetaVO {
        params = params == null ? null : List.copyOf(params);
    }
}
