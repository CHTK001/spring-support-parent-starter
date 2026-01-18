package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.spring.support.configuration.SpringBeanUtils;
import org.springframework.boot.context.properties.bind.Binder;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;

import java.util.Map;

/**
 * 实现了ProtocolExecutor接口的StaticProtocolExecutor类。
 * <p>
 * 该类旨在处理特定的协议执行逻辑，通过静态方法提供协议处理能力。
 * 作为协议执行器，它负责解析和执行特定格式的协议数据。
 * </p>
 * @author CH
 * @since 2024/6/12
 */
@SpiDefault
public class DefaultProtocolExecutor implements ProtocolExecutor{

    private final AuthClientProperties authClientProperties;
    private final String encryption;
    private final String protocol;

    public DefaultProtocolExecutor() {
        this.authClientProperties = Binder.get(SpringBeanUtils.getEnvironment())
                .bindOrCreate(AuthClientProperties.PRE, AuthClientProperties.class);
        this.encryption = authClientProperties.getEncryption();
        this.protocol = authClientProperties.getProtocol();
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        return ServiceProvider.of(Protocol.class).getNewExtension(protocol, authClientProperties)
                .getAccessToken(username, password, authType, ext);
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult ) {
        return ServiceProvider.of(Protocol.class).getNewExtension(protocol, authClientProperties)
                .logout(uid, logoutType, userResult);
    }

}

