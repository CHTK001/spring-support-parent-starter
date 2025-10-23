package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.grpc.*;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * gRPC协议实现
 * 基于gRPC协议进行OAuth认证，提供高性能的二进制通信
 *
 * @author CH
 */
@Extension("grpc")
@Slf4j
public class GrpcProtocol extends AbstractProtocol {

    private volatile ManagedChannel channel;
    private volatile OAuthAuthServiceGrpc.OAuthAuthServiceBlockingStub blockingStub;

    public GrpcProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    /**
     * 初始化gRPC连接
     */
    private void initializeChannel() {
        if (channel == null || channel.isShutdown()) {
            synchronized (this) {
                if (channel == null || channel.isShutdown()) {
                    String serverUrl = selectUrl();
                    if (serverUrl == null) {
                        throw new IllegalStateException("gRPC服务器地址不存在");
                    }

                    // 解析gRPC服务器地址和端口
                    String host = extractHost(serverUrl);
                    int port = extractGrpcPort(serverUrl);

                    channel = ManagedChannelBuilder.forAddress(host, port)
                            .usePlaintext() // 使用明文传输，生产环境应使用TLS
                            .keepAliveTime(30, TimeUnit.SECONDS)
                            .keepAliveTimeout(5, TimeUnit.SECONDS)
                            .keepAliveWithoutCalls(true)
                            .maxInboundMessageSize(4 * 1024 * 1024) // 4MB
                            .build();

                    blockingStub = OAuthAuthServiceGrpc.newBlockingStub(channel);
                }
            }
        }
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        initializeChannel();

        try {
            ApproveRequest.Builder requestBuilder = ApproveRequest.newBuilder()
                    .setCookie(cookie != null ? cookie.getValue() : "")
                    .setToken(token != null ? token : "")
                    .setAccessKey(authClientProperties.getKey().getAccessKey())
                    .setSecretKey(authClientProperties.getKey().getSecretKey())
                    .setSubProtocol(StringUtils.defaultString(subProtocol, "DEFAULT").toUpperCase())
                    .setParamAddress(RequestUtils.getIpAddress())
                    .setParamAppName(SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"))
                    .setExtTimestamp(System.currentTimeMillis());

            ApproveResponse response = blockingStub.approve(requestBuilder.build());
            return createAuthenticationInformation(response);

        } catch (StatusRuntimeException e) {
            log.error("gRPC认证调用失败: {}", e.getStatus());
            return AuthenticationInformation.authServerError();
        }
    }

    @Override
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        initializeChannel();

        try {
            // 构建 gRPC 请求
            AuthenticateUserCodeRequest.Builder requestBuilder = AuthenticateUserCodeRequest.newBuilder()
                    .setAccessKey(authClientProperties.getKey().getAccessKey())
                    .setSecretKey(authClientProperties.getKey().getSecretKey())
                    .setAppId(StringUtils.defaultString(appKeySecret.getAppId(), ""))
                    .setUserCode(StringUtils.defaultString(appKeySecret.getUserCode(), ""))
                    .setXTime(StringUtils.defaultString(appKeySecret.getXTime(), ""))
                    .setXRandom(StringUtils.defaultString(appKeySecret.getXRandom(), ""))
                    .setBody(StringUtils.defaultString(appKeySecret.getBody(), ""))
                    .setXSign(StringUtils.defaultString(appKeySecret.getXSign(), ""))
                    .setParamAddress(RequestUtils.getIpAddress())
                    .setParamAppName(SpringBeanUtils.getEnvironment().resolvePlaceholders("${spring.application.name:}"));

            log.info("gRPC UserCode 认证请求: appId={}, userCode={}, timestamp={}", 
                    appKeySecret.getAppId(), appKeySecret.getUserCode(), appKeySecret.getXTime());

            // 调用 gRPC 服务
            AuthenticateUserCodeResponse response = blockingStub.authenticateUserCode(requestBuilder.build());

            log.info("gRPC UserCode 认证响应: code={}, message={}", response.getCode(), response.getMessage());

            // 转换响应
            return createAuthenticationInformationFromUserCode(response);

        } catch (StatusRuntimeException e) {
            log.error("gRPC UserCode 认证调用失败: status={}, description={}", 
                    e.getStatus().getCode(), e.getStatus().getDescription());
            return AuthenticationInformation.authServerError();
        } catch (Exception e) {
            log.error("gRPC UserCode 认证异常", e);
            return AuthenticationInformation.authServerError();
        }
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        initializeChannel();

        try {
            UpgradeRequest.Builder requestBuilder = UpgradeRequest.newBuilder()
                    .setCookie(cookie != null ? cookie.getValue() : "")
                    .setToken(token != null ? token : "")
                    .setAccessKey(authClientProperties.getKey().getAccessKey())
                    .setSecretKey(authClientProperties.getKey().getSecretKey())
                    .setRefreshToken(refreshToken != null ? refreshToken : "")
                    .setUpgradeType(upgradeType.name().toUpperCase());

            UpgradeResponse response = blockingStub.upgrade(requestBuilder.build());
            return createAuthenticationInformation(response);

        } catch (StatusRuntimeException e) {
            log.error("gRPC升级调用失败: {}", e.getStatus());
            return AuthenticationInformation.authServerError();
        }
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        initializeChannel();

        try {
            GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder()
                    .setAccessKey(authClientProperties.getKey().getAccessKey())
                    .setSecretKey(authClientProperties.getKey().getSecretKey())
                    .setUsername(username)
                    .setPassword(password)
                    .setAuthType(authType.name());

            // 转换扩展参数
            if (ext != null) {
                Map<String, String> extMap = new HashMap<>();
                ext.forEach((k, v) -> extMap.put(k, v != null ? v.toString() : ""));
                requestBuilder.putAllExt(extMap);
            }

            GetAccessTokenResponse response = blockingStub.getAccessToken(requestBuilder.build());

            LoginAuthResult loginAuthResult = new LoginAuthResult();
            loginAuthResult.setCode(response.getCode());
            loginAuthResult.setMessage(response.getMessage());
            loginAuthResult.setToken(response.getToken());
            loginAuthResult.setRefreshToken(response.getRefreshToken());

            if (response.hasUserResume()) {
                loginAuthResult.setUserResume(convertToUserResume(response.getUserResume()));
            }

            return loginAuthResult;

        } catch (StatusRuntimeException e) {
            log.error("gRPC获取访问令牌失败: {}", e.getStatus());
            throw new AuthException("获取访问令牌失败: " + e.getStatus().getDescription());
        }
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        initializeChannel();

        try {
            LogoutRequest.Builder requestBuilder = LogoutRequest.newBuilder()
                    .setAccessKey(authClientProperties.getKey().getAccessKey())
                    .setSecretKey(authClientProperties.getKey().getSecretKey())
                    .setUid(uid)
                    .setLogoutType(logoutType.name());

            LogoutResponse response = blockingStub.logout(requestBuilder.build());

            if (response.getCode() == 200) {
                return LoginAuthResult.OK;
            }

            throw new AuthException(response.getMessage());

        } catch (StatusRuntimeException e) {
            log.error("gRPC登出调用失败: {}", e.getStatus());
            throw new AuthException("登出失败: " + e.getStatus().getDescription());
        }
    }

