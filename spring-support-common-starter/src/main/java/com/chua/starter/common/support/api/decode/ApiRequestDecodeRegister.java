package com.chua.starter.common.support.api.decode;
import com.chua.common.support.lang.algorithm.crypto.Codec;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.common.support.application.GlobalSettingFactory;
import org.apache.commons.codec.binary.Hex;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 请求解码注册器
 * <p>
 * 处理请求解密和防重放攻击验证。
 * </p>
 *
 * @since 2024/12/07
 * @author CH
 */
public class ApiRequestDecodeRegister implements Upgrade<ApiRequestDecodeSetting>  {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(ApiRequestDecodeRegister.class);
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
    
    /**
     * 路径匹配器
     */
    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 全局设置工厂
    */
    private final GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
    /**
     * Decode配置
    */
    private final ApiProperties.RequestDecodeProperties decodeConfig;
    /**
     * White列表
    */
    private final List<String> whiteList;

    /**
     * 请求codec
    */
    private Codec requestCodec;
    /**
     * Decode设置
    */
    private ApiRequestDecodeSetting decodeSetting;
    /**
     * 请求codec密钥
    */
    private String requestCodecKey;

    /**
     * 请求codec类型（生效值：sys_setting CodecType 优先，回落 yaml codecType）
     */
    private String requestCodecType;

    /**
     * 当前生效类型是否 keylog
     */
    private boolean keylogCodec;

    /**
     * keylog 服务端长期密钥提供器
     */
    private final KeyLogServerKeyProvider keyLogServerKeyProvider;

    /**
     * 请求帧 attribute 名：decode advice 暂存本次请求的密文帧（k2/hex），
     * 供响应加密在 keylog 回包时复用请求侧共享密钥。
     */
    public static final String REQUEST_CODEC_FRAME_ATTRIBUTE = "API_REQUEST_CODEC_FRAME";

    /**
     * 构造函数
     *
     * @param decodeConfig 解码配置
     */
    public ApiRequestDecodeRegister(ApiProperties.RequestDecodeProperties decodeConfig) {
        this(decodeConfig, null);
    }

    /**
     * 构造函数
     *
     * @param decodeConfig            解码配置
     * @param keyLogServerKeyProvider keylog 服务端长期密钥提供器；null 时按 yaml keylogKeyFile 兜底
     */
    public ApiRequestDecodeRegister(ApiProperties.RequestDecodeProperties decodeConfig,
                                    KeyLogServerKeyProvider keyLogServerKeyProvider) {
        this.decodeConfig = decodeConfig;
        this.keyLogServerKeyProvider = null != keyLogServerKeyProvider
                ? keyLogServerKeyProvider
                : new KeyLogServerKeyProvider(decodeConfig.getKeylogKeyFile());
        this.whiteList = decodeConfig.getWhiteList() != null 
                ? decodeConfig.getWhiteList() 
                : Collections.emptyList();
        if (!decodeConfig.isExtInject()) {
            ApiRequestDecodeSetting setting = getOrCreateSetting();
            setting.setEnable(decodeConfig.isEnable());
            setting.setCodecRequestKey(decodeConfig.getCodecRequestKey());
            this.upgrade(setting);
        }
    }

    /**
     * 请求解密是否开启
     *
     * @return 是否开启
     */
    public boolean requestDecodeOpen() {
        /**
         * 检查。
         */
        check();
        return null != decodeSetting && decodeSetting.isEnable();
    }

    /**
     * codec 分段传输（traceId 分片）是否开启
     *
     * @return 是否开启
     */
    public boolean isTraceOpen() {
        /**
         * 检查。
         */
        check();
        return null != decodeSetting && decodeSetting.isCodecTraceOpen();
    }

    /**
     * 校验
    */
    private void check() {
        ApiRequestDecodeSetting setting = getOrCreateSetting();
        if (null == setting) {
            setting = globalSettingFactory.get("decode", ApiRequestDecodeSetting.class);
        }
        if (null == setting) {
            setting = new ApiRequestDecodeSetting();
            setting.setEnable(decodeConfig.isEnable());
            setting.setCodecRequestKey(decodeConfig.getCodecRequestKey());
        }
        this.upgrade(setting);
    }

