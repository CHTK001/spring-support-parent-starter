package com.chua.starter.common.support.api.encode;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;

import java.util.List;

/**
 * 响应编码注册器
 * <p>
 * 提供响应数据加密功能。
 * </p>
 *
 * @author CH
 * @version 2.0.0
 * @since 2024/01/22
 */
@Slf4j
public class ApiResponseEncodeRegister implements Upgrade<ApiResponseEncodeConfiguration>, ApplicationListener<ApiResponseEncodeConfiguration> {
    private final List<String> whiteList;
    private final Codec codec;
    private final CodecKeyPair codecKeyPair;
    private final String publicKeyHex;
    private final GlobalSettingFactory globalSettingFactory;

    /**
     * 编解码器类型
     */
    private final String codecType;
    private ApiResponseEncodeConfiguration apiResponseEncodeConfiguration;

    /**
     * 构造函数
     *
     * @param responseEncodePropertiesConfig 编解码配置
     */
    public ApiResponseEncodeRegister(ApiProperties.ResponseEncodeProperties responseEncodePropertiesConfig) {
        this.globalSettingFactory = GlobalSettingFactory.getInstance();
        boolean extInject = responseEncodePropertiesConfig.isExtInject();
        if (!extInject) {
            globalSettingFactory.register("config", new ApiResponseEncodeConfiguration());
            globalSettingFactory.setIfNoChange("config", "codecResponseOpen", responseEncodePropertiesConfig.isResponseEnable());
            this.upgrade(globalSettingFactory.get("config", ApiResponseEncodeConfiguration.class));
        }
        this.codecType = responseEncodePropertiesConfig.getCodecType();
        this.codec = Codec.build(codecType);
        this.whiteList = responseEncodePropertiesConfig.getWhiteList();
        this.codecKeyPair = (CodecKeyPair) codec;
        this.publicKeyHex = codecKeyPair.getPublicKeyHex();
    }

    public boolean isPass() {
        check();
        return !apiResponseEncodeConfiguration.isCodecResponseOpen();
    }

    private void check() {
        if (null != apiResponseEncodeConfiguration) {
            return;
        }
        this.upgrade(globalSettingFactory.get("config", ApiResponseEncodeConfiguration.class));
    }

    /**
     * 编码数据
     *
     * @param data 数据
     * @return 编码结果
     */
    public CodecResult encode(String data) {
        try {
            String encryptedData = codecKeyPair.encode(data, publicKeyHex);
            String transportKey = codecKeyPair.getPrivateKeyHex();
            String keyLength = String.valueOf(transportKey.length());
            
            log.debug("[CodecFactory] 数据加密完成，数据长度: {}", data.length());
            return new CodecResult(transportKey, encryptedData, keyLength);
        } catch (Exception e) {
            log.error("[CodecFactory] 数据加密失败", e);
            String encode = codecKeyPair.encode(data, publicKeyHex);
            String transportKey = codecKeyPair.getPrivateKeyHex();
            String keyLength = String.valueOf(transportKey.length());
            return new CodecResult(transportKey, encode, keyLength);
        }
    }

    /**
     * 设置是否启用
     *
     * @param parseBoolean 是否启用
     */
    public void setEnable(boolean parseBoolean) {
        check();
        apiResponseEncodeConfiguration.setCodecResponseOpen(parseBoolean);
    }

    /**
     * 是否通过（白名单检查）
     *
     * @param requestURI 请求URI
     * @return 是否通过
     */
    public boolean isPass(String requestURI) {
        if (isPass()) {
            return true;
        }
        for (String s : whiteList) {
            if (PathMatcher.INSTANCE.match(s, requestURI)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void upgrade(ApiResponseEncodeConfiguration apiResponseEncodeConfiguration) {
        this.apiResponseEncodeConfiguration = apiResponseEncodeConfiguration;
    }

    @Override
    public void onApplicationEvent(ApiResponseEncodeConfiguration event) {
        upgrade(event);
    }

    /**
     * 编码结果
     */
    @Getter
    public static class CodecResult {
        /**
         * 传输密钥
         */
        private final String key;
        /**
         * 加密后的数据
         */
        private final String data;
        /**
         * 额外字段（当前用于透传 keyLength）
         */
        private final String timestamp;

        /**
         * 构造函数
         *
         * @param key       传输密钥
         * @param data      加密后的数据
         * @param timestamp 额外字段
         */
        public CodecResult(String key, String data, String timestamp) {
            this.key = key;
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}

