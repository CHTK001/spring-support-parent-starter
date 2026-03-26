package com.chua.starter.proxy.support.handler;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.network.protocol.filter.ServletFilter;
import com.chua.common.support.network.protocol.server.UpgradeServletFilter;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.proxy.support.entity.SystemServerSetting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTPS 配置处理器（与 SystemServerSetting 的 BLOB 字段对接）
 * <p>
 * 要求：存在一个 ServletFilter SPI 实现，SPI 名称与本设置的 type 一致（HTTPS_CONFIG），
 * 并且实现 UpgradeServletFilter 接口以支持热升级。
 * <p>
 * 传递的配置键：
 * - enabled: Boolean
 * - certType: String (PEM/PFX/JKS)
 * - pemCert: byte[]
 * - pemKey: byte[]
 * - keyPassword: String
 * - keystore: byte[]
 * - keystorePassword: String
 */
@Slf4j
@Spi("HTTPS_CONFIG")
@SpiDefault
public class HttpsServletFilterHandler implements ServletFilterHandler {

    @Override
    public ServletFilter handle(SystemServerSetting setting, ConfigureObjectContext objectContext) {
        ServletFilter servletFilter = ServiceProvider.of(ServletFilter.class)
                .getNewExtension(setting.getSystemServerSettingType());
        if (servletFilter instanceof UpgradeServletFilter upgrade) {
            upgrade.upgrade(createConfig(setting));
        }
        return servletFilter;
    }

    @Override
    public void update(ServletFilter filter, SystemServerSetting setting) {
        if (filter instanceof UpgradeServletFilter upgrade) {
            upgrade.upgrade(createConfig(setting));
        }
    }

    private Map<String, Object> createConfig(SystemServerSetting setting) {
        Map<String, Object> cfg = new HashMap<>(8);
        cfg.put("serverId", setting.getSystemServerSettingServerId());
        cfg.put("enabled", setting.getSystemServerSettingHttpsEnabled());
        cfg.put("certType", setting.getSystemServerSettingHttpsCertType() == null ? null : setting.getSystemServerSettingHttpsCertType().name());
        cfg.put("pemCert", setting.getSystemServerSettingHttpsPemCert());
        cfg.put("pemKey", setting.getSystemServerSettingHttpsPemKey());
        cfg.put("keyPassword", setting.getSystemServerSettingHttpsPemKeyPassword());
        cfg.put("keystore", setting.getSystemServerSettingHttpsKeystore());
        cfg.put("keystorePassword", setting.getSystemServerSettingHttpsKeystorePassword());
        return cfg;
    }
}





