package com.chua.starter.common.support.configuration;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.crypto.CodecKeyPair;
import com.chua.common.support.utils.Hex;
import com.chua.starter.common.support.properties.CodecProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import javax.annotation.Resource;

/**
 * 编解码器配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        CodecProperties.class
})
public class CodecConfiguration implements InitializingBean {

    @Resource
    private CodecProperties codecProperties;

    @Override
    public void afterPropertiesSet() throws Exception {
        if(codecProperties.isEnable()) {
            Codec codec = Codec.build(codecProperties.getCodecType());
            if(codec instanceof CodecKeyPair) {
                CodecKeyPair codecKeyPair = (CodecKeyPair) codec;
                codecProperties.setPrivateKey(Hex.encodeHexString(codecKeyPair.getPrivateKey().getEncoded()));
                codecProperties.setPublicKey(Hex.encodeHexString(codecKeyPair.getPublicKey().getEncoded()));
            }
        }
    }
}
