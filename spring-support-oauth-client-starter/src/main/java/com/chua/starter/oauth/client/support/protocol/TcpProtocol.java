package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.annotations.Extension;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.exception.AuthException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * TCP协议客户端实现 - 优化数据包大小
 * <p>
 * 数据包大小优化策略：
 * 1. 使用二进制协议头部，仅6字节
 * 2. GZIP压缩JSON数据，减少50-70%大小
 * 3. 字段名缩写映射，减少冗余字符
 * 4. 长连接复用，避免重复握手开销
 * </p>
 *
 * @author CH
 */
@Extension("tcp")
@Slf4j
public class TcpProtocol extends AbstractProtocol {

    // TCP协议常量
    private static final byte LOGIN_REQUEST = 0x01;
    private static final byte LOGOUT_REQUEST = 0x02;
    private static final byte OAUTH_REQUEST = 0x03;
    private static final byte UPGRADE_REQUEST = 0x04;
    
    private static final byte SUCCESS_RESPONSE = 0x10;
    private static final byte ERROR_RESPONSE = 0x11;
    
    private static final byte PROTOCOL_VERSION = 0x01;
    
    // 连接池管理
    private final Map<String, Socket> connectionPool = new ConcurrentHashMap<>();
    
    // 字段名压缩映射 - 减少JSON大小
    private static final Map<String, String> FIELD_MAPPING = Map.of(
        "x-oauth-access-key", "ak",
        "x-oauth-secret-key", "sk", 
        "x-oauth-username", "u",
        "x-oauth-password", "p",
        "x-oauth-auth-type", "at",
        "x-oauth-ext", "ext",
        "x-oauth-cookie", "c",
        "x-oauth-token", "t",
        "x-oauth-uid", "uid",
        "x-oauth-logout-type", "lt"
    );

    public TcpProtocol(AuthClientProperties authClientProperties) {
        super(authClientProperties);
    }

    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        JsonObject jsonObject = new JsonObject();
        // 使用压缩字段名
        jsonObject.put("c", cookie.getValue());
        jsonObject.put("t", token);
        jsonObject.put("ak", authClientProperties.getKey().getAccessKey());
        jsonObject.put("sk", authClientProperties.getKey().getSecretKey());
        jsonObject.put("sp", StringUtils.defaultString(subProtocol, "DEFAULT").toUpperCase());
        
