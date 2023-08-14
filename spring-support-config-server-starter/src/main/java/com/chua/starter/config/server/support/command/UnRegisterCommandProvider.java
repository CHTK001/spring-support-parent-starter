package com.chua.starter.config.server.support.command;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.key.KeyManagerProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.support.manager.DataManager;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 注册
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
@Spi("unregister")
public class UnRegisterCommandProvider implements CommandProvider {

    @Resource
    private ConfigServerProperties configServerProperties;

    @Override
    public ReturnResult<String> command(String subscribe, String applicationName, String data, String dataType, DataManager dataManager, HttpServletRequest request) {
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
        String providerKey = keyManagerProvider.getKey(applicationName);
        if(null == providerKey) {
            return ReturnResult.illegal();
        }

        String decode = encrypt.decodeHex(data, providerKey);
        if(null == decode) {
            return ReturnResult.illegal();
        }
        dataManager.unregister(applicationName, dataType);

        return ReturnResult.ok();
    }
}
