package com.chua.starter.common.support.api.encode;

import com.chua.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 响应编码配置
 *
 * @author CH
 * @since 2024/8/14
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class ApiResponseEncodeConfiguration extends ApplicationEvent implements Upgrade<ApiResponseEncodeConfiguration> {

    /**
     * 是否开启响应加�?
     */
    private boolean codecResponseOpen = true;

    /**
     * 无参构造方�?
     */
    public ApiResponseEncodeConfiguration() {
        this(true);
    }

    /**
     * 带参构造方�?
     *
     * @param source 事件源对�?
     */
    public ApiResponseEncodeConfiguration(Object source) {
        super(source);
    }

    /**
     * 升级配置信息
     *
     * @param apiResponseEncodeConfiguration 新的配置信息
     */
    @Override
    public void upgrade(ApiResponseEncodeConfiguration apiResponseEncodeConfiguration) {
        this.codecResponseOpen = apiResponseEncodeConfiguration.isCodecResponseOpen();
    }
}

