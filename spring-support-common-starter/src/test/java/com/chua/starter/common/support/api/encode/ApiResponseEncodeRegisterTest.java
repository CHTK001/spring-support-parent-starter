package com.chua.starter.common.support.api.encode;

import com.chua.common.support.application.GlobalSettingFactory;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.lang.algorithm.crypto.Codec;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeSetting;
import com.chua.starter.common.support.api.properties.ApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 响应加密：真实算法可还原、未配置密钥走显式 hex、未知类型失败不降级。
 *
 * <p>「未知类型抛异常」是静默降级的敏感性对照：若把 catch 改回返回明文，
 * 该用例会失败。</p>
 *
 * @author CH
 * @since 4.0.0.50
 */
class ApiResponseEncodeRegisterTest {

    /**
     * 测试密钥
     */
    private static final String KEY = "response-codec-key";

    /**
     * 测试明文
     */
    private static final String PLAIN = "{\"name\":\"月英\"}";

    /**
     * 全局设置中的请求/响应共用密钥；同一实例被注册一次后直接改值
     */
    private static final ApiRequestDecodeSetting SETTING = new ApiRequestDecodeSetting();

    static {
        GlobalSettingFactory.getInstance().register("config", SETTING);
    }

    /**
     * 每个用例前重置共享密钥。
     */
    @BeforeEach
    void resetKey() {
        SETTING.setCodecRequestKey(KEY);
    }

    /**
     * 默认 aes 类型加密后可被同一 Codec 解回。
     */
    @Test
    @DisplayName("aes 加密可还原")
    void aesRoundTrip() {
        ApiResponseEncodeRegister register = register("aes");
        ApiResponseEncodeRegister.CodecResult result = register.encode(PLAIN);
        assertEquals(PLAIN, Codec.build("aes", KEY).decodeHex(result.getData()));
    }

    /**
     * 未配置密钥时走显式 hex 兼容路径（不是加密）。
     */
    @Test
    @DisplayName("无密钥走显式 hex")
    void noKeyUsesExplicitHex() {
        SETTING.setCodecRequestKey(null);
        ApiResponseEncodeRegister register = register("aes");
        ApiResponseEncodeRegister.CodecResult result = register.encode(PLAIN);
        assertEquals(Hex.encodeHexString(PLAIN.getBytes(StandardCharsets.UTF_8)), result.getData());
    }

    /**
     * 未知类型必须失败，不能回退明文。
     */
    @Test
    @DisplayName("未知类型即失败")
    void unknownTypeFailsClosed() {
        ApiResponseEncodeRegister register = register("rc4");
        assertThrows(IllegalStateException.class, () -> register.encode(PLAIN));
    }

    /**
     * 构造注册器。
     *
     * @param codecType 编解码类型
     * @return 注册器
     */
    private static ApiResponseEncodeRegister register(String codecType) {
        ApiProperties.ResponseEncodeProperties properties = new ApiProperties.ResponseEncodeProperties();
        properties.setCodecType(codecType);
        return new ApiResponseEncodeRegister(properties);
    }
}
