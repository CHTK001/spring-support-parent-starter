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
     * 开放式编解码器
     */
    private boolean codecOpen = false;

    @Override
    public void upgrade(CodecSetting codecSetting) {
        this.codecOpen = codecSetting.isCodecOpen();
    }
}