    /**
     * 获取或创建Setting
     *
     * @return 获取or创建setting的结果
     */
    private ApiRequestDecodeSetting getOrCreateSetting() {
        ApiRequestDecodeSetting setting = globalSettingFactory.get("config", ApiRequestDecodeSetting.class);
        if (setting == null) {
            setting = new ApiRequestDecodeSetting();
            globalSettingFactory.register("config", setting);
        }
        return setting;
    }

    /**
     * 检查请求路径是否在白名单中
     *
     * @param requestPath 请求路径
     * @return 是否在白名单中
     */
    public boolean isWhiteListed(String requestPath) {
        if (whiteList.isEmpty() || StringUtils.isEmpty(requestPath)) {
            return false;
        }
        for (String pattern : whiteList) {
            if (PATH_MATCHER.match(pattern, requestPath)) {
                log.debug("[RequestDecode] 路径 {} 匹配白名单规则 {}", requestPath, pattern);
                return true;
            }
        }
        return false;
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
     * 获取请求Key
     *
     * @return 请求key
     */
    public String getRequestKey() {
        /**
         * 检查。
         */
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
                /**
                 * runtime异常。
                 * @param "解密器未初始化" "解密器未初始化"
                 * @return runtime异常的结果
                 */
                throw new RuntimeException("解密器未初始化");
            }
            String decoded = requestCodec.decodeHex(data);
            return decoded.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            /**
             * runtime异常。
             * @param e.getMessage( e.获取消息(
             * @return runtime异常的结果
             */
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
            /**
             * cleanupexpiredRequests。
             */
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
    /**
     * Upgrade
     * @param setting 参数
     * @return 返回值
    */
    public void upgrade(ApiRequestDecodeSetting setting) {
        String effectiveType = resolveEffectiveCodecType(setting);
        this.decodeSetting = setting;
        boolean enable = null != setting && setting.isEnable();
        boolean nextKeylog = "keylog".equalsIgnoreCase(effectiveType);
        // keylog 的密钥是服务端长期密钥对（pair:pub:priv），与共享密钥 CodecRequestKey 无关
        String nextRequestCodecKey = nextKeylog
                ? keyLogServerKeyProvider.getPairSpec()
                : (null == setting ? null : setting.getCodecRequestKey());

        if (null != requestCodec
                && (!enable
                || StringUtils.isEmpty(nextRequestCodecKey)
                || !nextRequestCodecKey.equals(this.requestCodecKey)
                || !effectiveType.equals(this.requestCodecType))) {
            requestCodec = null;
        }

        this.requestCodecKey = nextRequestCodecKey;
        this.requestCodecType = effectiveType;
        this.keylogCodec = nextKeylog;

        if (!enable || StringUtils.isEmpty(nextRequestCodecKey)) {
            return;
        }

        if (null != requestCodec) {
            return;
        }

        requestCodec = Codec.build(effectiveType, this.requestCodecKey);
    }

    /**
     * 解析生效的 codec 类型：sys_setting CodecType 优先，空回落 yaml codecType。
     *
     * @param setting 当前设置
     * @return 生效类型（小写）
     */
    private String resolveEffectiveCodecType(ApiRequestDecodeSetting setting) {
        String fallback = decodeConfig.getCodecType();
        return null == setting
                ? ApiRequestDecodeSetting.requireSupportedCodecType(fallback)
                : setting.effectiveCodecType(fallback);
    }

    /**
     * 当前生效 codec 是否 keylog（响应加密据此绑定请求加密）。
     *
     * @return 是否 keylog
     */
    public boolean isKeylogCodec() {
        check();
        return keylogCodec;
    }

    /**
     * 接口强制签名开关（sys_setting ApiSignOpen）：开启后缺签名头/坏签名一律拒绝。
     *
     * @return 是否强制签名
     */
    public boolean isApiSignOpen() {
        check();
        return null != decodeSetting && decodeSetting.isApiSignOpen();
    }

    /**
     * 当前生效的 codec 类型。
     *
     * @return 生效类型（小写），未生效时为 null
     */
    public String getRequestCodecType() {
        check();
        return requestCodecType;
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
