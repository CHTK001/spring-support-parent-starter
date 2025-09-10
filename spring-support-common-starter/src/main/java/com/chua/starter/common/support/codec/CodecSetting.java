package com.chua.starter.common.support.codec;

import com.chua.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 加密设置
 *
 * @author CH
 * @since 2024/8/14
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class CodecSetting extends ApplicationEvent implements Upgrade<CodecSetting> {

    /**
     * 是否开启响应加密
     * 默认值: false
     * 示例: true 表示开启响应加密, false 表示关闭响应加密
     */
    private boolean codecResponseOpen = false;

    /**
     * 是否开启请求解密
     * 默认值: false
     * 示例: true 表示开启请求解密, false 表示关闭请求解密
     */
    private boolean codecRequestOpen = false;

    /**
     * 请求加密key
     * 默认值: null
     * 示例: "mySecretKey" 表示使用该字符串作为加密密钥
     */
    private String codecRequestKey = null;

    /**
     * 无参构造方法
     * 调用带参构造方法并传入true作为参数
     */
    public CodecSetting() {
        this(true);
    }

    /**
     * 带参构造方法
     *
     * @param source 事件源对象，不能为null
     *               示例: new Object() 表示使用一个新创建的对象作为事件源
     */
    public CodecSetting(Object source) {
        super(source);
    }

    /**
     * 升级配置信息
     *
     * @param codecSetting 新的配置信息，不能为null
     *                     示例: CodecSetting对象，包含新的配置参数
     */
    @Override
    public void upgrade(CodecSetting codecSetting) {
        this.codecResponseOpen = codecSetting.isCodecResponseOpen();
        this.codecRequestOpen = codecSetting.isCodecRequestOpen();
        this.codecRequestKey = codecSetting.getCodecRequestKey();
    }
}