        return sendTcpRequest(OAUTH_REQUEST, jsonObject);
    }

    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        JsonObject jsonObject = new JsonObject();
        // 使用压缩字段名
        jsonObject.put("c", cookie.getValue());
        jsonObject.put("t", token);
        jsonObject.put("ak", authClientProperties.getKey().getAccessKey());
        jsonObject.put("sk", authClientProperties.getKey().getSecretKey());
        jsonObject.put("rt", refreshToken);
        jsonObject.put("ut", upgradeType.name());
        
        return sendTcpRequest(UPGRADE_REQUEST, jsonObject);
    }

    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        JsonObject jsonObject = new JsonObject();
        // 使用压缩字段名减少数据包大小
        jsonObject.put("ak", authClientProperties.getKey().getAccessKey());
        jsonObject.put("sk", authClientProperties.getKey().getSecretKey());
        jsonObject.put("u", username);
        jsonObject.put("p", password);
        jsonObject.put("at", authType.name());
        
        // 优化ext参数，移除冗余字段
        if (ext != null && !ext.isEmpty()) {
            JsonObject compressedExt = compressExtParameters(ext);
            jsonObject.put("ext", compressedExt);
        }
        
        AuthenticationInformation information = sendTcpRequest(LOGIN_REQUEST, jsonObject);
        
        LoginAuthResult loginAuthResult = new LoginAuthResult();
        loginAuthResult.setCode(information.getInformation().getCode());
        loginAuthResult.setMessage(information.getInformation().getMessage());
        loginAuthResult.setUserResume(information.getReturnResult());
        loginAuthResult.setToken(information.getToken());
        loginAuthResult.setRefreshToken(information.getRefreshToken());
        
        return loginAuthResult;
    }

    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        JsonObject jsonObject = new JsonObject();
        // 使用压缩字段名
        jsonObject.put("ak", authClientProperties.getKey().getAccessKey());
        jsonObject.put("sk", authClientProperties.getKey().getSecretKey());
        jsonObject.put("uid", uid);
        jsonObject.put("lt", logoutType.name());
        
        AuthenticationInformation information = sendTcpRequest(LOGOUT_REQUEST, jsonObject);
        
        if (information.getInformation() == Information.OK) {
            return LoginAuthResult.OK;
        }
        
        throw new AuthException(information.getInformation().getMessage());
    }

    /**
     * 发送TCP请求
     */
    private AuthenticationInformation sendTcpRequest(byte requestType, JsonObject jsonObject) {
        String serverUrl = selectUrl();
        if (serverUrl == null) {
            return AuthenticationInformation.authServerError();
        }
        
        Socket socket = null;
        try {
            // 获取或创建连接
            socket = getOrCreateConnection(serverUrl);
            
            // 发送请求
            sendRequest(socket, requestType, jsonObject);
            
            // 接收响应
            return receiveResponse(socket);
            
        } catch (Exception e) {
            log.error("TCP请求异常: {}", e.getMessage(), e);
            // 连接异常时移除连接
            if (socket != null) {
                connectionPool.remove(serverUrl);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
            return AuthenticationInformation.authServerError();
        }
    }

    /**
     * 获取或创建连接
     */
    private Socket getOrCreateConnection(String serverUrl) throws IOException {
        Socket socket = connectionPool.get(serverUrl);

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            // 从HTTP URL中提取主机和端口
            String host = extractHost(serverUrl);
            int port = extractTcpPort(serverUrl);

            socket = new Socket(host, port);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            connectionPool.put(serverUrl, socket);
            log.debug("创建新的TCP连接: {}:{}", host, port);
        }
        
        return socket;
    }

    /**
     * 从URL中提取主机名
     */
    private String extractHost(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                java.net.URL urlObj = new java.net.URL(url);
                return urlObj.getHost();
            } else {
                // 如果不是完整URL，假设是host:port格式
                String[] parts = url.split(":");
                return parts[0];
            }
        } catch (Exception e) {
            log.warn("解析主机名失败，使用默认值: {}", e.getMessage());
            return "localhost";
        }
    }

    /**
     * 从URL中提取TCP端口
     */
    private int extractTcpPort(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                java.net.URL urlObj = new java.net.URL(url);
                int httpPort = urlObj.getPort();
                if (httpPort == -1) {
                    httpPort = urlObj.getDefaultPort();
                }
                // TCP端口通常是HTTP端口+1
                return httpPort + 1;
            } else {
                // 如果不是完整URL，假设是host:port格式
                String[] parts = url.split(":");
                if (parts.length > 1) {
                    return Integer.parseInt(parts[1]) + 1; // TCP端口是HTTP端口+1
                }
                return 8081; // 默认TCP端口
            }
        } catch (Exception e) {
            log.warn("解析TCP端口失败，使用默认值: {}", e.getMessage());
            return 8081;
        }
    }

    /**
     * 发送请求
     */
    private void sendRequest(Socket socket, byte requestType, JsonObject jsonObject) throws IOException {
        try (DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            // 压缩JSON数据
            String jsonData = jsonObject.toJSONString();
            byte[] compressedData = compressData(jsonData);
            
            // 发送协议头部
            output.writeByte(PROTOCOL_VERSION);
            output.writeByte(requestType);
            output.writeInt(compressedData.length);
            
            // 发送数据
            output.write(compressedData);
            output.flush();
            
            log.debug("发送TCP请求，类型: {}, 原始大小: {}字节, 压缩后: {}字节", 
                requestType, jsonData.length(), compressedData.length);
        }
    }

    /**
     * 接收响应
     */
    private AuthenticationInformation receiveResponse(Socket socket) throws IOException {
        try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
            // 读取响应头部
            byte version = input.readByte();
            byte responseType = input.readByte();
            int dataLength = input.readInt();
            
            if (version != PROTOCOL_VERSION) {
                return AuthenticationInformation.authServerError();
            }
            
            // 读取响应数据
            byte[] data = new byte[dataLength];
            input.readFully(data);
            
            // 解压缩数据
            String responseData = decompressData(data);
            
            // 解析响应
            ReturnResult<?> result = Json.fromJson(responseData, ReturnResult.class);
            
            if (responseType == SUCCESS_RESPONSE && result.isOk()) {
                return createSuccessAuthenticationInformation(result);
            } else {
                return AuthenticationInformation.authServerError();
            }
        }
    }

    /**
     * 压缩扩展参数
     */
    private JsonObject compressExtParameters(Map<String, Object> ext) {
        JsonObject compressed = new JsonObject();
        
        // 只保留关键字段，使用缩写
        if (ext.containsKey("address")) {
            compressed.put("addr", ext.get("address"));
        }
        if (ext.containsKey("fingerprint")) {
            compressed.put("fp", ext.get("fingerprint"));
        }
        if (ext.containsKey("ua")) {
            compressed.put("ua", ext.get("ua"));
        }
        if (ext.containsKey("timezone")) {
            compressed.put("tz", ext.get("timezone"));
        }
        
        return compressed;
    }

    /**
     * 压缩数据
     */
    private byte[] compressData(String data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            
            gzipOut.write(data.getBytes(StandardCharsets.UTF_8));
            gzipOut.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("数据压缩失败，使用原始数据: {}", e.getMessage());
            return data.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 解压缩数据
     */
    private String decompressData(byte[] compressedData) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("数据解压缩失败，尝试使用原始数据: {}", e.getMessage());
            return new String(compressedData, StandardCharsets.UTF_8);
        }
    }

    /**
     * 创建成功的认证信息
     */
    private AuthenticationInformation createSuccessAuthenticationInformation(ReturnResult<?> result) {
        if (result.isSuccess()) {
            // 从结果中提取UserResume
            Object data = result.getData();
            if (data instanceof String) {
                try {
                    UserResume userResume = Json.fromJson((String) data, UserResume.class);
                    return new AuthenticationInformation(Information.OK, userResume);
                } catch (Exception e) {
                    log.warn("解析UserResume失败: {}", e.getMessage());
                    return new AuthenticationInformation(Information.OK, null);
                }
            }
            return new AuthenticationInformation(Information.OK, null);
        } else {
            // 创建失败的认证信息
            return new AuthenticationInformation(Information.AUTHENTICATION_FAILURE, null);
        }
    }
}
