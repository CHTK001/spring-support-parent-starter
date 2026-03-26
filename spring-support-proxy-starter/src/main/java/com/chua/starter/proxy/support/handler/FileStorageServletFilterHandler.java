package com.chua.starter.proxy.support.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.image.WatermarkUtils;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.storage.oss.FileStorage;
import com.chua.common.support.storage.oss.setting.BucketSetting;
import com.chua.common.support.network.protocol.filter.FileStorageServletFilter;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.storage.FileStorageFactory;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.MapUtils;
import com.chua.common.support.core.utils.ObjectUtils;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.entity.SystemServerSettingFileStorage;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import com.chua.starter.proxy.support.service.server.SystemServerSettingFileStorageService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件存储过滤器
 *
 * @author CH
 * @since 2025/8/11 16:59
 */
@Slf4j
@Spi("fileStorage")
public class FileStorageServletFilterHandler implements ServletFilterHandler {


    final SystemServerSettingItemService systemServerSettingItemService;
    final SystemServerSettingFileStorageService systemServerSettingFileStorageService;

    public FileStorageServletFilterHandler() {
        this.systemServerSettingItemService = SpringBeanUtils.getBean(SystemServerSettingItemService.class);
        this.systemServerSettingFileStorageService = SpringBeanUtils.getBean(SystemServerSettingFileStorageService.class);
    }

    /**
     * 注册文件存储过滤器
     *
     * @param setting       设置
     * @param objectContext
     * @return ServletFilter
     */
    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        FileStorageServletFilter fileStorageServletFilter = new FileStorageServletFilter(getFileStorageSetting(setting));
        upgradeFileStorage(fileStorageServletFilter, setting);
        return fileStorageServletFilter;
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (filter instanceof FileStorageServletFilter fileStorageServletFilter) {
            upgradeGlobal(fileStorageServletFilter, setting);
            upgradeFileStorage(fileStorageServletFilter, setting);
        }
    }

    /**
     * 升级文件存储
     *
     * @param fileStorageServletFilter fileStorageServletFilter
     * @param setting                  setting
     */
    private void upgradeFileStorage(FileStorageServletFilter fileStorageServletFilter, SystemServerSetting setting) {
        List<SystemServerSettingFileStorage> list = systemServerSettingFileStorageService.list(
                Wrappers.<SystemServerSettingFileStorage>lambdaQuery()
                        .eq(SystemServerSettingFileStorage::getFileStorageServerId, setting.getSystemServerSettingServerId())
        );
        for (SystemServerSettingFileStorage systemServerSettingFileStorage : list) {
            Boolean fileStorageEnabled = systemServerSettingFileStorage.getFileStorageEnabled();
            String fileStorageBucket = systemServerSettingFileStorage.getFileStorageBucket();
            if (null == fileStorageEnabled || !fileStorageEnabled) {
                log.warn("文件存储配置未启用: {}", fileStorageBucket);
                continue;
            }
            if (fileStorageServletFilter.containsFileStorage(fileStorageBucket)) {
                fileStorageServletFilter.removeFileStorage(fileStorageBucket);
            }
            fileStorageServletFilter.addFileStorage(fileStorageBucket,
                    createFileStorage(systemServerSettingFileStorage));
        }
    }

    /**
     * 升级全局
     *
     * @param fileStorageServletFilter fileStorageServletFilter
     * @param setting                  setting
     */
    private void upgradeGlobal(FileStorageServletFilter fileStorageServletFilter, SystemServerSetting setting) {
        FileStorageFactory.FileStorageSetting fileStorageSetting = getFileStorageSetting(setting);
        fileStorageServletFilter.updateConfigurationObject(fileStorageSetting);
    }

    /**
     * 创建文件存储
     *
     * @param systemServerSettingFileStorage 文件存储
     * @return 文件存储
     */
    private FileStorage createFileStorage(SystemServerSettingFileStorage systemServerSettingFileStorage) {

        BucketSetting bucketSetting = BucketSetting.builder()
                .endpoint(systemServerSettingFileStorage.getFileStorageEndpoint())
                .bucket(systemServerSettingFileStorage.getFileStorageBucket())
                .accessKeyId(systemServerSettingFileStorage.getFileStorageAccessKey())
                .accessKeySecret(systemServerSettingFileStorage.getFileStorageSecretKey())
                .region(systemServerSettingFileStorage.getFileStorageRegion())
                .connectionTimeoutMills(ObjectUtils.defaultIfNull(systemServerSettingFileStorage.getFileStorageConnectionTimeout(), 5000L))
                .build();
        return ServiceProvider.of(FileStorage.class).getNewExtension(systemServerSettingFileStorage.getFileStorageType(), bucketSetting);
    }

    /**
     * 创建文件存储
     *
     * @param setting 设置
     * @return 文件存储
     */
    private FileStorageFactory.FileStorageSetting getFileStorageSetting(SystemServerSetting setting) {
        List<SystemServerSettingItem> list = systemServerSettingItemService.list(
                Wrappers.<SystemServerSettingItem>lambdaQuery().eq(SystemServerSettingItem::getSystemServerSettingItemSettingId, setting.getSystemServerSettingId())
        );

        Map<String, String> collect = list.stream().collect(Collectors.toMap(SystemServerSettingItem::getSystemServerSettingItemName, SystemServerSettingItem::getSystemServerSettingItemValue));
        FileStorageFactory.FileStorageSetting.FileStorageSettingBuilder builder = FileStorageFactory.FileStorageSetting.builder();
        builder.openSetting(MapUtils.getBoolean(collect, "openSetting", false));
        builder.openPlugin(MapUtils.getBoolean(collect, "openPlugin", false));
        builder.openRange(MapUtils.getBoolean(collect, "openRange", false));
        builder.openPreview(MapUtils.getBoolean(collect, "openPreview", false));
        builder.openDownload(MapUtils.getBoolean(collect, "openDownload", false));
        builder.openWebjars(MapUtils.getBoolean(collect, "openWebjars", true));
        builder.openRemoteFile(MapUtils.getBoolean(collect, "openRemoteFile", false));
        builder.openWatermark(MapUtils.getBoolean(collect, "openWatermark", false));
        builder.watermark(MapUtils.getString(collect, "watermark", ""));
        builder.watermarkColor(MapUtils.getString(collect, "watermarkColor", ""));
        builder.watermarkX(MapUtils.getIntValue(collect, "watermarkX", 0));
        builder.watermarkY(MapUtils.getIntValue(collect, "watermarkY", 0));
        builder.watermarkWay(WatermarkUtils.WatermarkWay.valueOf(MapUtils.getString(collect, "watermarkWay", "NORMAL").toUpperCase()));
        builder.plugins(MapUtils.getStringArray(collect, "plugins" ));
        builder.settings(MapUtils.getStringArray(collect, "settings"));
        return builder.build();
    }
}




