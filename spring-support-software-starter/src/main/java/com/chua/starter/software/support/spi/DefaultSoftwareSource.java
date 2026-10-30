package com.chua.starter.software.support.spi;

import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 默认软件源实现 - 空实现，作为回退
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Component
@ConditionalOnMissingBean(SoftwareSource.class)
public class DefaultSoftwareSource implements SoftwareSource {

    @Override
    /**
     * 支持
     * @param source 参数
     * @return 返回值
    */
    public boolean support(SoftSource source) {
        return false;
    }

    @Override
    /**
     * 获取software列表
     * @param source 参数
     * @return 返回值
    */
    public List<SoftSoftware> fetchSoftwareList(SoftSource source) {
        return Collections.emptyList();
    }

    @Override
    /**
     * 获取softwaredetail
     * @param softwareCode 字符串
     * @param source 参数
     * @return 返回值
    */
    public SoftSoftware fetchSoftwareDetail(SoftSource source, String softwareCode) {
        return null;
    }

    @Override
    /**
     * Install
     * @param installPath 字符串
     * @param software 参数
     * @param source 参数
     * @return 返回值
    */
    public SoftwareSourceResult install(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    /**
     * Uninstall
     * @param installPath 字符串
     * @param software 参数
     * @param source 参数
     * @return 返回值
    */
    public SoftwareSourceResult uninstall(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    /**
     * 更新
     * @param installPath 字符串
     * @param software 参数
     * @param source 参数
     * @param targetVersion 字符串
     * @return 返回值
    */
    public SoftwareSourceResult update(SoftSource source, SoftSoftware software, String targetVersion, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    /**
     * 获取latest版本
     * @param softwareCode 字符串
     * @param source 参数
     * @return 返回值
    */
    public String getLatestVersion(SoftSource source, String softwareCode) {
        return null;
    }
}