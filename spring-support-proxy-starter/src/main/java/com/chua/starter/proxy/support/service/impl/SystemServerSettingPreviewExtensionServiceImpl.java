package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.proxy.support.entity.SystemServerSettingPreviewExtension;
import com.chua.starter.proxy.support.mapper.SystemServerSettingPreviewExtensionMapper;
import com.chua.starter.proxy.support.pojo.PreviewExtensionConfigVo;
import com.chua.starter.proxy.support.service.server.SystemServerSettingPreviewExtensionService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 预览扩展名配置Service实现类
 *
 * @author CH
 * @since 2024/12/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingPreviewExtensionServiceImpl
        extends ServiceImpl<SystemServerSettingPreviewExtensionMapper, SystemServerSettingPreviewExtension>
        implements SystemServerSettingPreviewExtensionService {

    private final SystemServerSettingService systemServerSettingService;

    @Override
    public PreviewExtensionConfigVo getByServerIdForPreviewExtension(Integer serverId) {
        log.debug("获取服务器[{}]的预览扩展名配置", serverId);
        
        SystemServerSettingPreviewExtension entity = getOne(
                new LambdaQueryWrapper<SystemServerSettingPreviewExtension>()
                        .eq(SystemServerSettingPreviewExtension::getPreviewExtensionServerId, serverId)
        );

        PreviewExtensionConfigVo vo = new PreviewExtensionConfigVo();
        vo.setServerId(serverId);

        if (entity == null) {
            // 返回默认配置
            vo.setDisabledExtensions(Collections.emptyList());
            vo.setAllowedExtensions(Collections.emptyList());
            vo.setWhitelistMode(false);
        } else {
            vo.setDisabledExtensions(parseExtensions(entity.getPreviewExtensionDisabled()));
            vo.setAllowedExtensions(parseExtensions(entity.getPreviewExtensionAllowed()));
            vo.setWhitelistMode(Boolean.TRUE.equals(entity.getPreviewExtensionWhitelistMode()));
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveConfigForPreviewExtension(PreviewExtensionConfigVo configVo) {
        log.info("保存服务器[{}]的预览扩展名配置", configVo.getServerId());

        try {
            SystemServerSettingPreviewExtension entity = getOne(
                    new LambdaQueryWrapper<SystemServerSettingPreviewExtension>()
                            .eq(SystemServerSettingPreviewExtension::getPreviewExtensionServerId, configVo.getServerId())
            );

            if (entity == null) {
                entity = new SystemServerSettingPreviewExtension();
                entity.setPreviewExtensionServerId(configVo.getServerId());
            }

            entity.setPreviewExtensionDisabled(joinExtensions(configVo.getDisabledExtensions()));
            entity.setPreviewExtensionAllowed(joinExtensions(configVo.getAllowedExtensions()));
            entity.setPreviewExtensionWhitelistMode(configVo.getWhitelistMode());

            boolean success = saveOrUpdate(entity);

            if (success) {
                // 热更新到运行中的服务器
                applyToRunningServer(configVo.getServerId(), configVo);
            }

            return ReturnResult.ok(success);
        } catch (Exception e) {
            log.error("保存预览扩展名配置失败", e);
            return ReturnResult.illegal("保存配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> addDisabledExtensionsForPreviewExtension(Integer serverId, List<String> extensions) {
        log.info("为服务器[{}]添加禁用扩展名: {}", serverId, extensions);

        try {
            PreviewExtensionConfigVo config = getByServerIdForPreviewExtension(serverId);
            Set<String> disabledSet = new LinkedHashSet<>(config.getDisabledExtensions());
            
            for (String ext : extensions) {
                String normalized = normalizeExtension(ext);
                if (StringUtils.hasText(normalized)) {
                    disabledSet.add(normalized);
                }
            }
            
            config.setDisabledExtensions(new ArrayList<>(disabledSet));
            return saveConfigForPreviewExtension(config);
        } catch (Exception e) {
            log.error("添加禁用扩展名失败", e);
            return ReturnResult.illegal("添加失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> removeDisabledExtensionsForPreviewExtension(Integer serverId, List<String> extensions) {
        log.info("为服务器[{}]移除禁用扩展名: {}", serverId, extensions);

        try {
            PreviewExtensionConfigVo config = getByServerIdForPreviewExtension(serverId);
            Set<String> toRemove = extensions.stream()
                    .map(this::normalizeExtension)
                    .collect(Collectors.toSet());
            
            List<String> remaining = config.getDisabledExtensions().stream()
                    .filter(ext -> !toRemove.contains(ext))
                    .collect(Collectors.toList());
            
            config.setDisabledExtensions(remaining);
            return saveConfigForPreviewExtension(config);
        } catch (Exception e) {
            log.error("移除禁用扩展名失败", e);
            return ReturnResult.illegal("移除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> addAllowedExtensionsForPreviewExtension(Integer serverId, List<String> extensions) {
        log.info("为服务器[{}]添加允许扩展名: {}", serverId, extensions);

        try {
            PreviewExtensionConfigVo config = getByServerIdForPreviewExtension(serverId);
            Set<String> allowedSet = new LinkedHashSet<>(config.getAllowedExtensions());
            
            for (String ext : extensions) {
                String normalized = normalizeExtension(ext);
                if (StringUtils.hasText(normalized)) {
                    allowedSet.add(normalized);
                }
            }
            
            config.setAllowedExtensions(new ArrayList<>(allowedSet));
            return saveConfigForPreviewExtension(config);
        } catch (Exception e) {
            log.error("添加允许扩展名失败", e);
            return ReturnResult.illegal("添加失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> removeAllowedExtensionsForPreviewExtension(Integer serverId, List<String> extensions) {
        log.info("为服务器[{}]移除允许扩展名: {}", serverId, extensions);

        try {
            PreviewExtensionConfigVo config = getByServerIdForPreviewExtension(serverId);
            Set<String> toRemove = extensions.stream()
                    .map(this::normalizeExtension)
                    .collect(Collectors.toSet());
            
            List<String> remaining = config.getAllowedExtensions().stream()
                    .filter(ext -> !toRemove.contains(ext))
                    .collect(Collectors.toList());
            
            config.setAllowedExtensions(remaining);
            return saveConfigForPreviewExtension(config);
        } catch (Exception e) {
            log.error("移除允许扩展名失败", e);
            return ReturnResult.illegal("移除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> setPreviewModeForPreviewExtension(Integer serverId, Boolean whitelistMode) {
        log.info("设置服务器[{}]的预览模式: {}", serverId, whitelistMode ? "白名单" : "黑名单");

        try {
            PreviewExtensionConfigVo config = getByServerIdForPreviewExtension(serverId);
            config.setWhitelistMode(whitelistMode);
            return saveConfigForPreviewExtension(config);
        } catch (Exception e) {
            log.error("设置预览模式失败", e);
            return ReturnResult.illegal("设置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> clearConfigForPreviewExtension(Integer serverId) {
        log.info("清空服务器[{}]的预览扩展名配置", serverId);

        try {
            boolean success = remove(
                    new LambdaQueryWrapper<SystemServerSettingPreviewExtension>()
                            .eq(SystemServerSettingPreviewExtension::getPreviewExtensionServerId, serverId)
            );

            if (success) {
                // 热更新：清空运行中服务器的配置
                PreviewExtensionConfigVo emptyConfig = new PreviewExtensionConfigVo();
                emptyConfig.setServerId(serverId);
                emptyConfig.setDisabledExtensions(Collections.emptyList());
                emptyConfig.setAllowedExtensions(Collections.emptyList());
                emptyConfig.setWhitelistMode(false);
                applyToRunningServer(serverId, emptyConfig);
            }

            return ReturnResult.ok(success);
        } catch (Exception e) {
            log.error("清空配置失败", e);
            return ReturnResult.illegal("清空失败: " + e.getMessage());
        }
    }

    /**
     * 解析扩展名字符串为列表
     *
     * @param extensions 逗号分隔的扩展名字符串
     * @return 扩展名列表
     */
    private List<String> parseExtensions(String extensions) {
        if (!StringUtils.hasText(extensions)) {
            return Collections.emptyList();
        }
        return Arrays.stream(extensions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 将扩展名列表合并为字符串
     *
     * @param extensions 扩展名列表
     * @return 逗号分隔的字符串
     */
    private String joinExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return "";
        }
        return extensions.stream()
                .map(this::normalizeExtension)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(","));
    }

    /**
     * 标准化扩展名（转小写、去除点号）
     *
     * @param extension 扩展名
     * @return 标准化后的扩展名
     */
    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "";
        }
        return extension.trim().toLowerCase().replaceFirst("^\\.", "");
    }

    /**
     * 应用配置到运行中的服务器
     *
     * @param serverId 服务器ID
     * @param config   配置
     */
    private void applyToRunningServer(Integer serverId, PreviewExtensionConfigVo config) {
        try {
            log.debug("热更新预览扩展名配置到服务器[{}]", serverId);
            systemServerSettingService.applyPreviewExtensionConfigForSetting(serverId, config);
        } catch (Exception e) {
            log.warn("热更新预览扩展名配置失败，配置已保存但未生效: {}", e.getMessage());
        }
    }
}




