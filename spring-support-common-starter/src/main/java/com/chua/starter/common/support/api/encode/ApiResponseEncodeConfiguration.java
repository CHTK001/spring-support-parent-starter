package com.chua.starter.common.support.api.encode;

import com.chua.starter.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
* 响应编码配置
*
* @since 2024/8/14
* @author CH
 */
@EqualsAndHashCode(callSuper = true)
public class ApiResponseEncodeConfiguration extends ApplicationEvent implements Upgrade<ApiResponseEncodeConfiguration> {

    /**
    * 是否开启响应加密
    */
    private boolean codecResponseOpen = true;

    /**
    * 无参构造方法
    */
    public ApiResponseEncodeConfiguration() {
        /**
        * this。
        * @param true true
        */
        this(true);
    }

    /**
    * 带参构造方法
    *
    * @param source 事件源对象
    */
    public ApiResponseEncodeConfiguration(Object source) {
        /**
        * 父。
        * @param source 源
        */
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
    /**
    * 获取 codec响应打开
    *
    * @return codecResponseOpen
    */
    public boolean getCodecResponseOpen() {
        return codecResponseOpen;
    }

    /**
    * 判断是否开启响应加密
    *
    * @return codecResponseOpen
    */
    public boolean isCodecResponseOpen() {
        return codecResponseOpen;
    }

    /**
    * 设置 codec响应打开
    *
    * @param codecResponseOpen codec响应打开
    */
    public void setCodecResponseOpen(boolean codecResponseOpen) {
        this.codecResponseOpen = codecResponseOpen;
    }


}