    /**
     * 创建认证信息
     */
    private AuthenticationInformation createAuthenticationInformation(ApproveResponse response) {
        ReturnResult<UserResult> returnResult = new ReturnResult<>();
        returnResult.setCode(String.valueOf(response.getCode()));
        returnResult.setMsg(response.getMessage());

        if (response.hasData()) {
            returnResult.setData(convertToUserResult(response.getData()));
        }

        AuthenticationInformation authInfo = createAuthenticationInformation(returnResult, IdUtils.simpleUuid(), "oauth");
        if (StringUtils.isNotBlank(response.getToken())) {
            authInfo.setToken(response.getToken());
        }
        if (StringUtils.isNotBlank(response.getRefreshToken())) {
            authInfo.setRefreshToken(response.getRefreshToken());
        }

        return authInfo;
    }

    /**
     * 创建认证信息（升级用）
     */
    private AuthenticationInformation createAuthenticationInformation(UpgradeResponse response) {
        ReturnResult<UserResult> returnResult = new ReturnResult<>();
        returnResult.setCode(String.valueOf(response.getCode()));
        returnResult.setMsg(response.getMessage());

        if (response.hasData()) {
            returnResult.setData(convertToUserResult(response.getData()));
        }

        AuthenticationInformation authInfo = createAuthenticationInformation(returnResult, IdUtils.simpleUuid(), "upgrade");
        if (StringUtils.isNotBlank(response.getToken())) {
            authInfo.setToken(response.getToken());
        }
        if (StringUtils.isNotBlank(response.getRefreshToken())) {
            authInfo.setRefreshToken(response.getRefreshToken());
        }

        return authInfo;
    }

