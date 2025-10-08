package com.chua.starter.common.support.codec;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.properties.CodecProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.context.ApplicationListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 编解码器提供程序 - 简化版（去除一次性密钥）
 *
 * @author CH
 * @version 2.0.0
 * @since 2024/01/22
 */
@Slf4j
public class CodecFactory implements Upgrade<CodecSetting>, ApplicationListener<CodecSetting> {

    /**
     * 请求时间戳有效期(毫秒) - 默认10分钟
     */
    private static final long REQUEST_TIMESTAMP_TTL_MS = 10 * 60 * 1000L;
    /**
     * 请求时间戳最大存储数量
     */
    private static final int REQUEST_TIMESTAMP_MAX_SIZE = 50000;
    /**
     * 请求时间戳存储 - 用于防止重放攻击
     */
    private final ConcurrentHashMap<String, Long> requestTimestampStore = new ConcurrentHashMap<>();

    private final List<String> whiteList;
    private final Codec codec;
    private Codec requestCodec;
    private final CodecKeyPair codecKeyPair;
    private final String publicKeyHex;
    GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
    /**
     * 编解码器类型
     */
    private final String codecType = "sm2";
    private CodecSetting codecSetting;
    private String requestCodecKey;

    public CodecFactory(CodecProperties codecProperties) {
        boolean extInject = codecProperties.isExtInject();
        if(!extInject) {
            globalSettingFactory.register("config", new CodecSetting());
            globalSettingFactory.setIfNoChange("config", "codecResponseOpen", codecProperties.isRequestEnable());
            globalSettingFactory.setIfNoChange("config", "codecRequestOpen", codecProperties.isRequestEnable());
            globalSettingFactory.setIfNoChange("config", "codecRequestKey", codecProperties.getCodecRequestKey());
            this.upgrade(globalSettingFactory.get("config", CodecSetting.class));

        }
        this.codec = Codec.build(codecType);
        this.whiteList = codecProperties.getWhiteList();
        this.codecKeyPair = (CodecKeyPair) codec;
        this.publicKeyHex = codecKeyPair.getPublicKeyHex();
    }

    public boolean isPass() {
        check();
        return !codecSetting.isCodecResponseOpen();
    }

    /**
     *
     */
    private void check() {
        if(null != codecSetting) {
            return;
        }
        this.upgrade(globalSettingFactory.get("config", CodecSetting.class));
    }

