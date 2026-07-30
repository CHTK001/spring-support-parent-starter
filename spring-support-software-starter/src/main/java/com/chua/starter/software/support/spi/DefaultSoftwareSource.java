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
 * @author CH
 * @since 2026/7/25
 */
@Slf4j
@Component
@ConditionalOnMissingBean(SoftwareSource.class)
public class DefaultSoftwareSource implements SoftwareSource {

    @Override
    public boolean support(SoftSource source) {
        return false;
    }

    @Override
    public List<SoftSoftware> fetchSoftwareList(SoftSource source) {
        return Collections.emptyList();
    }

    @Override
    public SoftSoftware fetchSoftwareDetail(SoftSource source, String softwareCode) {
        return null;
    }

    @Override
    public SoftwareSourceResult install(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    public SoftwareSourceResult uninstall(SoftSource source, SoftSoftware software, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    public SoftwareSourceResult update(SoftSource source, SoftSoftware software, String targetVersion, String installPath) {
        return SoftwareSourceResult.fail("不支持的软件源类型");
    }

    @Override
    public String getLatestVersion(SoftSource source, String softwareCode) {
        return null;
    }
}