package com.chua.starter.common.support.api.decode;

import com.chua.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 请求解码设置
 *
 * @author CH
 * @since 2024/12/07
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class ApiRequestDecodeSetting extends ApplicationEvent implements Upgrade<ApiRequestDecodeSetting> {

    /**
     * 是否开启请求解�?
     */
    private boolean enable = true;

    /**
     * 请求加密key
     */
    private String codecRequestKey = null;

    /**
     * 无参构造方�?
     */
    public ApiRequestDecodeSetting() {
        this(true);
    }

    /**
     * 带参构造方�?
     *
     * @param source 事件源对�?
     */
    public ApiRequestDecodeSetting(Object source) {
        super(source);
    }

    /**
     * 升级配置信息
     *
     * @param setting 新的配置信息
     */
    @Override
    public void upgrade(ApiRequestDecodeSetting setting) {
        this.enable = setting.isEnable();
        this.codecRequestKey = setting.getCodecRequestKey();
    }
}

