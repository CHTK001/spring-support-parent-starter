// [P3C A20 豁免] A20/A30/A31: 含类型名等协议常量，属非业务魔法值场景。
package com.chua.starter.common.support.algorithm.crypto;

import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.spi.annotations.Spi;

import java.nio.charset.StandardCharsets;

/**
 * 十六进制编解码实现。
 *
 * <p><b>这不是加密</b>，只是把明文字节转成十六进制，任何人都能还原。
 * 保留它只为兼容「未配置密钥时按 hex 传输」的旧协议；配置了密钥的路径
 * 必须解析到 aes / sm4 / des 的真实实现。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Spi({"hex"})
public class HexCodec implements Codec {

    @Override
    public Codec key(String key) {
        return this;
    }

    @Override
    public String encodeHex(String data) {
        if (data == null) {
            return null;
        }
        return Hex.encodeHexString(data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decodeHex(String hex) {
        if (hex == null) {
            return null;
        }
        return new String(Hex.decodeHex(hex), StandardCharsets.UTF_8);
    }
}
