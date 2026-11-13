package com.chua.starter.common.support.api.encode;

import com.chua.common.support.application.GlobalSettingFactory;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.lang.algorithm.crypto.Codec;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeSetting;
import com.chua.starter.common.support.api.properties.ApiProperties;
import org.springframework.context.ApplicationListener;

import java.util.Collections;
import java.util.List;

/**
 * 响应编码注册器。
 *
 * <p>提供响应数据加密功能。加密类型由 {@code plugin.api.encode.codec-type} 指定，
 * 真实实现由 {@link Codec} 的 SPI 解析；<b>加密失败不会再退回明文</b>。</p>
 *
 * <p>历史版本对 {@code sm2} 分支现场生成一次性密钥对，并把私钥塞进响应结果，
 * 任何拿到响应的人都能解密；该分支已删除。</p>
 *
 * @since 2024/01/22
 * @author CH
 */
public class ApiResponseEncodeRegister
        implements Upgrade<ApiResponseEncodeConfiguration>, ApplicationListener<ApiResponseEncodeConfiguration> {

    /**
     * 日志
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ApiResponseEncodeRegister.class);

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
     *
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
            String codecKey = resolveCodecKey();
            // 有密钥时按配置类型走真实加密（前端 encodeRequestBody 同一口径）；
            // 没有密钥时使用显式的 hex 兼容路径，避免把明文误当密文。
            String effectiveType = (codecKey == null || codecKey.isBlank()) ? "hex" : codecType;
            String encryptedData = Codec.build(effectiveType, codecKey).encodeHex(data);
            return new CodecResult(String.valueOf(effectiveType.length()), encryptedData,
                    String.valueOf(effectiveType.length()));
        } catch (Exception e) {
            log.error("[CodecFactory] 响应加密失败", e);
            throw new IllegalStateException("响应加密失败（不会降级为明文）: " + e.getMessage(), e);
        }
    }

    /**
     * 响应加密密钥。
     *
     * <p>系统配置只维护一份 {@code CodecRequestKey}，请求解密和响应加密共用它；
     * 没有单独再暴露响应密钥，避免 UI 出现两个必须保持一致的密钥输入框。</p>
     *
     * @return 当前 codec 密钥，未配置时为 null
     */
    private String resolveCodecKey() {
        ApiRequestDecodeSetting setting = GlobalSettingFactory.getInstance()
                .get("config", ApiRequestDecodeSetting.class);
        return setting == null ? null : setting.getCodecRequestKey();
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
        upgrade(event);
    }

    /**
     * 获取或创建配置
     *
     * @return 获取or创建配置的结果
     */
    private ApiResponseEncodeConfiguration getOrCreateConfiguration() {
        ApiResponseEncodeConfiguration configuration = GlobalSettingFactory.getInstance()
                .get("config", ApiResponseEncodeConfiguration.class);
        if (configuration == null) {
            configuration = new ApiResponseEncodeConfiguration();
            GlobalSettingFactory.getInstance().register("config", configuration);
        }
        return configuration;
    }

    /**
     * 编码结果
     *
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
