package com.chua.starter.software.support.spi.local;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.spi.SoftwareSource;
import com.chua.starter.software.support.spi.SoftwareSourceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地软件源实现 - 根据当前操作系统选择对应的采集器
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@Component
public class LocalSoftwareSource implements SoftwareSource {

    /** Collectors */
    private final List<LocalSoftwareCollector> collectors;

    @Autowired
    /**
    * 创建 本地software源 实例
    * @param collectors collectors
    */
    public LocalSoftwareSource(List<LocalSoftwareCollector> collectors) {
        this.collectors = collectors;
    }

    @Override
    /** 支持 */
    public boolean support(SoftSource source) {
        return source != null && source.getSourceType() == 3;
    }

    @Override
    /** 获取software列表 */
    public List<SoftSoftware> fetchSoftwareList(SoftSource source) {
        if (!support(source)) {
            return Collections.emptyList();
        }
        for (LocalSoftwareCollector collector : collectors) {
            if (collector.support()) {
                return collector.collect();
            }
        }
        return Collections.emptyList();
    }

    @Override
    /** 获取softwaredetail */
    public SoftSoftware fetchSoftwareDetail(SoftSource source, String softwareCode) {
        // 本地软件源暂不支持通过编码获取详情（可后续实现）
        return null;
    }

    @Override
    /** Install */
    public SoftwareSourceResult install(SoftSource source, SoftSoftware software, String installPath) {
        // 本地软件已经安装，视为成功
        return SoftwareSourceResult.success("本地软件已存在", installPath);
    }

    @Override
    /** Uninstall */
    public SoftwareSourceResult uninstall(SoftSource source, SoftSoftware software, String installPath) {
        // 本地软件卸实际指从管理列表移除，实际卸载交由系统处理
        return SoftwareSourceResult.success("标记为待删除", installPath);
    }

    @Override
    /** 更新 */
    public SoftwareSourceResult update(SoftSource source, SoftSoftware software, String targetVersion, String installPath) {
        // 本地软件版本更新由系统负责，这里仅标记为最新
        software.setSoftwareLatestVersion(targetVersion);
        return SoftwareSourceResult.success("版本已更新", installPath);
    }

    @Override
    /** 获取latest版本 */
    public String getLatestVersion(SoftSource source, String softwareCode) {
        // 返回当前版本作为最新版本
        return null;
    }
}
