package com.chua.starter.common.support.codec;

import com.chua.common.support.function.Upgrade;
import lombok.Data;

/**
 * 加密设置
 * @author CH
 * @since 2024/8/14
 */
@Data
public class CodecSetting implements Upgrade<CodecSetting> {

    /**
     * 是否开启响应加密
     */
    private boolean codecResponseOpen = false;

    /**
     * 是否开启请求加密
     */
    private boolean codecRequestOpen = false;

    /**
     * 请求加密key
     */
    private String codecRequestKey = null;

    @Override
    public void upgrade(CodecSetting codecSetting) {
        this.codecResponseOpen = codecSetting.isCodecResponseOpen();
        this.codecRequestOpen = codecSetting.isCodecRequestOpen();
        this.codecRequestKey = codecSetting.getCodecRequestKey();
    }
}
