package com.chua.starter.common.support.api.encode;
import com.chua.common.support.lang.algorithm.cipher.Sm2Cipher;
import com.chua.starter.common.support.algorithm.crypto.Codec;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.common.support.application.GlobalSettingFactory;
import lombok.Getter;
import org.springframework.context.ApplicationListener;

import java.security.KeyPair;
import java.util.Collections;
import java.util.List;

/**
 * 响应编码注册器
 * <p>
 * 提供响应数据加密功能。
 * </p>
 *
 * @since 2024/01/22
 * @author CH
 */
public class ApiResponseEncodeRegister implements Upgrade<ApiResponseEncodeConfiguration>, ApplicationListener<ApiResponseEncodeConfiguration>  {
    /**
     * 日志
    */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiResponseEncodeRegister.class);
    /**
     * White列表
    */
    private final List<String> whiteList;
    /**
     * Codec类型
    */
    private final String codecType;
    /**
     * API响应encodeconfiguration
    */
    private ApiResponseEncodeConfiguration apiResponseEncodeConfiguration;

    /**
     * 编解码器类型
     * @param codecType codec类型
     * @param whiteList white列表
     */
    private ApiResponseEncodeRegister(String codecType, List<String> whiteList) {
        this.codecType = codecType;
        this.whiteList = whiteList;
    }

    /**
     * 构造函数
     *
     * @param responseEncodePropertiesConfig 编解码配置
     */
    public ApiResponseEncodeRegister(ApiProperties.ResponseEncodeProperties responseEncodePropertiesConfig) {
        this.codecType = responseEncodePropertiesConfig.getCodecType();
        this.whiteList = responseEncodePropertiesConfig.getWhiteList() == null
                ? Collections.emptyList()
                : responseEncodePropertiesConfig.getWhiteList();
        ApiResponseEncodeConfiguration configuration = getOrCreateConfiguration();
        if (!responseEncodePropertiesConfig.isExtInject()) {
            configuration.setCodecResponseOpen(responseEncodePropertiesConfig.isResponseEnable());
        }
        this.apiResponseEncodeConfiguration = configuration;
    }

    /**
     * 是否通过
     *
     * @return 是否通过的结果
     */
    public boolean isPass() {
        /**
         * 检查。
         */
        check();
        return !apiResponseEncodeConfiguration.isCodecResponseOpen();
    }

    /**
     * 校验
    */
    private void check() {
        if (null != apiResponseEncodeConfiguration) {
            return;
        }
        this.apiResponseEncodeConfiguration = getOrCreateConfiguration();
    }

    /**
     * 编码数据
     *
     * @param data 数据
     * @return 编码结果
     */
    public CodecResult encode(String data) {
        try {
            if ("sm2".equalsIgnoreCase(codecType)) {
                return sm2Encode(data);
            }
            String encryptedData = Codec.build("sm4", codecType).encodeHex(data);
            /**
             * codec结果。
             * @param codecType codec类型
             * @param encryptedData encrypted数据
             * @param String.valueOf(codecType.length( 字符串.值的(codec类型.长度(
             * @return codec结果的结果
             */
            return new CodecResult(codecType, encryptedData, String.valueOf(codecType.length()));
        } catch (Exception e) {
            log.error("[CodecFactory] 数据加密失败", e);
            /**
             * codec结果。
             * @param "" ""
             * @param data 数据
             * @param String.valueOf(0 字符串.值的(0
             * @return codec结果的结果
             */
            return new CodecResult("", data, String.valueOf(0));
        }
    }

    /**
     * Sm编码
     *
     * @param data 数据
     * @return sm2Encode的结果
     */
    private CodecResult sm2Encode(String data) {
        try {
            Sm2Cipher sm2Cipher = Sm2Cipher.create("bc");
            KeyPair keyPair = sm2Cipher.generateKeyPair();
            byte[] encrypted = sm2Cipher.encrypt(keyPair.getPublic().getEncoded(), data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encryptedData = bytesToHex(encrypted);
            String privateKeyHex = bytesToHex(keyPair.getPrivate().getEncoded());
            /**
             * codec结果。
             * @param privateKeyHex 私募Keyhex
             * @param encryptedData encrypted数据
             * @param String.valueOf(privateKeyHex.length( 字符串.valueof(私募Keyhex.长度(
             * @return codec结果的结果
             */
            return new CodecResult(privateKeyHex, encryptedData, String.valueOf(privateKeyHex.length()));
        } catch (Exception e) {
            log.error("[CodecFactory] SM2 数据加密失败", e);
            /**
             * codec结果。
             * @param "" ""
             * @param data 数据
             * @param String.valueOf(0 字符串.值的(0
             * @return codec结果的结果
             */
            return new CodecResult("", data, String.valueOf(0));
        }
    }

    /**
     * bytes转为hex
     *
     * @param bytes bytes
     * @return bytes转为hex的结果
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 设置是否启用
     *
     * @param parseBoolean 是否启用
     */
    public void setEnable(boolean parseBoolean) {
        /**
         * 检查。
         */
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
    /**
     * Upgrade
     * @param apiResponseEncodeConfiguration 参数
     * @return 返回值
    */
    public void upgrade(ApiResponseEncodeConfiguration apiResponseEncodeConfiguration) {
        this.apiResponseEncodeConfiguration = apiResponseEncodeConfiguration;
    }

    @Override
    /**
     * onapplication事件
     * @param event 参数
     * @return 返回值
    */
    public void onApplicationEvent(ApiResponseEncodeConfiguration event) {
        /**
         * upgrade。
         * @param event 事件
         */
        upgrade(event);
    }

    /**
     * 获取或创建配置
     *
     * @return 获取or创建配置的结果
     */
    private ApiResponseEncodeConfiguration getOrCreateConfiguration() {
        ApiResponseEncodeConfiguration configuration = GlobalSettingFactory.getInstance().get("config", ApiResponseEncodeConfiguration.class);
        if (configuration == null) {
            configuration = new ApiResponseEncodeConfiguration();
            GlobalSettingFactory.getInstance().register("config", configuration);
        }
        return configuration;
    }

    /**
     * 编码结果
     * @author CH
     * @since 4.0.0
     */
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
         * 额外字段
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

        /**
         * 获取Key
         *
         * @return 获取Key的结果
         */
        public String getKey() {
            return key;
        }

        /**
         * 获取数据
         *
         * @return 获取数据的结果
         */
        public String getData() {
            return data;
        }

        /**
         * 获取时间戳
         *
         * @return 获取时间戳的结果
         */
        public String getTimestamp() {
            return timestamp;
        }
    }
}
