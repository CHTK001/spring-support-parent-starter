package com.chua.starter.config.server.cammand;

import com.chua.common.support.crypto.Encrypt;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.common.support.key.KeyManagerProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.manager.ConfigurationManager;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;



/**
 * 注册
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
public class RegisterCommandProvider implements CommandProvider, Constant {
    @Override
    public ReturnResult<String> command(String binder, String data, ConfigServerProperties configServerProperties, HttpServletRequest request) {

        ServiceProvider<Encrypt> serviceProvider = ServiceProvider.of(Encrypt.class);
        Encrypt encrypt = serviceProvider.getExtension(configServerProperties.getEncrypt());
        if(null == encrypt) {
            return ReturnResult.illegal();
        }

        AutowireCapableBeanFactory beanFactory = SpringBeanUtils.getApplicationContext().getAutowireCapableBeanFactory();
        ServiceProvider<KeyManagerProvider> providerServiceProvider = ServiceProvider.of(KeyManagerProvider.class);
        KeyManagerProvider keyManagerProvider = providerServiceProvider.getExtension(configServerProperties.getKeyManager());
        if(null != keyManagerProvider) {
            beanFactory.autowireBean(keyManagerProvider);
        }
        String providerKey = configServerProperties.isOpenKey() ? keyManagerProvider.getKey(binder) : DEFAULT_SER;
        if(null == providerKey) {
            return ReturnResult.illegal();
        }

        String decode = encrypt.decodeHex(data, providerKey);
        if(null == decode) {
            return ReturnResult.illegal();
        }
        Map<String, Object> stringObjectMap = Json.toMapStringObject(decode);
        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
        beanFactory.autowireBean(configurationManager);
        try {
            configurationManager.afterPropertiesSet();
        } catch (Exception ignored) {
        }
        configurationManager.register(stringObjectMap, binder);
        String value = configurationManager.findValue(binder, MapUtils.getString(stringObjectMap, "binder-profile", "dev"));
        return ReturnResult.ok(encrypt.encodeHex(value, MapUtils.getString(stringObjectMap, "binder-key")));
    }

    @Override
    public String[] named() {
        return new String[]{"registerBean"};
    }
}
