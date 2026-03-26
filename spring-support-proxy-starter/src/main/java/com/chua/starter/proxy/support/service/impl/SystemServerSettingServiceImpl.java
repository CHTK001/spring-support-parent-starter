package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.text.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.filter.ViewServletFilter;
import com.chua.common.support.network.protocol.viewer.ViewerManager;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.network.protocol.viewer.Viewer;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.spi.SpiOption;
import com.chua.common.support.core.spi.definition.ServiceDefinition;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.entity.SystemServerSettingItem;
import com.chua.starter.proxy.support.mapper.SystemServerSettingMapper;
import com.chua.starter.proxy.support.pojo.PreviewExtensionConfigVo;
import com.chua.starter.proxy.support.pojo.ServerSettingOrderRequest;
import com.chua.starter.proxy.support.pojo.ViewerConfigVO;
import com.chua.starter.proxy.support.pojo.ViewerInfoVO;
import com.chua.starter.proxy.support.service.server.SystemServerService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统服务器配置表服务实现类
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerSettingServiceImpl extends ServiceImpl<SystemServerSettingMapper, SystemServerSetting>
        implements SystemServerSettingService {

    private final SystemServerService systemServerService;
    private final SystemServerSettingItemService systemServerSettingItemService;

    // 重写基础方法以支持加密解密
    @Override
    public boolean save(SystemServerSetting entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(SystemServerSetting entity) {
        if (entity != null) {
            encryptSensitiveData(entity);
        }
        return super.updateById(entity);
    }

    @Override
    public SystemServerSetting getById(Serializable id) {
        SystemServerSetting entity = super.getById(id);
        if (entity != null) {
            decryptSensitiveData(entity);
        }
        return entity;
    }

    @Override
    public List<SystemServerSetting> list() {
        List<SystemServerSetting> entities = super.list();
        if (entities != null) {
            entities.forEach(this::decryptSensitiveData);
        }
        return entities;
    }

    /**
     * 加密敏感数据
     */
    private void encryptSensitiveData(SystemServerSetting entity) {
        if (entity == null) {
            return;
        }
        
        try {
            // 使用SystemServerServiceImpl中的加密方法
            if (systemServerService instanceof SystemServerServiceImpl serverServiceImpl) {
                serverServiceImpl.encryptSettingPasswords(entity);
            }
        } catch (Exception e) {
            log.error("加密SystemServerSetting敏感数据失败", e);
        }
    }

    /**
     * 解密敏感数据
     */
    private void decryptSensitiveData(SystemServerSetting entity) {
        if (entity == null) {
            return;
        }
        
        try {
            // 使用SystemServerServiceImpl中的解密方法
            if (systemServerService instanceof SystemServerServiceImpl serverServiceImpl) {
                serverServiceImpl.decryptSettingPasswords(entity);
            }
        } catch (Exception e) {
            log.error("解密SystemServerSetting敏感数据失败", e);
        }
    }

    @Override
    public IPage<SystemServerSetting> pageFor(Page<SystemServerSetting> page, SystemServerSetting entity) {
        return baseMapper.pageFor(page, entity);
    }

    @Override
    public ReturnResult<List<SystemServerSetting>> getByServerId(Integer serverId) {
        try {
            List<SystemServerSetting> settings = baseMapper.selectByServerId(serverId);
            log.info("获取服务器配置成功: serverId={}, 配置数量={}", serverId, settings.size());
            return ReturnResult.ok(settings);
        } catch (Exception e) {
            log.error("获取服务器配置失败: serverId={}", serverId, e);
            return ReturnResult.error("获取服务器配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SpiOption>> getAvailableServletFilterObjects(Integer serverId) {
        try {
            // 通过SPI机制获取可用的ServletFilter
            ServiceProvider<ServletFilter> serviceProvider = ServiceProvider.of(ServletFilter.class);
            List<SpiOption> options = serviceProvider.options();

            log.info("获取可用ServletFilter成功，共{}个", options.size());
            return ReturnResult.ok(options);
        } catch (Exception e) {
            log.error("获取可用ServletFilter失败", e);
            return ReturnResult.error("获取ServletFilter失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SystemServerSetting>> getServerInstalledFilters(Integer serverId) {
        try {
            List<SystemServerSetting> settings = baseMapper.selectByServerId(serverId);
            log.info("获取服务器已安装ServletFilter成功: serverId={}, 数量={}", serverId, settings.size());
            return ReturnResult.ok(settings);
        } catch (Exception e) {
            log.error("获取服务器已安装ServletFilter失败: serverId={}", serverId, e);
            return ReturnResult.error("获取已安装Filter失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<SystemServerSetting> installServletFilter(Integer serverId, String filterType) {
        try {
            // 检查是否已安装相同类型的Filter
            SystemServerSetting existingSetting = baseMapper.selectByServerIdAndType(serverId, filterType);
            if (existingSetting != null) {
                return ReturnResult.error("该类型的ServletFilter已安装");
            }

            // 获取最大排序值
            Integer maxOrder = baseMapper.getMaxOrderByServerId(serverId);
            ServiceProvider<ServletFilter> servletFilterServiceProvider = ServiceProvider.of(ServletFilter.class);
            ServiceDefinition definition = servletFilterServiceProvider.getDefinition(filterType);
            // 创建配置记录
            SystemServerSetting setting = new SystemServerSetting();
            setting.setSystemServerSettingServerId(serverId);
            setting.setSystemServerSettingName(definition.getDescribe());
            setting.setSystemServerSettingType(filterType);
            setting.setSystemServerSettingDescription(definition.getDescribe());
            setting.setSystemServerSettingEnabled(true);
            setting.setSystemServerSettingOrder(maxOrder + 1);

            save(setting);

            // 创建默认配置项
            createDefaultConfigItems(setting.getSystemServerSettingId(), filterType);

            // 应用配置到运行中的服务器
            applyConfigToRunningServer(serverId);

            log.info("安装ServletFilter成功: serverId={}, filterType={}, filterName={}", serverId, filterType, definition.getDescribe());
            return ReturnResult.ok(setting);
        } catch (Exception e) {
            log.error("安装ServletFilter失败: serverId={}, filterType={}", serverId, filterType, e);
            return ReturnResult.error("安装ServletFilter失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> uninstallServletFilter(Integer settingId) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            Integer serverId = setting.getSystemServerSettingServerId();

            // 删除配置项
            systemServerSettingItemService.deleteBySettingId(settingId);

            // 删除配置
            removeById(settingId);

            // 应用配置到运行中的服务器
            applyDeleteConfigToRunningServer(serverId);

            log.info("卸载ServletFilter成功: settingId={}, filterType={}", settingId, setting.getSystemServerSettingType());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("卸载ServletFilter失败: settingId={}", settingId, e);
            return ReturnResult.error("卸载ServletFilter失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> enableServletFilter(Integer settingId) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            baseMapper.updateEnabled(settingId, true);

            // 应用配置到运行中的服务器
            applyConfigToRunningServer(setting.getSystemServerSettingServerId());

            log.info("启用ServletFilter成功: settingId={}", settingId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("启用ServletFilter失败: settingId={}", settingId, e);
            return ReturnResult.error("启用ServletFilter失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> disableServletFilter(Integer settingId) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            baseMapper.updateEnabled(settingId, false);

            // 应用配置到运行中的服务器
            applyConfigToRunningServer(setting.getSystemServerSettingServerId());

            log.info("禁用ServletFilter成功: settingId={}", settingId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("禁用ServletFilter失败: settingId={}", settingId, e);
            return ReturnResult.error("禁用ServletFilter失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> updateServletFilterOrder(List<Map<String, Integer>> orderUpdates) {
        try {
            for (Map<String, Integer> update : orderUpdates) {
                Integer settingId = update.get("settingId");
                Integer order = update.get("order");
                baseMapper.updateOrder(settingId, order);
            }

            // 获取第一个配置的服务器ID来应用配置
            if (!orderUpdates.isEmpty()) {
                Integer firstSettingId = orderUpdates.get(0).get("settingId");
                SystemServerSetting setting = getById(firstSettingId);
                if (setting != null) {
                    applyConfigToRunningServer(setting.getSystemServerSettingServerId());
                }
            }

            log.info("更新ServletFilter排序成功，更新数量: {}", orderUpdates.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("更新ServletFilter排序失败", e);
            return ReturnResult.error("更新排序失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> getServletFilterConfigItems(String filterType) {
        try {
            ServiceProvider serviceProvider = ServiceProvider.of("ServletFilter");
            Object filterInstance = serviceProvider.getNewExtension(filterType);
            List<Map<String, Object>> configItems = getFilterConfigItems(filterInstance);

            log.info("获取ServletFilter配置项成功: filterType={}, 配置项数量={}", filterType, configItems.size());
            return ReturnResult.ok(configItems);
        } catch (Exception e) {
            log.error("获取ServletFilter配置项失败: filterType={}", filterType, e);
            return ReturnResult.error("获取配置项失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> updateServletFilterConfig(Integer settingId, Map<String, Object> config) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            // 更新配置项
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                SystemServerSettingItem item = new SystemServerSettingItem();
                item.setSystemServerSettingItemSettingId(settingId);
                item.setSystemServerSettingItemName(entry.getKey());
                item.setSystemServerSettingItemValue(entry.getValue().toString());
                item.setSystemServerSettingItemOrder(1);
                systemServerSettingItemService.remove(
                        Wrappers.<SystemServerSettingItem>lambdaQuery()
                                .eq(SystemServerSettingItem::getSystemServerSettingItemSettingId, settingId)
                                .eq(SystemServerSettingItem::getSystemServerSettingItemName, entry.getKey())
                );
                systemServerSettingItemService.save(item);
            }

            // 应用配置到运行中的服务器
            applyConfigToRunningServer(setting.getSystemServerSettingServerId());

            log.info("更新ServletFilter配置成功: settingId={}", settingId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("更新ServletFilter配置失败: settingId={}", settingId, e);
            return ReturnResult.error("更新配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> applyConfigToRunningServer(Integer serverId) {
        try {
            return systemServerService.applyConfigChanges(serverId);
        } catch (Exception e) {
            log.error("应用配置到运行中的服务器失败: serverId={}", serverId, e);
            return ReturnResult.error("应用配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> applyDeleteConfigToRunningServer(Integer serverId) {
        try {
            return systemServerService.applyDeleteConfigToRunningServer(serverId);
        } catch (Exception e) {
            log.error("应用配置到运行中的服务器失败: serverId={}", serverId, e);
            return ReturnResult.error("应用配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> cloneServerSettings(Integer sourceServerId, Integer targetServerId) {
        try {
            List<SystemServerSetting> sourceSettings = baseMapper.selectByServerId(sourceServerId);

            for (SystemServerSetting sourceSetting : sourceSettings) {
                // 创建新配置
                SystemServerSetting newSetting = new SystemServerSetting();
                BeanUtils.copyProperties(sourceSetting, newSetting);
                newSetting.setSystemServerSettingId(null);
                newSetting.setSystemServerSettingServerId(targetServerId);
                save(newSetting);

                // 克隆配置项
                systemServerSettingItemService.batchSaveItems(newSetting.getSystemServerSettingId(),
                        systemServerSettingItemService.getBySettingId(sourceSetting.getSystemServerSettingId())
                                .getData());
            }

            log.info("克隆服务器配置成功: sourceServerId={}, targetServerId={}, 配置数量={}",
                    sourceServerId, targetServerId, sourceSettings.size());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("克隆服务器配置失败: sourceServerId={}, targetServerId={}", sourceServerId, targetServerId, e);
            return ReturnResult.error("克隆配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<SystemServerSetting>> getByServerIdAndEnabled(Integer serverId, Boolean enabled) {
        try {
            List<SystemServerSetting> settings = baseMapper.selectByServerIdAndEnabled(serverId, enabled);
            return ReturnResult.ok(settings);
        } catch (Exception e) {
            log.error("根据服务器ID和启用状态查询配置失败: serverId={}, enabled={}", serverId, enabled, e);
            return ReturnResult.error("查询配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> batchUpdateEnabled(List<Integer> settingIds, Boolean enabled) {
        try {
            for (Integer settingId : settingIds) {
                baseMapper.updateEnabled(settingId, enabled);
            }

            log.info("批量更新ServletFilter启用状态成功: settingIds={}, enabled={}", settingIds, enabled);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("批量更新ServletFilter启用状态失败: settingIds={}, enabled={}", settingIds, enabled, e);
            return ReturnResult.error("批量更新失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> orderServerInstalledFilters(List<ServerSettingOrderRequest> settingOrders) {
        try {
            List<SystemServerSetting> systemServerSettings = this.listByIds(settingOrders.stream().map(ServerSettingOrderRequest::getId).toList());
            int index = 1;
            for (ServerSettingOrderRequest settingOrder : settingOrders) {
                SystemServerSetting systemServerSetting = systemServerSettings.stream()
                        .filter(item -> item.getSystemServerSettingId().equals(settingOrder.getId())).findFirst().get();
                systemServerSetting.setSystemServerSettingOrder(index++);
            }
            this.updateBatchById(systemServerSettings);
        } catch (Exception e) {
            log.error("排序ServletFilter失败: settingOrders={}", settingOrders, e);
            return ReturnResult.error("排序失败: " + e.getMessage());
        }
        return ReturnResult.ok(true);
    }

    @Override
    public ReturnResult<Map<String, Object>> getServletFilterConfig(Integer id) {
        List<SystemServerSettingItem> list = systemServerSettingItemService.list(
                Wrappers.lambdaQuery(SystemServerSettingItem.class)
                        .eq(SystemServerSettingItem::getSystemServerSettingItemSettingId, id)
        );
        return ReturnResult.ok(list.stream().collect(Collectors.toMap(SystemServerSettingItem::getSystemServerSettingItemName, SystemServerSettingItem::getSystemServerSettingItemValue)));
    }

    /**
     * 获取Filter显示名称
     */
    private String getFilterDisplayName(Object filterInstance) {
        try {
            return (String) filterInstance.getClass().getMethod("getDisplayName").invoke(filterInstance);
        } catch (Exception e) {
            return filterInstance.getClass().getSimpleName();
        }
    }

    /**
     * 获取Filter描述
     */
    private String getFilterDescription(Object filterInstance) {
        try {
            return (String) filterInstance.getClass().getMethod("getDescription").invoke(filterInstance);
        } catch (Exception e) {
            return "无描述";
        }
    }

    /**
     * 获取Filter版本
     */
    private String getFilterVersion(Object filterInstance) {
        try {
            return (String) filterInstance.getClass().getMethod("getVersion").invoke(filterInstance);
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    /**
     * 获取Filter配置项
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getFilterConfigItems(Object filterInstance) {
        try {
            return (List<Map<String, Object>>) filterInstance.getClass().getMethod("getConfigItems")
                    .invoke(filterInstance);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 创建默认配置项
     */
    private void createDefaultConfigItems(Integer settingId, String filterType) {
        try {
            ReturnResult<List<Map<String, Object>>> configItemsResult = getServletFilterConfigItems(filterType);
            if (configItemsResult.isOk() && configItemsResult.getData() != null) {
                // 这里需要调用SystemServerSettingItemService来创建配置项
                // 具体实现在SystemServerSettingItemServiceImpl中
            }
        } catch (Exception e) {
        }
    }

    @Override
    public ReturnResult<SystemServerSetting> getHttpsConfigByServerId(Integer serverId) {
        SystemServerSetting setting = baseMapper.selectByServerIdAndType(serverId, "HTTPS_CONFIG");
        if (setting == null) {
            setting = new SystemServerSetting();
            setting.setSystemServerSettingServerId(serverId);
            setting.setSystemServerSettingType("HTTPS_CONFIG");
            setting.setSystemServerSettingEnabled(false); // 配置项本身先禁用，避免无处理器时报错
            setting.setSystemServerSettingName("HTTPS配置");
            Integer maxOrder = baseMapper.getMaxOrderByServerId(serverId);
            setting.setSystemServerSettingOrder(maxOrder == null ? 1 : maxOrder + 1);
            save(setting);
        }
        return ReturnResult.ok(setting);
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> saveHttpsConfig(Integer serverId, Boolean enabled, SystemServerSetting.HttpsCertType certType, byte[] pemCert, byte[] pemKey, String keyPassword, byte[] keystore, String keystorePassword) {
        try {
            SystemServerSetting setting = baseMapper.selectByServerIdAndType(serverId, "HTTPS_CONFIG");
            if (setting == null) {
                setting = new SystemServerSetting();
                setting.setSystemServerSettingServerId(serverId);
                setting.setSystemServerSettingType("HTTPS_CONFIG");
                setting.setSystemServerSettingEnabled(true);
                setting.setSystemServerSettingName("HTTPS配置");
                Integer maxOrder = baseMapper.getMaxOrderByServerId(serverId);
                setting.setSystemServerSettingOrder(maxOrder == null ? 1 : maxOrder + 1);
                save(setting);
            }
            // 同步启用状态到配置开关（是否加载该配置作为Filter）
            setting.setSystemServerSettingEnabled(Boolean.TRUE.equals(enabled));
            // 业务内部HTTPS启用标记
            setting.setSystemServerSettingHttpsEnabled(Boolean.TRUE.equals(enabled));
            // 证书类型直接赋值（枚举）
            setting.setSystemServerSettingHttpsCertType(certType == null ? SystemServerSetting.HttpsCertType.PEM : certType);
            if (pemCert != null && pemCert.length > 0) setting.setSystemServerSettingHttpsPemCert(pemCert);
            if (pemKey != null && pemKey.length > 0) setting.setSystemServerSettingHttpsPemKey(pemKey);
            setting.setSystemServerSettingHttpsPemKeyPassword(keyPassword);
            if (keystore != null && keystore.length > 0) setting.setSystemServerSettingHttpsKeystore(keystore);
            setting.setSystemServerSettingHttpsKeystorePassword(keystorePassword);
            updateById(setting);
            // 热应用
            applyConfigToRunningServer(serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存HTTPS证书配置失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> getFilterConfig(Integer settingId) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            // 从systemServerSettingConfig字段解析JSON配置
            String configJson = setting.getSystemServerSettingConfig();
            Map<String, Object> config = new HashMap<>();

            if (configJson != null && !configJson.trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    config = objectMapper.readValue(configJson, Map.class);
                } catch (Exception e) {
                    log.warn("解析配置JSON失败，使用空配置: settingId={}", settingId, e);
                }
            }

            log.info("获取ServletFilter配置成功: settingId={}", settingId);
            return ReturnResult.ok(config);
        } catch (Exception e) {
            log.error("获取ServletFilter配置失败: settingId={}", settingId, e);
            return ReturnResult.error("获取配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> saveFilterConfig(Integer settingId, Map<String, Object> config) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            // 将配置转换为JSON字符串保存到systemServerSettingConfig字段
            ObjectMapper objectMapper = new ObjectMapper();
            String configJson = objectMapper.writeValueAsString(config);

            setting.setSystemServerSettingConfig(configJson);
            updateById(setting);

            // 应用配置到运行中的服务器
            applyConfigToRunningServer(setting.getSystemServerSettingServerId());

            log.info("保存ServletFilter配置成功: settingId={}", settingId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存ServletFilter配置失败: settingId={}", settingId, e);
            return ReturnResult.error("保存配置失败: " + e.getMessage());
        }
    }

    @Override
    public void applyPreviewExtensionConfigForSetting(Integer serverId, PreviewExtensionConfigVo config) {
        log.info("应用预览扩展名配置到服务器[{}]: whitelistMode={}, disabled={}, allowed={}",
                serverId, config.getWhitelistMode(),
                config.getDisabledExtensions(), config.getAllowedExtensions());

        try {
            // 获取运行中的服务器实例
            ProtocolServer runningServer = systemServerService.getRunningServerInstance(serverId);
            if (runningServer == null) {
                log.warn("服务器未运行，配置将在下次启动时生效: serverId={}", serverId);
                return;
            }

            // 查找 ViewServletFilter 并更新配置
            applyViewFilterConfig(runningServer, config);

            log.info("预览扩展名配置已成功应用到服务器[{}]", serverId);
        } catch (Exception e) {
            log.error("应用预览扩展名配置失败: serverId={}", serverId, e);
        }
    }

    /**
     * 应用配置到 ViewServletFilter
     *
     * @param runningServer 运行中的服务器实例
     * @param config        预览扩展名配置
     */
    private void applyViewFilterConfig(ProtocolServer runningServer, PreviewExtensionConfigVo config) {
        try {
            // 获取 ViewServletFilter
            var viewFilter = runningServer.getFilter(ViewServletFilter.class);
            if (viewFilter == null) {
                log.debug("ViewServletFilter 未安装，跳过配置更新");
                return;
            }

            // 清空并重新设置禁用扩展名
            viewFilter.clearDisabledExtensions();
            if (config.getDisabledExtensions() != null && !config.getDisabledExtensions().isEmpty()) {
                viewFilter.addDisabledExtensions(config.getDisabledExtensions().toArray(new String[0]));
            }

            // 清空并重新设置允许扩展名
            viewFilter.clearAllowedExtensions();
            if (config.getAllowedExtensions() != null && !config.getAllowedExtensions().isEmpty()) {
                viewFilter.addAllowedExtensions(config.getAllowedExtensions().toArray(new String[0]));
            }

            // 设置白名单模式
            viewFilter.setWhitelistMode(Boolean.TRUE.equals(config.getWhitelistMode()));

            log.debug("ViewServletFilter 配置已更新");
        } catch (Exception e) {
            log.warn("更新 ViewServletFilter 配置失败: {}", e.getMessage());
        }
    }

    // ==================== Viewer 视图查看器配置 ====================

    @Override
    public ReturnResult<List<ViewerInfoVO>> getViewerList(Integer settingId) {
        try {
            List<ViewerInfoVO> viewerList = new ArrayList<>();

            // 使用 ServiceProvider 获取所有 Viewer
            ServiceProvider<Viewer> provider = ServiceProvider.of(Viewer.class);

            // 获取已保存的禁用查看器列表
            ViewerConfigVO savedConfig = getViewerConfigFromDb(settingId);
            List<String> disabledViewers = savedConfig.getDisabledViewers() != null
                    ? savedConfig.getDisabledViewers()
                    : new ArrayList<>();

            // 收集所有查看器信息
            for (Viewer viewer : provider.collect(true)) {
                ViewerInfoVO viewerInfo = ViewerInfoVO.builder()
                        .name(viewer.getViewerName())
                        .description(viewer.getDescription())
                        .priority(viewer.getPriority())
                        .enabled(!disabledViewers.contains(viewer.getViewerName()))
                        .supportedContentTypes(viewer.getSupportedContentTypes())
                        .supportedExtensions(viewer.getSupportedExtensions())
                        .targetFormat(viewer.getTargetFormat())
                        .build();
                viewerList.add(viewerInfo);
            }

            // 按优先级排序
            viewerList.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

            log.debug("获取到 {} 个视图查看器", viewerList.size());
            return ReturnResult.ok(viewerList);
        } catch (Exception e) {
            log.error("获取视图查看器列表失败", e);
            return ReturnResult.error("获取视图查看器列表失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<ViewerConfigVO> getViewerConfig(Integer settingId) {
        try {
            ViewerConfigVO config = getViewerConfigFromDb(settingId);
            return ReturnResult.ok(config);
        } catch (Exception e) {
            log.error("获取视图查看器配置失败", e);
            return ReturnResult.error("获取视图查看器配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> saveViewerConfig(Integer settingId, ViewerConfigVO config) {
        try {
            SystemServerSetting setting = getById(settingId);
            if (setting == null) {
                return ReturnResult.error("配置不存在");
            }

            // 将配置转换为 JSON 保存
            String configJson = Json.toJson(config);
            setting.setSystemServerSettingConfig(configJson);
            updateById(setting);

            // 热应用配置到运行中的服务器
            applyViewerConfigToRunningServer(setting.getSystemServerSettingServerId(), config);

            log.info("视图查看器配置已保存并热应用: settingId={}", settingId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存视图查看器配置失败", e);
            return ReturnResult.error("保存视图查看器配置失败: " + e.getMessage());
        }
    }

    /**
     * 从数据库获取查看器配置
     *
     * @param settingId 配置ID
     * @return 查看器配置
     */
    private ViewerConfigVO getViewerConfigFromDb(Integer settingId) {
        SystemServerSetting setting = getById(settingId);
        if (setting == null || setting.getSystemServerSettingConfig() == null) {
            return new ViewerConfigVO();
        }
        try {
            return Json.fromJson(
                    setting.getSystemServerSettingConfig(),
                    ViewerConfigVO.class
            );
        } catch (Exception e) {
            log.warn("解析查看器配置失败: {}", e.getMessage());
            return new ViewerConfigVO();
        }
    }

    /**
     * 应用查看器配置到运行中的服务器
     *
     * @param serverId 服务器ID
     * @param config   查看器配置
     */
    private void applyViewerConfigToRunningServer(Integer serverId, ViewerConfigVO config) {
        try {
            var runningServer = systemServerService.getRunningServerInstance(serverId);
            if (runningServer == null) {
                log.debug("服务器未运行，跳过热应用: serverId={}", serverId);
                return;
            }

            // 获取 ViewServletFilter
            var viewFilter = runningServer.getFilter(ViewServletFilter.class);
            if (viewFilter == null) {
                log.debug("ViewServletFilter 未安装，跳过配置更新");
                return;
            }

            // 获取 ViewerManager
            var viewerManager = viewFilter.getViewerManager();
            if (viewerManager == null) {
                log.debug("ViewerManager 未初始化，跳过配置更新");
                return;
            }

            // 获取禁用的查看器列表
            var disabledViewers = config.getDisabledViewers() != null
                    ? config.getDisabledViewers()
                    : new ArrayList<String>();

            // 遍历所有查看器，设置启用/禁用状态
            for (var viewer : viewerManager.getAllViewers()) {
                var viewerName = viewer.getViewerName();
                var enabled = !disabledViewers.contains(viewerName);
                viewerManager.setViewerEnabled(viewerName, enabled);
            }

            log.info("视图查看器配置已热应用: serverId={}, 禁用查看器数={}", serverId, disabledViewers.size());
        } catch (Exception e) {
            log.warn("应用视图查看器配置失败: {}", e.getMessage());
        }
    }

}




