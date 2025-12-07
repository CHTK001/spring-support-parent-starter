package com.chua.starter.common.support.api.decode;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 请求解码注册器
 * <p>
 * 处理请求解密和防重放攻击验证。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 * @version 1.0.0
 */
@Slf4j
public class ApiRequestDecodeRegister implements Upgrade<ApiRequestDecodeSetting> {

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

    private final GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
    private final ApiProperties.RequestDecodeProperties decodeConfig;

    private Codec requestCodec;
    private ApiRequestDecodeSetting decodeSetting;
    private String requestCodecKey;

    /**
     * 构造函数
     *
     * @param decodeConfig 解码配置
     */
    public ApiRequestDecodeRegister(ApiProperties.RequestDecodeProperties decodeConfig) {
        this.decodeConfig = decodeConfig;
        if (!decodeConfig.isExtInject()) {
            globalSettingFactory.register("config", new ApiRequestDecodeSetting());
            globalSettingFactory.setIfNoChange("config", "codecRequestOpen", decodeConfig.isEnable());
            this.upgrade(globalSettingFactory.get("config", ApiRequestDecodeSetting.class));
        }
    }

    /**
     * 请求解密是否开启
     *
     * @return 是否开启
     */
    public boolean requestDecodeOpen() {
        check();
        return null != decodeSetting && decodeSetting.isEnable();
    }

    private void check() {
        if (null != decodeSetting) {
            return;
        }
        this.upgrade(globalSettingFactory.get("decode", ApiRequestDecodeSetting.class));
    }

    /**
     * 获取密钥头
     *
     * @return 密钥头名称
     */
    public String getKeyHeader() {
        return "access-control-origin-key";
    }

    /**
     * 获取请求key
     *
     * @return 请求key
     */
    public String getRequestKey() {
        check();
        return decodeSetting != null ? decodeSetting.getCodecRequestKey() : null;
    }

    /**
     * 解密请求
     *
     * @param data 加密数据
     * @return 解密后的字节数组
     */
    public byte[] decodeRequest(String data) {
        try {
            if (requestCodec == null) {
                throw new RuntimeException("解密器未初始化");
            }
            return requestCodec.decode(Hex.decodeHex(data));
        } catch (Exception e) {
            throw new RuntimeException("请求解析失败: " + e.getMessage());
        }
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

            if (Math.abs(currentTime - requestTime) > REQUEST_TIMESTAMP_TTL_MS) {
                log.warn("[AntiReplay] 请求时间戳超出有效范围： {}, 当前时间: {}", requestTime, currentTime);
                return false;
            }

            String requestId = timestamp + "_" + nonce;

            if (requestTimestampStore.containsKey(requestId)) {
                log.warn("[AntiReplay] 检测到重放攻击，请求ID: {}", requestId);
                return false;
            }

            requestTimestampStore.put(requestId, currentTime);
            cleanupExpiredRequests();

            log.debug("[AntiReplay] 请求验证通过，请求ID: {}", requestId);
            return true;

        } catch (NumberFormatException e) {
            log.warn("[AntiReplay] 时间戳格式错误： {}", timestamp);
            return false;
        } catch (Exception e) {
            log.error("[AntiReplay] 验证请求时发生错误", e);
            return false;
        }
    }

    @Override
    public void upgrade(ApiRequestDecodeSetting setting) {
        this.decodeSetting = setting;
        if (null != requestCodec) {
            IoUtils.closeQuietly(requestCodec);
            requestCodec = null;
        }
        if (null == setting || null == setting.getCodecRequestKey()) {
            return;
        }
        if (setting.getCodecRequestKey().equals(this.requestCodecKey)) {
            return;
        }
        if (!setting.isEnable()) {
            return;
        }
        this.requestCodecKey = setting.getCodecRequestKey();
        requestCodec = Codec.build(decodeConfig.getCodecType(), this.requestCodecKey);
    }

    /**
     * 清理过期的请求记录
     */
    private void cleanupExpiredRequests() {
        try {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;

            for (Map.Entry<String, Long> entry : requestTimestampStore.entrySet()) {
                if (currentTime - entry.getValue() > REQUEST_TIMESTAMP_TTL_MS) {
                    requestTimestampStore.remove(entry.getKey());
                    removedCount++;
                }
            }

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
                log.debug("[AntiReplay-Cleanup] 清理了{} 个过期/多余的请求记录，当前存储: {}",
                        removedCount, requestTimestampStore.size());
            }
        } catch (Exception e) {
            log.error("[AntiReplay-Cleanup] 清理请求记录时发生错误", e);
        }
    }
}

