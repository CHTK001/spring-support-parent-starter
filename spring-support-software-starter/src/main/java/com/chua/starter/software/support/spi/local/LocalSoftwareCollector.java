package com.chua.starter.software.support.spi.local;

import com.chua.starter.software.support.entity.SoftSoftware;

import java.util.List;

/**
 * 本地软件采集器 SPI（Strategy + 工厂 模式）
 *
 * <p>每个平台提供自己的实现，由 {@link LocalSoftwareSource} 按当前 OS 选择使用。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
public interface LocalSoftwareCollector {

    /**
     * 判断是否支持当前操作系统
     *
     * @return true 表示当前采集器可用
     */
    boolean support();

    /**
     * 采集已安装软件列表
     *
     * @return 软件信息列表（仅包含名称、版本、安装路径、图标路径等基础信息）
     */
    List<SoftSoftware> collect();
}
