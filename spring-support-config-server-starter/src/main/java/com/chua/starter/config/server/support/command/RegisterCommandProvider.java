package com.chua.starter.config.server.support.command;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.common.support.key.KeyManagerProvider;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.manager.DataManager;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ExecutorService;


/**
 * 注册
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
@Spi("register")
public class RegisterCommandProvider implements CommandProvider, Constant {
    private final ExecutorService executorService = ThreadUtils.newProcessorThreadExecutor();

    @Resource
    private ConfigServerProperties configServerProperties;
    @Override
    public ReturnResult<String> command(String applicationName, String data, String dataType, String applicationProfile, DataManager dataManager, HttpServletRequest request) {
        ServiceProvider<Codec> serviceProvider = ServiceProvider.of(Codec.class);
        Codec encrypt = serviceProvider.getExtension(configServerProperties.getEncrypt());
        if (null == encrypt) {
            return ReturnResult.illegal();
        }

        AutowireCapableBeanFactory beanFactory = SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory();
        ServiceProvider<KeyManagerProvider> providerServiceProvider = ServiceProvider.of(KeyManagerProvider.class);
        KeyManagerProvider keyManagerProvider = providerServiceProvider.getExtension(configServerProperties.getKeyManager());
        if(null != keyManagerProvider) {
            beanFactory.autowireBean(keyManagerProvider);
        }
        String providerKey = configServerProperties.isOpenKey() ? keyManagerProvider.getKey(applicationName) : DEFAULT_SER;
        if(null == providerKey) {
            return ReturnResult.illegal();
        }

        String decode = encrypt.decodeHex(data, providerKey);
        if(null == decode) {
            return ReturnResult.illegal();
        }
        Map<String, Object> stringObjectMap = Json.toMapStringObject(decode);
        executorService.execute(() -> {
            dataManager.register(applicationName, dataType.toLowerCase(), applicationProfile, stringObjectMap);
        });
        return ReturnResult.ok(encrypt.encodeHex("", MapUtils.getString(stringObjectMap, ConfigConstant.KEY)));
    }

}
