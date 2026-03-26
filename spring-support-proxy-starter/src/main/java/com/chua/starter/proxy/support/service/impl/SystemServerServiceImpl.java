package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.core.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.protocol.ProtocolSetting;
import com.chua.common.support.network.protocol.event.ServletEvent;
import com.chua.common.support.network.protocol.event.ServletListener;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.network.protocol.ServerSetting;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.spi.SpiOption;
import com.chua.starter.proxy.support.entity.SystemServer;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import com.chua.starter.proxy.support.filter.LoggingServletFilterProxy;
import com.chua.starter.proxy.support.handler.HotloadingDeleteServletFilterHandler;
import com.chua.starter.proxy.support.handler.HotloadingServletFilterHandler;
import com.chua.starter.proxy.support.handler.ServletFilterHandler;
import com.chua.starter.proxy.support.properties.ProxyManagementProperties;
import com.chua.starter.proxy.support.mapper.SystemServerMapper;
import com.chua.starter.proxy.support.mapper.SystemServerSettingAddressRateLimitMapper;
import com.chua.starter.proxy.support.mapper.SystemServerSettingIPRateLimitMapper;
import com.chua.starter.proxy.support.service.server.SystemServerService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingFileStorageService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingItemService;
import com.chua.starter.proxy.support.service.server.SystemServerSettingService;
import com.chua.starter.service.utils.ProtocolServerSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

