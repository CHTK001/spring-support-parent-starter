package com.chua.starter.common.support.provider;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.starter.common.support.properties.CodecProperties;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 编解码器提供程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class CodecProvider {


    private final List<String> whiteList;
    private boolean enable;

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";


    public CodecProvider(CodecProperties codecProperties) {
        this.enable = codecProperties.isEnable();
        this.codecType = codecProperties.getCodecType();
        this.whiteList = codecProperties.getWhiteList();
    }


    public boolean isPass() {
        return !enable;
    }

    /**
     * 编码
     *
     * @param data 数据
     * @return {@link Object}
     */
    public CodecResult encode(String data) {
        Codec codec = Codec.build(codecType);
        CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
        String publicKeyHex = codecKeyPair.getPublicKeyHex();
        String encode = codecKeyPair.encode(data, publicKeyHex);
        return new CodecResult(codecKeyPair.getPrivateKeyHex(), encode);
    }

    public void setEnable(boolean parseBoolean) {
        if(!parseBoolean) {
            this.enable = false;
            return;
        }
        this.enable = true;
    }

    public boolean isPass(String requestURI) {
        for (String s : whiteList) {
            if(PathMatcher.INSTANCE.match(s, requestURI)) {
                return true;
            }
        }

        return false;
    }

    public boolean isEnable() {
        return enable;
    }



    @Data
    @AllArgsConstructor
    public static class CodecResult {

        private String key;

        private String data;
    }
}
