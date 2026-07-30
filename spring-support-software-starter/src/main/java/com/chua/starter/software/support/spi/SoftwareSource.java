package com.chua.starter.software.support.spi;

import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;

import java.util.List;

/**
 * 软件源接口 - SPI（Service Provider Interface）
 *
 * <p>定义软件源的标准操作能力，由具体源实现（如包管理器、第三方仓库、本地文件）实现该接口。</p>
 *
 * @author CH
 * @since 2026/7/25
 */
public interface SoftwareSource {

    /**
     * 判断是否支持指定的软件源
     *
     * @param source 软件源配置
     * @return true 表示支持，false 表示不支持
     */
    boolean support(SoftSource source);

    /**
     * 从源获取软件列表
     *
     * @param source 软件源配置
     * @return 软件信息列表
     */
    List<SoftSoftware> fetchSoftwareList(SoftSource source);

    /**
     * 从源获取软件详情
     *
     * @param source 软件源配置
     * @param softwareCode 软件编码
     * @return 软件信息
     */
    SoftSoftware fetchSoftwareDetail(SoftSource source, String softwareCode);

    /**
     * 安装软件
     *
     * @param source 软件源配置
     * @param software 软件信息
     * @param installPath 安装路径
     * @return 安装结果
     */
    SoftwareSourceResult install(SoftSource source, SoftSoftware software, String installPath);

    /**
     * 卸载软件
     *
     * @param source 软件源配置
     * @param software 软件信息
     * @param installPath 安装路径
     * @return 卸载结果
     */
    SoftwareSourceResult uninstall(SoftSource source, SoftSoftware software, String installPath);

    /**
     * 更新软件
     *
     * @param source 软件源配置
     * @param software 软件信息
     * @param targetVersion 目标版本
     * @param installPath 安装路径
     * @return 更新结果
     */
    SoftwareSourceResult update(SoftSource source, SoftSoftware software, String targetVersion, String installPath);

    /**
     * 获取软件最新版本
     *
     * @param source 软件源配置
     * @param softwareCode 软件编码
     * @return 最新版本号
     */
    String getLatestVersion(SoftSource source, String softwareCode);
}