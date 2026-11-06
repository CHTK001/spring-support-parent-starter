package com.chua.starter.account.manage;

import com.chua.starter.account.ChannelRegistry;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.Model3DChannelProvider;
import com.chua.starter.account.spi.VideoChannelProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 账号模块新增能力的纯内存回归测试。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>文生视频 / 图生 3D 渠道已通过 SPI 注册；</li>
 *   <li>敏感值编解码器的加解密与掩码识别；</li>
 *   <li>账号种类词表的支持范围。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.52
 */
class AccountManageSpiTest {

    /**
     * 验证文生视频渠道注册（不触达网络）。
     */
    @Test
    void videoChannelsAreRegistered() {
        Map<String, VideoChannelProvider> channels = ChannelRegistry.videoChannels();
        Assertions.assertTrue(channels.containsKey("video-doubao"), "video-doubao 未注册");
        Assertions.assertTrue(channels.containsKey("video-qwen"), "video-qwen 未注册");
        Assertions.assertEquals("doubao-proxy", channels.get("video-doubao").extension());
        Assertions.assertEquals("qwen-proxy", channels.get("video-qwen").extension());
    }

    /**
     * 验证图生 3D 渠道注册（不触达网络）。
     */
    @Test
    void model3dChannelsAreRegistered() {
        Map<String, Model3DChannelProvider> channels = ChannelRegistry.model3dChannels();
        Assertions.assertTrue(channels.containsKey("model3d-forge"), "model3d-forge 未注册");
        Assertions.assertNull(channels.get("model3d-forge").extension(),
                "通用 Forge 渠道不应绑定 ChatClient 扩展");
    }

    /**
     * 验证敏感值加密往返与掩码识别。
     */
    @Test
    void secretCodecRoundTrip() {
        AccountSecretCodec codec = new AccountSecretCodec("unit-test-secret-key");
        String cipher = codec.encrypt("plain-value");
        Assertions.assertTrue(codec.isEncrypted(cipher), "密文应带 enc: 前缀");
        Assertions.assertNotEquals("plain-value", cipher, "落库值不应是明文");
        Assertions.assertEquals("plain-value", codec.decrypt(cipher));
        Assertions.assertTrue(codec.isMasked("ab****cd"));
        Assertions.assertFalse(codec.isMasked("plain-value"));
    }

    /**
     * 验证未配置密钥时保持明文（兼容既有部署）。
     */
    @Test
    void secretCodecFallsBackToPlain() {
        AccountSecretCodec codec = new AccountSecretCodec("");
        Assertions.assertEquals("plain-value", codec.encrypt("plain-value"));
        Assertions.assertEquals("plain-value", codec.decrypt("plain-value"));
    }

    /**
     * 验证账号种类支持范围。
     */
    @Test
    void accountKindSupported() {
        Assertions.assertTrue(AccountKind.supported(AccountKind.API_KEY));
        Assertions.assertTrue(AccountKind.supported(AccountKind.EMAIL));
        Assertions.assertTrue(AccountKind.supported(AccountKind.SMS));
        Assertions.assertFalse(AccountKind.supported("UNKNOWN"));
    }
}