/**
 * 系统服务器配置服务实现类
 *
 * @author CH
 * @since 2025/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerServiceImpl extends ServiceImpl<SystemServerMapper, SystemServer> implements SystemServerService, InitializingBean {

    /**
     * AES加密算法
     */
    private static final String ALGORITHM = "AES";
    
    /**
     * AES加密密钥（实际项目中应该从配置文件或环境变量中获取）
     */
    private static final String SECRET_KEY = "MySecretKey12345"; // 16字节密钥

    /**
     * 存储已启动的服务器实例
     * Key: 服务器ID, Value: 服务器实例对象
     */
    public static final Map<Integer, ProtocolServer> runningServerInstances = new ConcurrentHashMap<>();

    private final ProxyManagementProperties proxyManagementProperties;
    final ExecutorService executorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("server-initial-execute").factory());
    @Autowired
    @Lazy
    private SystemServerSettingService systemServerSettingService;
    @Autowired
    private SystemServerSettingIPRateLimitMapper systemServerSettingIPRateLimitMapper;
    @Autowired
    private SystemServerSettingAddressRateLimitMapper systemServerSettingAddressRateLimitMapper;
    @Autowired
    private SystemServerSettingItemService systemServerSettingItemService;
    private SystemServerSettingFileStorageService systemServerSettingFileStorageService;

    @Override
    public IPage<SystemServer> pageFor(Page<SystemServer> page, SystemServer entity) {
        return baseMapper.pageFor(page, entity);
    }

    @Override
    public ReturnResult<List<SpiOption>> getAvailableServerTypes() {
        try {
            // 通过SPI机制获取可用的服务器类型
            ServiceProvider<ProtocolServer> serviceProvider = ServiceProvider.of(ProtocolServer.class);
            List<SpiOption> options = serviceProvider.options();
            log.info("获取可用服务器类型成功，共{}种类型", options.size());
            return ReturnResult.ok(options);
        } catch (Exception e) {
            log.error("获取可用服务器类型失败", e);
            return ReturnResult.error("获取服务器类型失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> statistics = baseMapper.getStatistics();
            log.info("获取服务器统计信息成功: {}", statistics);
            return ReturnResult.ok(statistics);
        } catch (Exception e) {
            log.error("获取服务器统计信息失败", e);
            return ReturnResult.error("获取统计信息失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> startServer(Integer serverId) {
        try {
            SystemServer server = getById(serverId);
            if (server == null) {
                return ReturnResult.error("服务器不存在");
            }

            if (SystemServer.SystemServerStatus.RUNNING.equals(server.getSystemServerStatus())) {
                return ReturnResult.error("服务器已在运行中");
            }

            // 检查端口是否被占用
            if (isPortInUse(server.getSystemServerPort())) {
                return ReturnResult.error("端口 " + server.getSystemServerPort() + " 已被占用");
            }

            // 更新状态为启动中
            server.setSystemServerStatus(SystemServer.SystemServerStatus.STARTING);
            updateById(server);

            try {
                // 通过SPI机制创建服务器实例
                ProtocolServer serverInstance = createServerInstance(server);

                // 应用配置
                applyServerConfiguration(serverId, serverInstance, Action.CREATE);

                // 启动服务器
                startServerInstance(serverInstance);

                // 存储服务器实例
                runningServerInstances.put(serverId, serverInstance);

                // 更新状态为运行中
                server.setSystemServerStatus(SystemServer.SystemServerStatus.RUNNING);
                updateById(server);

                log.info("服务器启动成功: {} (端口: {})", server.getSystemServerName(), server.getSystemServerPort());
                return ReturnResult.ok(true);

            } catch (Exception e) {
                // 启动失败，更新状态为错误
                server.setSystemServerStatus(SystemServer.SystemServerStatus.ERROR);
                updateById(server);
                throw e;
            }

        } catch (Exception e) {
            log.error("启动服务器失败: serverId={}", serverId, e);
            return ReturnResult.error("启动服务器失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> stopServer(Integer serverId) {
        try {
            SystemServer server = getById(serverId);
            if (server == null) {
                return ReturnResult.error("服务器不存在");
            }

            if (SystemServer.SystemServerStatus.STOPPED.equals(server.getSystemServerStatus())) {
                return ReturnResult.error("服务器已停止");
            }

            // 更新状态为停止中
            server.setSystemServerStatus(SystemServer.SystemServerStatus.STOPPING);
            updateById(server);

            try {
                // 获取服务器实例
                ProtocolServer serverInstance = runningServerInstances.get(serverId);
                if (serverInstance != null) {
                    // 停止服务器
                    stopServerInstance(serverInstance);

                    // 移除服务器实例
                    runningServerInstances.remove(serverId);
                }

                // 更新状态为已停止
                server.setSystemServerStatus(SystemServer.SystemServerStatus.STOPPED);
                updateById(server);

                log.info("服务器停止成功: {}", server.getSystemServerName());
                return ReturnResult.ok(true);

            } catch (Exception e) {
                // 停止失败，更新状态为错误
                server.setSystemServerStatus(SystemServer.SystemServerStatus.ERROR);
                updateById(server);
                throw e;
            }

        } catch (Exception e) {
            log.error("停止服务器失败: serverId={}", serverId, e);
            return ReturnResult.error("停止服务器失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<String> getServerStatus(Integer serverId) {
        try {
            SystemServer server = getById(serverId);
            if (server == null) {
                return ReturnResult.error("服务器不存在");
            }

            String status = server.getSystemServerStatus().getCode();
            return ReturnResult.ok(status);
        } catch (Exception e) {
            log.error("获取服务器状态失败: serverId={}", serverId, e);
            return ReturnResult.error("获取服务器状态失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<Boolean> restartServer(Integer serverId) {
        try {
            // 先停止服务器
            ReturnResult<Boolean> stopResult = stopServer(serverId);
            if (!stopResult.isOk()) {
                return stopResult;
            }

            // 等待一段时间确保完全停止
            Thread.sleep(1000);

            // 再启动服务器
            return startServer(serverId);
        } catch (Exception e) {
            log.error("重启服务器失败: serverId={}", serverId, e);
            return ReturnResult.error("重启服务器失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> checkPortAvailable(Integer port, Integer serverId) {
        try {
            SystemServer existingServer = baseMapper.selectByPort(port);
            if (existingServer != null && !existingServer.getSystemServerId().equals(serverId)) {
                return ReturnResult.ok(false);
            }

            // 检查端口是否被其他进程占用
            boolean inUse = isPortInUse(port);
            return ReturnResult.ok(!inUse);
        } catch (Exception e) {
            log.error("检查端口可用性失败: port={}", port, e);
            return ReturnResult.error("检查端口失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ReturnResult<SystemServer> cloneServer(Integer sourceServerId, String newServerName, Integer newPort) {
        try {
            SystemServer sourceServer = getById(sourceServerId);
            if (sourceServer == null) {
                return ReturnResult.error("源服务器不存在");
            }

            // 检查新端口是否可用
            ReturnResult<Boolean> portCheck = checkPortAvailable(newPort, null);
            if (!portCheck.isOk() || !portCheck.getData()) {
                return ReturnResult.error("端口 " + newPort + " 不可用");
            }

            // 创建新服务器
            SystemServer newServer = new SystemServer();
            BeanUtils.copyProperties(sourceServer, newServer);
            newServer.setSystemServerId(null);
            newServer.setSystemServerName(newServerName);
            newServer.setSystemServerPort(newPort);
            newServer.setSystemServerStatus(SystemServer.SystemServerStatus.STOPPED);

            save(newServer);

            // 克隆配置
            systemServerSettingService.cloneServerSettings(sourceServerId, newServer.getSystemServerId());

            log.info("克隆服务器成功: {} -> {}", sourceServer.getSystemServerName(), newServerName);
            return ReturnResult.ok(newServer);
        } catch (Exception e) {
            log.error("克隆服务器失败: sourceServerId={}", sourceServerId, e);
            return ReturnResult.error("克隆服务器失败: " + e.getMessage());
        }
    }

    @Override
    public ProtocolServer getRunningServerInstance(Integer serverId) {
        return runningServerInstances.get(serverId);
    }

    @Override
    public ReturnResult<Boolean> applyConfigChanges(Integer serverId) {
        try {
            ProtocolServer serverInstance = runningServerInstances.get(serverId);
            if (serverInstance == null) {
                return ReturnResult.error("服务器未运行，无需应用配置");
            }

            // 重新应用配置
            applyServerConfiguration(serverId, serverInstance, Action.UPDATE);

            log.info("应用配置更改成功: serverId={}", serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("应用配置更改失败: serverId={}", serverId, e);
            return ReturnResult.error("应用配置失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> applyDeleteConfigToRunningServer(Integer serverId) {
        try {
            ProtocolServer serverInstance = runningServerInstances.get(serverId);
            if (serverInstance == null) {
                return ReturnResult.error("服务器未运行，无需应用配置");
            }

            // 重新应用配置
            applyServerConfiguration(serverId, serverInstance, Action.DELETE);

            log.info("应用配置更改成功: serverId={}", serverId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("应用配置更改失败: serverId={}", serverId, e);
            return ReturnResult.error("应用配置失败: " + e.getMessage());
        }
    }

    /**
     * 通过SPI机制创建服务器实例
     */
    private ProtocolServer createServerInstance(SystemServer server) throws Exception {
        ServerSetting.ServerSettingBuilder builder = ServerSetting.builder()
                .serverId(String.valueOf(server.getSystemServerId()))
                .host(server.getSystemServerHost())
                .port(server.getSystemServerPort());
        if (null != server.getSystemServerMaxConnections()) {
            builder.maxConnections(server.getSystemServerMaxConnections());
        }

        if (null != server.getSystemServerTimeout()) {
            builder.connectionTimeoutMillis(server.getSystemServerTimeout());
        }
        ServerSetting serverSetting = builder.build();
        return ProtocolServerSupport.create(server.getSystemServerType(), serverSetting);
    }

    /**
     * 应用服务器配置
     */
    private void applyServerConfiguration(Integer serverId, ProtocolServer serverInstance, Action action) throws Exception {
        // 获取服务器配置
        ReturnResult<List<SystemServerSetting>> settingsResult = systemServerSettingService.getByServerIdAndEnabled(serverId, true);
        if (settingsResult.isOk() && settingsResult.getData() != null) {
            List<SystemServerSetting> settingsResultData = settingsResult.getData();
            for (SystemServerSetting setting : settingsResultData) {
                // 通过反射或其他方式应用配置到服务器实例
                applySettingToServerInstance(serverInstance, setting, action);
            }
            if (action == Action.CREATE || action == Action.UPDATE) {
                systemServerSettingService.updateBatchById(settingsResultData);
            }
        }
    }

    /**
     * 应用单个配置到服务器实例
     */
    private void applySettingToServerInstance(ProtocolServer serverInstance, SystemServerSetting setting, Action action) throws Exception {
        String systemServerSettingType = setting.getSystemServerSettingType();
        try {
            // 在使用配置之前先解密密码字段
            SystemServerSetting decryptedSetting = new SystemServerSetting();
            BeanUtils.copyProperties(setting, decryptedSetting);
            decryptSettingPasswords(decryptedSetting);
            
            if (action == Action.CREATE) {
                ServletFilterHandler servletFilterHandler = ServiceProvider.of(ServletFilterHandler.class).getNewExtension(
                        systemServerSettingType
                );
                // 包装日志代理，采集执行日志
                ServletFilter servletFilter = servletFilterHandler.handle(decryptedSetting, serverInstance.getObjectContext());
                servletFilter.addServletListener(new ServletListener() {

                    @Override
                    public void onEvent(ServletEvent event) {
                        LoggingServletFilterProxy.wrap(decryptedSetting, servletFilter, event);
                    }
                });
                setting.setSystemServerSettingFilterId(servletFilter.getFilterId());
                serverInstance.addFilter(servletFilter);
                return;
            }
            if (action == Action.UPDATE) {
                new HotloadingServletFilterHandler(serverInstance, systemServerSettingType).update(decryptedSetting);
                return;
            }
            if (action == Action.DELETE) {
                new HotloadingDeleteServletFilterHandler(serverInstance, systemServerSettingType).update(decryptedSetting);
            }
        } finally {
            log.debug("应用配置到服务器实例: 类型：{}, 配置名称{}",
                    systemServerSettingType, setting.getSystemServerSettingName());
        }
    }

    /**
     * 启动服务器实例
     */
    private void startServerInstance(ProtocolServer serverInstance) throws Exception {
        ProtocolServerSupport.startIfNecessary(serverInstance);
    }

    /**
     * 停止服务器实例
     */
    private void stopServerInstance(ProtocolServer serverInstance) throws Exception {
        ProtocolServerSupport.stopQuietly(serverInstance);
    }

    /**
     * 检查端口是否被占用
     */
    private boolean isPortInUse(Integer port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return false;
        } catch (java.io.IOException e) {
            return true;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!proxyManagementProperties.isAutoRestartRunning()) {
            log.info("代理管理模块已启用，但已关闭运行中实例自动恢复");
            return;
        }
        executorService.execute(() -> {
            for (SystemServer systemServer : this.list(
                    Wrappers.<SystemServer>lambdaQuery()
                            .eq(SystemServer::getSystemServerStatus, SystemServer.SystemServerStatus.RUNNING)
            )) {
                restartServer(systemServer.getSystemServerId());
            }

        });
    }

    /**
     * 重写save方法，在保存前加密敏感数据
     */
    @Override
    public boolean save(SystemServer entity) {
        if (entity != null) {
            encryptServerSensitiveData(entity);
        }
        return super.save(entity);
    }

    /**
     * 重写updateById方法，在更新前加密敏感数据
     */
    @Override
    public boolean updateById(SystemServer entity) {
        if (entity != null) {
            encryptServerSensitiveData(entity);
        }
        return super.updateById(entity);
    }

    /**
     * 重写getById方法，在查询后解密敏感数据
     */
    @Override
    public SystemServer getById(java.io.Serializable id) {
        SystemServer entity = super.getById(id);
        if (entity != null) {
            decryptServerSensitiveData(entity);
        }
        return entity;
    }

    /**
     * 重写list方法，在查询后解密敏感数据
     */
    @Override
    public List<SystemServer> list() {
        List<SystemServer> entities = super.list();
        if (entities != null) {
            entities.forEach(this::decryptServerSensitiveData);
        }
        return entities;
    }

    /**
     * 重写list方法，在查询后解密敏感数据
     */
    @Override
    public List<SystemServer> list(com.baomidou.mybatisplus.core.conditions.Wrapper<SystemServer> queryWrapper) {
        List<SystemServer> entities = super.list(queryWrapper);
        if (entities != null) {
            entities.forEach(this::decryptServerSensitiveData);
        }
        return entities;
    }

    /**
     * 加密SystemServer中的敏感数据
     *
     * @param server 服务器对象
     */
    private void encryptServerSensitiveData(SystemServer server) {
        if (server == null) {
            return;
        }
        
        // 如果有需要加密的字段，在这里添加
        // 例如：如果SystemServer有密码字段
        // if (server.getPassword() != null) {
        //     server.setPassword(encryptPassword(server.getPassword()));
        // }
    }

    /**
     * 解密SystemServer中的敏感数据
     *
     * @param server 服务器对象
     */
    private void decryptServerSensitiveData(SystemServer server) {
        if (server == null) {
            return;
        }
        
        // 如果有需要解密的字段，在这里添加
        // 例如：如果SystemServer有密码字段
        // if (server.getPassword() != null) {
        //     server.setPassword(decryptPassword(server.getPassword()));
        // }
    }

    /**
     * 加密密码
     *
     * @param password 明文密码
     * @return 加密后的密码（Base64编码）
     */
    public String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密密码
     *
     * @param encryptedPassword 加密后的密码（Base64编码）
     * @return 明文密码
     */
    public String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return encryptedPassword;
        }
        
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedPassword);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("密码解密失败", e);
            throw new RuntimeException("密码解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加密SystemServerSetting中的密码字段
     *
     * @param setting 服务器设置对象
     */
    public void encryptSettingPasswords(SystemServerSetting setting) {
        if (setting == null) {
            return;
        }
        
        // 加密PEM私钥密码
        if (setting.getSystemServerSettingHttpsPemKeyPassword() != null) {
            setting.setSystemServerSettingHttpsPemKeyPassword(
                encryptPassword(setting.getSystemServerSettingHttpsPemKeyPassword())
            );
        }
        
        // 加密Keystore密码
        if (setting.getSystemServerSettingHttpsKeystorePassword() != null) {
            setting.setSystemServerSettingHttpsKeystorePassword(
                encryptPassword(setting.getSystemServerSettingHttpsKeystorePassword())
            );
        }
    }

    /**
     * 解密SystemServerSetting中的密码字段
     *
     * @param setting 服务器设置对象
     */
    public void decryptSettingPasswords(SystemServerSetting setting) {
        if (setting == null) {
            return;
        }
        
        // 解密PEM私钥密码
        if (setting.getSystemServerSettingHttpsPemKeyPassword() != null) {
            setting.setSystemServerSettingHttpsPemKeyPassword(
                decryptPassword(setting.getSystemServerSettingHttpsPemKeyPassword())
            );
        }
        
        // 解密Keystore密码
        if (setting.getSystemServerSettingHttpsKeystorePassword() != null) {
            setting.setSystemServerSettingHttpsKeystorePassword(
                decryptPassword(setting.getSystemServerSettingHttpsKeystorePassword())
            );
        }
    }
}




