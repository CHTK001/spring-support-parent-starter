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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.context.ApplicationListener;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 编解码器提供程序 - 增强版（支持一次性密钥）
 *
 * @author CH
 * @version 2.0.0
 * @since 2024/01/22
 */
@Slf4j
public class CodecFactory implements Upgrade<CodecSetting>, ApplicationListener<CodecSetting> {

    // === OTK配置参数 ===
    private static final long OTK_TTL_MS = 300000L; // 5分钟过期
    private static final int OTK_MAX_SIZE = 10000; // 最大缓存数量
    private static final String OTK_PREFIX = "OTK_";
    private static final int OTK_LENGTH = 32; // OTK长度
    /**
     * 请求时间戳有效期(毫秒) - 默认10分钟
     */
    private static final long REQUEST_TIMESTAMP_TTL_MS = 10 * 60 * 1000L;
    /**
     * 请求时间戳最大存储数量
     */
    private static final int REQUEST_TIMESTAMP_MAX_SIZE = 50000;
    // === 一次性密钥(OTK)管理相关字段 ===
    private final Map<String, OtkEntry> otkStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    // === 反重放攻击保护相关字段 ===
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong otkCounter = new AtomicLong(0);
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

        // === 初始化OTK清理任务 ===
        initOtkCleanup();
        log.info("[CodecFactory] 一次性密钥系统已启动，TTL={}ms, MaxSize={}", OTK_TTL_MS, OTK_MAX_SIZE);
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
     * 编码 - 增强版（支持一次性密钥）
     *
     * @param data 数据
     * @return {@link CodecResult}
     */
    public CodecResult encode(String data) {
        try {
            // === 生成一次性密钥 ===
            String otkId = generateOtk();
            OtkEntry otkEntry = otkStore.get(otkId);

            if (otkEntry == null) {
                log.warn("[CodecFactory] OTK生成后立即失效，重新生成: {}", otkId);
                otkId = generateOtk();
                otkEntry = otkStore.get(otkId);
            }

            // === 使用OTK加密数据 ===
            String encryptedData = codecKeyPair.encode(data, otkEntry.getOtkKey());

            // === 生成传输密钥（用主密钥加密OTK） ===
            String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, "0");
            String transportKey = DigestUtils.aesEncrypt(otkEntry.getOtkKey(), nanoTime);

            // === 标记OTK为已使用 ===
            otkEntry.markUsed();

            log.debug("[CodecFactory] 数据加密完成，OTK_ID={}, 数据长度={}", otkId, data.length());
            return new CodecResult(transportKey, encryptedData, nanoTime, otkId);

        } catch (Exception e) {
            log.error("[CodecFactory] 数据加密失败", e);
            // === 降级到原始加密方式 ===
            String encode = codecKeyPair.encode(data, publicKeyHex);
            String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, "0");
            String encrypt = DigestUtils.aesEncrypt(codecKeyPair.getPrivateKeyHex(), nanoTime);
            return new CodecResult(encrypt, encode, nanoTime, null);
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

    /**
     * 初始化清理任务（包括OTK和反重放攻击记录）
     */
    private void initOtkCleanup() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                // 清理OTK
                cleanupExpiredOtk();

