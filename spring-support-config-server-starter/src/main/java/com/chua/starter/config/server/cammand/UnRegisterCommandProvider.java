package com.chua.starter.config.server.cammand;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.key.KeyManagerProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.manager.ConfigurationManager;
import com.chua.starter.config.server.properties.ConfigServerProperties;

import javax.servlet.http.HttpServletRequest;

/**
 * 注册
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
public class UnRegisterCommandProvider implements CommandProvider {
    @Override
    public ReturnResult<String> command(String subscribe, String binder, String data, ConfigServerProperties configServerProperties, HttpServletRequest request) {
        if (!configServerProperties.isOpenAutoDestroy()) {
            return ReturnResult.ok();
        }

        ServiceProvider<Codec> serviceProvider = ServiceProvider.of(Codec.class);
        Codec encrypt = serviceProvider.getExtension(configServerProperties.getEncrypt());
        if (null == encrypt) {
            return ReturnResult.illegal();
        }
        ServiceProvider<KeyManagerProvider> providerServiceProvider = ServiceProvider.of(KeyManagerProvider.class);
        KeyManagerProvider keyManagerProvider = providerServiceProvider.getExtension(configServerProperties.getKeyManager());
        String providerKey = keyManagerProvider.getKey(binder);
        if(null == providerKey) {
            return ReturnResult.illegal();
        }

        String decode = encrypt.decodeHex(data, providerKey);
        if(null == decode) {
            return ReturnResult.illegal();
        }

        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
        configurationManager.unregister(binder);

        return ReturnResult.ok();
    }

    @Override
    public String[] named() {
        return new String[]{"unregister"};
    }
}
