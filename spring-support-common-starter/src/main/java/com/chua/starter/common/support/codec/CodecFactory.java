package com.chua.starter.common.support.codec;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.GlobalSettingFactory;
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
public class CodecFactory implements Upgrade<CodecSetting> {


    private final List<String> whiteList;
    private final Codec codec;
    private final CodecKeyPair codecKeyPair;
    private final String publicKeyHex;

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";
    private CodecSetting codecSetting;

    public CodecFactory(CodecProperties codecProperties) {
        GlobalSettingFactory globalSettingFactory = GlobalSettingFactory.getInstance();
        globalSettingFactory.register("codec", new CodecSetting());
        globalSettingFactory.setIfNoChange("codec", "enable", codecProperties.isEnable());
        this.upgrade(globalSettingFactory.get("codec", CodecSetting.class));
        this.codecType = codecProperties.getCodecType();
        this.codec = Codec.build(codecType);
        this.whiteList = codecProperties.getWhiteList();
        this.codecKeyPair = (CodecKeyPair) codec;
        this.publicKeyHex = codecKeyPair.getPublicKeyHex();
    }


    public boolean isPass() {
        return !codecSetting.isEnable();
    }

    /**
     * 编码
     *
     * @param data 数据
     * @return {@link Object}
     */
    public CodecResult encode(String data) {
        String encode = codecKeyPair.encode(data, publicKeyHex);
        String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, "0");
        String encrypt = DigestUtils.aesEncrypt(codecKeyPair.getPrivateKeyHex(), nanoTime);
        return new CodecResult(encrypt, encode, nanoTime);
    }

    /**
     * 设置是否启用
     *
     * @param parseBoolean 是否启用
     */
    public void setEnable(boolean parseBoolean) {
        codecSetting.setEnable(parseBoolean);
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
    }
}