                // 清理反重放攻击记录
                cleanupExpiredRequests();

            } catch (Exception e) {
                log.error("[Cleanup] 定时清理任务执行失败", e);
            }
        }, OTK_TTL_MS / 2, OTK_TTL_MS / 2, TimeUnit.MILLISECONDS);

        log.info("[Cleanup-System] 定时清理任务已启动，清理间隔: {}ms", OTK_TTL_MS / 2);
    }

    /**
     * 清理过期的OTK
     */
    private void cleanupExpiredOtk() {
        try {
            long currentTime = System.currentTimeMillis();
            int removedCount = 0;

            // 清理过期的OTK
            Iterator<Map.Entry<String, OtkEntry>> iterator = otkStore.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, OtkEntry> entry = iterator.next();
                if (currentTime - entry.getValue().getCreateTime() > OTK_TTL_MS) {
                    iterator.remove();
                    removedCount++;
                }
            }

            // 如果存储超过最大限制，清理最旧的密钥
            if (otkStore.size() > OTK_MAX_SIZE) {
                List<Map.Entry<String, OtkEntry>> sortedEntries = otkStore.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue((o1, o2) -> Long.compare(o1.getCreateTime(), o2.getCreateTime())))
                        .collect(Collectors.toList());

                int toRemove = otkStore.size() - OTK_MAX_SIZE;
                for (int i = 0; i < toRemove; i++) {
                    otkStore.remove(sortedEntries.get(i).getKey());
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                log.debug("[OTK-Cleanup] 清理了 {} 个过期/多余的一次性密钥，当前存储: {}", removedCount, otkStore.size());
            }
        } catch (Exception e) {
            log.error("[OTK-Cleanup] 清理一次性密钥时发生错误", e);
        }
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
     * 生成一次性密钥
     */
    private String generateOtk() {
        String otkId = OTK_PREFIX + otkCounter.incrementAndGet() + "_" + System.currentTimeMillis();

        // 生成随机密钥
        byte[] keyBytes = new byte[OTK_LENGTH];
        secureRandom.nextBytes(keyBytes);
        String otkKey = Hex.encodeHexString(keyBytes);

        // 存储OTK
        OtkEntry otkEntry = new OtkEntry(otkKey, System.currentTimeMillis(), false);
        otkStore.put(otkId, otkEntry);

        log.debug("[OTK-Generate] 生成一次性密钥: {}, 当前存储数量: {}", otkId, otkStore.size());
        return otkId;
    }

    /**
     * 获取并验证一次性密钥
     */
    public String getAndValidateOtk(String otkId) {
        if (!StringUtils.hasText(otkId)) {
            log.warn("[OTK-Validate] 一次性密钥ID为空");
            return null;
        }

        OtkEntry otkEntry = otkStore.get(otkId);
        if (otkEntry == null) {
            log.warn("[OTK-Validate] 一次性密钥不存在: {}", otkId);
            return null;
        }

        // 检查是否已使用
        if (otkEntry.isUsed()) {
            log.warn("[OTK-Validate] 一次性密钥已被使用: {}", otkId);
            otkStore.remove(otkId); // 移除已使用的密钥
            return null;
        }

        // 检查是否过期
        long currentTime = System.currentTimeMillis();
        if (currentTime - otkEntry.getCreateTime() > OTK_TTL_MS) {
            log.warn("[OTK-Validate] 一次性密钥已过期: {}", otkId);
            otkStore.remove(otkId); // 移除过期的密钥
            return null;
        }

        // 标记为已使用并移除
        otkEntry.markUsed();
        otkStore.remove(otkId);

        log.debug("[OTK-Validate] 一次性密钥验证成功: {}", otkId);
        return otkEntry.getOtkKey();
    }

    /**
     * 生成带有一次性密钥的加密结果
     */
    public CodecResult encodeWithOtk(String data) {
        if (!StringUtils.hasText(data)) {
            return new CodecResult(null, null, String.valueOf(System.currentTimeMillis()), null);
        }

        try {
            // 生成一次性密钥
            String otkId = generateOtk();

            // 使用原有的编码逻辑
            CodecResult originalResult = encode(data);

            // 返回包含OTK ID的结果
            return new CodecResult(
                    originalResult.getKey(),
                    originalResult.getData(),
                    originalResult.getTimestamp(),
                    otkId
            );
        } catch (Exception e) {
            log.error("[OTK-Encode] 带一次性密钥的加密失败", e);
            return new CodecResult(null, null, String.valueOf(System.currentTimeMillis()), null);
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
            Iterator<Map.Entry<String, Long>> iterator = requestTimestampStore.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (currentTime - entry.getValue() > REQUEST_TIMESTAMP_TTL_MS) {
                    iterator.remove();
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
     * 一次性密钥条目
     */
    @Data
    @AllArgsConstructor
    public static class OtkEntry {
        private String otkKey;
        private long createTime;
        private boolean used;

        public void markUsed() {
            this.used = true;
        }
    }

    @Data
    @AllArgsConstructor
    public static class CodecResult {

        private String key;

        private String data;

        /**
         * 时间戳
         */
        private String timestamp;

        /**
         * 一次性密钥ID
         */
        private String otkId;

        // 兼容原有的三参数构造函数
        public CodecResult(String key, String data, String timestamp) {
            this(key, data, timestamp, null);
        }
    }
}