    /**
     * 创建认证信息（UserCode认证用）
     */
    private AuthenticationInformation createAuthenticationInformationFromUserCode(AuthenticateUserCodeResponse response) {
        ReturnResult<UserResult> returnResult = new ReturnResult<>();
        returnResult.setCode(String.valueOf(response.getCode()));
        returnResult.setMsg(response.getMessage());

        if (response.hasData()) {
            returnResult.setData(convertToUserResult(response.getData()));
        }

        AuthenticationInformation authInfo = createAuthenticationInformation(returnResult, IdUtils.simpleUuid(), "userCode");
        if (StringUtils.isNotBlank(response.getToken())) {
            authInfo.setToken(response.getToken());
        }
        if (StringUtils.isNotBlank(response.getRefreshToken())) {
            authInfo.setRefreshToken(response.getRefreshToken());
        }

        return authInfo;
    }

    /**
     * 转换proto UserResult到Java UserResult
     */
    private UserResult convertToUserResult(com.chua.starter.oauth.client.support.grpc.UserResult protoUser) {
        UserResult userResult = new UserResult();
        userResult.setUsername(protoUser.getUsername());
        userResult.setToken(protoUser.getToken());
        userResult.setUserId(protoUser.getUserId());
        userResult.setTenantId(protoUser.getTenantId());
        userResult.setRealName(protoUser.getRealName());
        userResult.setRoles(new HashSet<>(protoUser.getRolesList()));
//        userResult.setPermissions(protoUser.getPermissionsList().toArray(new String[0]));

        Map<String, Object> ext = new HashMap<>();
        protoUser.getExtMap().forEach(ext::put);
        userResult.setExt(ext);

        return userResult;
    }

    /**
     * 转换proto UserResume到Java UserResume
     */
    private UserResume convertToUserResume(com.chua.starter.oauth.client.support.grpc.UserResume protoUser) {
        UserResume userResume = new UserResume();
        userResume.setUsername(protoUser.getUsername());
        userResume.setUserId(protoUser.getUserId());
        userResume.setTenantId(protoUser.getTenantId());
        userResume.setRealName(protoUser.getRealName());
        userResume.setDeptId(protoUser.getDeptId());
//        userResume.isAdmin(protoUser.getIsAdmin());

        Map<String, Object> ext = new HashMap<>();
        protoUser.getExtMap().forEach(ext::put);
        userResume.setExt(ext);

        return userResume;
    }

    /**
     * 提取主机名
     */
    private String extractHost(String url) {
        if (url.startsWith("grpc://")) {
            url = url.substring(7);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }

        int colonIndex = url.indexOf(':');
        if (colonIndex > 0) {
            return url.substring(0, colonIndex);
        }

        int slashIndex = url.indexOf('/');
        if (slashIndex > 0) {
            return url.substring(0, slashIndex);
        }

        return url;
    }

    /**
     * 提取gRPC端口
     */
    private int extractGrpcPort(String url) {
        if (url.startsWith("grpc://")) {
            url = url.substring(7);
        } else if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }

        int colonIndex = url.indexOf(':');
        if (colonIndex > 0) {
            int slashIndex = url.indexOf('/', colonIndex);
            String portStr = slashIndex > 0 ? url.substring(colonIndex + 1, slashIndex) : url.substring(colonIndex + 1);
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.warn("无法解析端口号: {}, 使用默认端口9090", portStr);
            }
        }

        return 9090; // 默认gRPC端口
    }

    /**
     * 关闭gRPC连接
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("关闭gRPC连接时被中断", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}