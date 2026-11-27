package com.chua.starter.datapool.support.support;

import com.chua.common.support.lang.algorithm.crypto.AesGcmCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.chua.common.support.utils.StringUtils;

/**
 * 数据源口令的存储编解码。
 *
 * <p>台账里的口令有三种存法，靠前缀区分，因此历史数据不需要迁移：</p>
 * <ul>
 *   <li>{@code enc:v1:} —— 本版本起的 AES-GCM 密文（配置了密钥时写入）；</li>
 *   <li>{@code b64:} —— 历史从采集平台（{@code collect_datasource}）迁来的 Base64；</li>
 *   <li>无前缀 —— 明文，早期版本建的台账。</li>
 * </ul>
 *
 * <p><strong>密钥缺失时不加密，而不是用一个内置默认密钥。</strong> 内置默认密钥等于把
 * 「加密」做成一层障眼法：任何拿到代码的人都能解，还会让人误以为口令是安全的。
 * 没配密钥就维持原来的明文存储（行为不变、不会让已有数据变得解不开），
 * 同时在日志里说清楚。</p>
 *
 * <p><strong>密钥一旦配置就不要随意更换。</strong> 换了密钥，此前写下的
 * {@code enc:v1:} 值就再也解不开了 —— 那些数据源的口令只能重新录入。
 * 密钥本身放在配置文件/环境变量里，不进代码库。</p>
 *
 * <p>AES-GCM 运算与密钥派生统一委托给底层 {@link AesGcmCodec}
 * （SHA-256 派生 32 字节密钥、12 字节 IV、128 位认证标签，与历史格式字节兼容），
 * 本类只负责 {@code enc:v1:}+Base64 封装与降级策略。</p>
 *
 * <p>接口出参不回传口令明文，页面只显示「是否已设置」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DatasourceSecrets {

    /**
     * 历史编码值前缀（Base64）。
     */
    private static final String PREFIX_B64 = "b64:";

    /**
     * 密文前缀。带 {@code v1} 是为了将来换算法/换格式时能按版本分支解码。
     */
    private static final String PREFIX_ENC = "enc:v1:";

    /**
     * GCM IV 长度（字节），与底层 {@link AesGcmCodec} 的 IV 长度一致。
     */
    private static final int IV_LENGTH = 12;

    /**
     * 配置的密钥原文；空白表示未启用加密。
     */
    private static volatile String secretKey = "";

    /**
     * 未配置密钥时的告警只打一次，避免每个数据源一条。
     */
    private static volatile boolean plaintextWarned = false;

    /**
     * 工具类禁止实例化。
     */
    private DatasourceSecrets() {
    }

    /**
     * 注入密钥原文（由配置绑定后调用）。
     *
     * @param key 密钥原文，空白表示不启用加密
     */
    public static void setSecretKey(String key) {
        secretKey = key == null ? "" : key.trim();
    }

    /**
     * 是否已启用加密。
     *
     * @return true=配置了密钥，新写入的口令会加密
     */
    public static boolean isEncryptionEnabled() {
        return !secretKey.isEmpty();
    }

    /**
     * 明文口令转为存储值。
     *
     * <p>空口令存空串（很多数据源本来就没有口令，加密一个空串只会让判断变复杂）；
     * 未配置密钥时按原样存，行为与旧版本一致。</p>
     *
     * @param plain 明文口令，可为 {@code null}
     * @return 存储值
     */
    public static String encode(String plain) {
        if (StringUtils.isEmpty(plain)) {
            return "";
        }
        if (!isEncryptionEnabled()) {
            if (!plaintextWarned) {
                plaintextWarned = true;
                log.warn("[datapool] 未配置 plugin.datapool.secret-key，"
                        + "数据源口令将以明文入库。生产环境请配置密钥后再录入口令。");
            }
            return plain;
        }
        try {
            byte[] combined = codec().encryptToCombined(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX_ENC + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            // 加密失败不能把明文写库：宁可让这次保存失败，也不能悄悄降级成明文
            throw new IllegalStateException("数据源口令加密失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 存储值还原为明文。
     *
     * @param stored 存储值，可为密文、{@code b64:} 编码值或明文
     * @return 明文口令，空白输入返回空串
     */
    public static String decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        String text = stored.trim();
        if (text.startsWith(PREFIX_ENC)) {
            return decodeEncrypted(text);
        }
        if (!text.startsWith(PREFIX_B64)) {
            return text;
        }
        try {
            return new String(Base64.getDecoder().decode(text.substring(PREFIX_B64.length())), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // 不是合法 Base64 时按原值处理，避免把用户口令直接改坏
            return text;
        }
    }

    /**
     * 解密 {@code enc:v1:} 值。
     *
     * <p>解不开时返回空串而不是密文本身：密文当口令去连库只会得到一个莫名其妙的
     * 认证失败，而空串加上这条告警能让人立刻想到「密钥配错了」。</p>
     *
     * @param text 完整存储值
     * @return 明文口令，失败时为空串
     */
    private static String decodeEncrypted(String text) {
        if (!isEncryptionEnabled()) {
            log.warn("[datapool] 台账里存在加密口令，但当前未配置 "
                    + "plugin.datapool.secret-key，无法解密");
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(text.substring(PREFIX_ENC.length()));
            if (combined.length <= IV_LENGTH) {
                log.warn("[datapool] 加密口令长度异常，无法解密");
                return "";
            }
            return new String(codec().decryptCombined(combined), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("[datapool] 加密口令解密失败（密钥被更换过？）: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 以当前密钥构造底层编解码器。
     *
     * <p>实例持有密钥字节，而密钥可经 {@link #setSecretKey(String)} 刷新，
     * 因此每次按当前 {@code secretKey} 新建，不做跨调用缓存。</p>
     *
     * @return 已注入密钥的 AES-GCM 编解码器
     */
    private static AesGcmCodec codec() {
        AesGcmCodec codec = new AesGcmCodec();
        codec.key(secretKey);
        return codec;
    }
}