    /**
     * 编码 - 简化版（去除一次性密钥）
     *
     * @param data 数据
     * @return {@link CodecResult}
     */
    public CodecResult encode(String data) {
        try {
            // === 直接使用主密钥加密数据 ===
            String encryptedData = codecKeyPair.encode(data, publicKeyHex);
            String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, "0");
            String transportKey = DigestUtils.aesEncrypt(codecKeyPair.getPrivateKeyHex(), nanoTime);
            
            log.debug("[CodecFactory] 数据加密完成，数据长度={}", data.length());
            return new CodecResult(transportKey, encryptedData, nanoTime);
        } catch (Exception e) {
            log.error("[CodecFactory] 数据加密失败", e);
            // === 降级到原始加密方式 ===
            String encode = codecKeyPair.encode(data, publicKeyHex);
            String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, "0");
            String encrypt = DigestUtils.aesEncrypt(codecKeyPair.getPrivateKeyHex(), nanoTime);
            return new CodecResult(encrypt, encode, nanoTime);
        }
    }

    /**
     * 设置是否启用
     *
     * @param parseBoolean 是否启用
     */
    public void setEnable(boolean parseBoolean) {
        check();
        codecSetting.setCodecResponseOpen(parseBoolean);
    }

    /**
     * 是否通过
     *
     * @param requestURI 请求uri
     * @return {@link boolean}
     */
    public boolean isPass(String requestURI) {
        if(isPass()) {
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
    public void upgrade(CodecSetting codecSetting) {
        this.codecSetting = codecSetting;
        if(null != requestCodec) {
            IoUtils.closeQuietly(requestCodec);
            requestCodec = null;
        }
        if(null == codecSetting.getCodecRequestKey()) {
            return;
        }

        if(codecSetting.getCodecRequestKey().equals(this.requestCodecKey)) {
            return;
        }

        if(!codecSetting.isCodecRequestOpen()) {
            return;
        }
        this.requestCodecKey = codecSetting.getCodecRequestKey();
        requestCodec = Codec.build("sm4", this.requestCodecKey);
    }

    /**
     * 获取密钥头
     * @return {@link String}
     */
    public String getKeyHeader() {
        return "access-control-origin-key";
    }

    /**
     * 获取请求key
     * @return {@link String}
     */
    public String getRequestKey() {
        return codecSetting.getCodecRequestKey();
    }

    @Override
    public void onApplicationEvent(CodecSetting event) {
        upgrade(event);
    }

    /**
     * 解密请求
     * @param data 数据
     * @return {@link byte[]}
     */
    public byte[] decodeRequest(String data) {
        try {
            return requestCodec.decode(Hex.decodeHex(data));
        } catch (Exception e) {
            throw new RuntimeException("请求解析失败!");
        }
    }

    /**
     * 请求是否开启
     * @return {@link boolean}
     */
    public boolean requestCodecOpen() {
        return null != codecSetting && codecSetting.isCodecRequestOpen();
    }

    /**
     * 验证请求防重放攻击
     *
     * @param timestamp 请求时间戳
     * @param nonce     随机数
     * @return 是否通过验证
     */
    public boolean validateAntiReplay(String timestamp, String nonce) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            log.warn("[AntiReplay] 时间戳或nonce为空");
            return false;
        }

        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();

            // 检查时间戳是否在有效范围内
            if (Math.abs(currentTime - requestTime) > REQUEST_TIMESTAMP_TTL_MS) {
                log.warn("[AntiReplay] 请求时间戳超出有效范围: {}, 当前时间: {}", requestTime, currentTime);
                return false;
            }

            // 生成唯一标识符
            String requestId = timestamp + "_" + nonce;

            // 检查是否已存在（重放攻击）
            if (requestTimestampStore.containsKey(requestId)) {
                log.warn("[AntiReplay] 检测到重放攻击，请求ID: {}", requestId);
                return false;
            }

            // 存储请求标识符
            requestTimestampStore.put(requestId, currentTime);

            // 清理过期的请求记录
            cleanupExpiredRequests();

            log.debug("[AntiReplay] 请求验证通过，请求ID: {}", requestId);
            return true;

        } catch (NumberFormatException e) {
            log.warn("[AntiReplay] 时间戳格式错误: {}", timestamp);
            return false;
        } catch (Exception e) {
            log.error("[AntiReplay] 验证请求时发生错误", e);
            return false;
        }
    }

    /**
     * 清理过期的请求记录
     */
    private void cleanupExpiredRequests() {
        try {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;

            // 清理过期的请求记录
            for (Map.Entry<String, Long> entry : requestTimestampStore.entrySet()) {
                if (currentTime - entry.getValue() > REQUEST_TIMESTAMP_TTL_MS) {
                    requestTimestampStore.remove(entry.getKey());
                    removedCount++;
                }
            }

            // 如果存储超过最大限制，清理最旧的记录
            if (requestTimestampStore.size() > REQUEST_TIMESTAMP_MAX_SIZE) {
                List<Map.Entry<String, Long>> sortedEntries = requestTimestampStore.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .collect(Collectors.toList());

                int toRemove = requestTimestampStore.size() - REQUEST_TIMESTAMP_MAX_SIZE;
                for (int i = 0; i < toRemove; i++) {
                    requestTimestampStore.remove(sortedEntries.get(i).getKey());
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                log.debug("[AntiReplay-Cleanup] 清理了 {} 个过期/多余的请求记录，当前存储: {}", removedCount, requestTimestampStore.size());
            }
        } catch (Exception e) {
            log.error("[AntiReplay-Cleanup] 清理请求记录时发生错误", e);
        }
    }

    /**
     * 编码结果
     */
    @Data
    public static class CodecResult {
        private String key;
        private String data;
        /**
         * 时间戳
         */
        private String timestamp;

        public CodecResult(String key, String data, String timestamp) {
            this.key = key;
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}