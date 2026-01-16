package com.chua.starter.common.support.api.encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.application.GlobalSettingFactory;
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
public class ApiResponseEncodeRegister implements Upgrade<ApiResponseEncodeConfiguration>, ApplicationListener<ApiResponseEncodeConfiguration> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiResponseEncodeRegister.class);


    private final List<String> whiteList;
    private final Codec codec;
    private final CodecKeyPair codecKeyPair;
    private final String publicKeyHex;
    private final GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();

    /**
     * 编解码器类型
     */
    private final String codecType = "sm2";
    private ApiResponseEncodeConfiguration apiResponseEncodeConfiguration;

    /**
     * 构造函数
     *
     * @param responseEncodePropertiesConfig 编解码配置
     */
    public ApiResponseEncodeRegister(ApiProperties.ResponseEncodeProperties responseEncodePropertiesConfig) {
        boolean extInject = responseEncodePropertiesConfig.isExtInject();
        if (!extInject) {
            globalSettingFactory.register("config", new ApiResponseEncodeConfiguration());
            globalSettingFactory.setIfNoChange("config", "codecResponseOpen", responseEncodePropertiesConfig.isResponseEnable());
            this.upgrade(globalSettingFactory.get("config", ApiResponseEncodeConfiguration.class));
        }
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
        public static class CodecResult {
        private String key;
        private String data;
        private String timestamp;

        public CodecResult(String key, String data, String timestamp) {
            this.key = key;
            this.data = data;
            this.timestamp = timestamp;
        }
    /**
     * 获取 whiteList
     *
     * @return whiteList
     */
    public List<String> getWhiteList() {
        return whiteList;
    }

    /**
     * 设置 whiteList
     *
     * @param whiteList whiteList
     */
    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    /**
     * 获取 codec
     *
     * @return codec
     */
    public Codec getCodec() {
        return codec;
    }

    /**
     * 设置 codec
     *
     * @param codec codec
     */
    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    /**
     * 获取 codecKeyPair
     *
     * @return codecKeyPair
     */
    public CodecKeyPair getCodecKeyPair() {
        return codecKeyPair;
    }

    /**
     * 设置 codecKeyPair
     *
     * @param codecKeyPair codecKeyPair
     */
    public void setCodecKeyPair(CodecKeyPair codecKeyPair) {
        this.codecKeyPair = codecKeyPair;
    }

    /**
     * 获取 publicKeyHex
     *
     * @return publicKeyHex
     */
    public String getPublicKeyHex() {
        return publicKeyHex;
    }

    /**
     * 设置 publicKeyHex
     *
     * @param publicKeyHex publicKeyHex
     */
    public void setPublicKeyHex(String publicKeyHex) {
        this.publicKeyHex = publicKeyHex;
    }

    /**
     * 获取 globalSettingFactory
     *
     * @return globalSettingFactory
     */
    public GlobalSettingFactory getGlobalSettingFactory() {
        return globalSettingFactory;
    }

    /**
     * 设置 globalSettingFactory
     *
     * @param globalSettingFactory globalSettingFactory
     */
    public void setGlobalSettingFactory(GlobalSettingFactory globalSettingFactory) {
        this.globalSettingFactory = globalSettingFactory;
    }

    /**
     * 获取 codecType
     *
     * @return codecType
     */
    public String getCodecType() {
        return codecType;
    }

    /**
     * 设置 codecType
     *
     * @param codecType codecType
     */
    public void setCodecType(String codecType) {
        this.codecType = codecType;
    }

    /**
     * 获取 apiResponseEncodeConfiguration
     *
     * @return apiResponseEncodeConfiguration
     */
    public ApiResponseEncodeConfiguration getApiResponseEncodeConfiguration() {
        return apiResponseEncodeConfiguration;
    }

    /**
     * 设置 apiResponseEncodeConfiguration
     *
     * @param apiResponseEncodeConfiguration apiResponseEncodeConfiguration
     */
    public void setApiResponseEncodeConfiguration(ApiResponseEncodeConfiguration apiResponseEncodeConfiguration) {
        this.apiResponseEncodeConfiguration = apiResponseEncodeConfiguration;
    }

    /**
     * 获取 extInject
     *
     * @return extInject
     */
    public boolean getExtInject() {
        return extInject;
    }

    /**
     * 设置 extInject
     *
     * @param extInject extInject
     */
    public void setExtInject(boolean extInject) {
        this.extInject = extInject;
    }

    /**
     * 获取 encryptedData
     *
     * @return encryptedData
     */
    public String getEncryptedData() {
        return encryptedData;
    }

    /**
     * 设置 encryptedData
     *
     * @param encryptedData encryptedData
     */
    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }

    /**
     * 获取 transportKey
     *
     * @return transportKey
     */
    public String getTransportKey() {
        return transportKey;
    }

    /**
     * 设置 transportKey
     *
     * @param transportKey transportKey
     */
    public void setTransportKey(String transportKey) {
        this.transportKey = transportKey;
    }

    /**
     * 获取 keyLength
     *
     * @return keyLength
     */
    public String getKeyLength() {
        return keyLength;
    }

    /**
     * 设置 keyLength
     *
     * @param keyLength keyLength
     */
    public void setKeyLength(String keyLength) {
        this.keyLength = keyLength;
    }

    /**
     * 获取 encode
     *
     * @return encode
     */
    public String getEncode() {
        return encode;
    }

    /**
     * 设置 encode
     *
     * @param encode encode
     */
    public void setEncode(String encode) {
        this.encode = encode;
    }

    /**
     * 获取 transportKey
     *
     * @return transportKey
     */
    public String getTransportKey() {
        return transportKey;
    }

    /**
     * 设置 transportKey
     *
     * @param transportKey transportKey
     */
    public void setTransportKey(String transportKey) {
        this.transportKey = transportKey;
    }

    /**
     * 获取 keyLength
     *
     * @return keyLength
     */
    public String getKeyLength() {
        return keyLength;
    }

    /**
     * 设置 keyLength
     *
     * @param keyLength keyLength
     */
    public void setKeyLength(String keyLength) {
        this.keyLength = keyLength;
    }

    /**
     * 获取 key
     *
     * @return key
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置 key
     *
     * @param key key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取 data
     *
     * @return data
     */
    public String getData() {
        return data;
    }

    /**
     * 设置 data
     *
     * @param data data
     */
    public void setData(String data) {
        this.data = data;
    }

    /**
     * 获取 timestamp
     *
     * @return timestamp
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * 设置 timestamp
     *
     * @param timestamp timestamp
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }


    }
}

