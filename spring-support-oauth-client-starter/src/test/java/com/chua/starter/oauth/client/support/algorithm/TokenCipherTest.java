package com.chua.starter.oauth.client.support.algorithm;

import com.chua.starter.oauth.client.support.user.UserResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌编解码单元测试
 * <p>覆盖 SM4/ECB + Hex 的统一加解密：往返一致性、确定性、非法输入容错。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class TokenCipherTest {

    /**
     * 构造测试用户
     *
     * @return 测试用户
     */
    private UserResult buildUser() {
        UserResult user = new UserResult();
        user.setUsername("admin");
        user.setUserId("1");
        user.setLoginType("WEB");
        user.setNickName("管理员");
        user.setRoles(Set.of("ADMIN"));
        return user;
    }

    /**
     * 加密后再解密可还原明文
     */
    @Test
    void encryptDecryptRoundTrip() {
        UserResult user = buildUser();
        String token = TokenCipher.encrypt(user);
        assertNotNull(token, "加密结果不应为 null");
        assertTrue(token.length() > 0, "加密结果不应为空");

        String plain = TokenCipher.decrypt(token);
        assertNotNull(plain, "解密结果不应为 null");
        assertTrue(plain.contains("admin"), "解密明文应包含用户信息");
    }

    /**
     * 加密后直接解析为用户信息，字段保持一致
     */
    @Test
    void encryptDecryptToUser() {
        UserResult user = buildUser();
        String token = TokenCipher.encrypt(user);
        UserResult decoded = TokenCipher.decryptToUser(token);
        assertNotNull(decoded, "解密解析结果不应为 null");
        assertEquals("admin", decoded.getUsername());
        assertEquals("1", decoded.getUserId());
        assertEquals("WEB", decoded.getLoginType());
    }

    /**
     * SM4/ECB 模式下相同输入生成相同令牌（确定性）
     */
    @Test
    void deterministicSameInputSameToken() {
        String token1 = TokenCipher.encrypt(buildUser());
        String token2 = TokenCipher.encrypt(buildUser());
        assertEquals(token1, token2, "相同用户信息应生成相同令牌");
    }

    /**
     * 加密结果应为十六进制字符串
     */
    @Test
    void encryptProducesHex() {
        String token = TokenCipher.encrypt("hello");
        assertNotNull(token);
        assertTrue(token.matches("[0-9a-fA-F]+"), "令牌应为十六进制字符串");
    }

    /**
     * 非法令牌解密返回 null，不抛异常
     */
    @Test
    void invalidHexReturnsNull() {
        assertNull(TokenCipher.decrypt("not-hex!!"));
        assertNull(TokenCipher.decrypt(""));
        assertNull(TokenCipher.decrypt(null));
        assertNull(TokenCipher.decryptToUser("deadbeef"));
    }

    /**
     * null 输入返回 null
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(TokenCipher.encrypt((String) null));
        assertNull(TokenCipher.encrypt((UserResult) null));
    }
}
