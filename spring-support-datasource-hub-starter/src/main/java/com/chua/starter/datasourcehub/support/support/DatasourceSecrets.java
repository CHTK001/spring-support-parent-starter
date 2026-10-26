package com.chua.starter.datasourcehub.support.support;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

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
     * 加密算法：AES-GCM，带认证标签，密文被篡改会解不开而不是解出垃圾。
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * GCM 推荐的 IV 长度（字节）。
     */
    private static final int IV_LENGTH = 12;

    /**
     * 认证标签长度（位）。
     */
    private static final int TAG_BITS = 128;

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
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (!isEncryptionEnabled()) {
            if (!plaintextWarned) {
                plaintextWarned = true;
                log.warn("[datasource-hub] 未配置 plugin.datasource.hub.secret-key，"
                        + "数据源口令将以明文入库。生产环境请配置密钥后再录入口令。");
            }
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
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
            log.warn("[datasource-hub] 台账里存在加密口令，但当前未配置 "
                    + "plugin.datasource.hub.secret-key，无法解密");
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(text.substring(PREFIX_ENC.length()));
            if (combined.length <= IV_LENGTH) {
                log.warn("[datasource-hub] 加密口令长度异常，无法解密");
                return "";
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, combined, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("[datasource-hub] 加密口令解密失败（密钥被更换过？）: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * 由密钥原文派生 32 字节 AES 密钥。
     *
     * <p>用 SHA-256 而不是直接取前 32 字节：用户配置的密钥长度不受控，
     * 短密钥补零会显著削弱强度，哈希之后长度固定且分布均匀。</p>
     *
     * @return 32 字节密钥
     * @throws Exception 摘要算法不可用（实际不会发生）
     */
    private static byte[] deriveKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
