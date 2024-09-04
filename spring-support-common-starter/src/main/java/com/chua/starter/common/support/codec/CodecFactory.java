package com.chua.starter.common.support.codec;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.matcher.PathMatcher;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.Hex;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.properties.CodecProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.ApplicationListener;

import java.util.List;

/**
 * 编解码器提供程序
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class CodecFactory implements Upgrade<CodecSetting>, ApplicationListener<CodecSetting> {


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
        return codecSetting.isCodecRequestOpen();
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
