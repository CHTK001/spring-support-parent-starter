package com.chua.starter.oauth.client.support.execute;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.robin.Node;
import com.chua.common.support.lang.robin.Robin;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.Binder;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.oauth.client.support.advice.def.DefSecret;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.protocol.Protocol;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.chua.common.support.constant.NumberConstant.NUM_200;
import static com.chua.common.support.http.HttpClientUtils.APPLICATION_JSON;
import static com.chua.common.support.lang.code.ReturnCode.OK;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.ACCESS_KEY;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SECRET_KEY;

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
        this.authClientProperties = Binder.binder(AuthClientProperties.PRE, AuthClientProperties.class);
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

