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
     *
     * <p>默认关闭：与其配套的 {@code CodecRequestOpen} / {@code CodecTraceOpen} / {@code CodecSniffOpen}
     * 同口径——加密与嗅探都属可选加固，缺省不得替业务方打开；需要时由
     * {@code sys_setting}(group=config) 的 {@code CodecResponseOpen} 显式开启。</p>
     */
    private boolean codecResponseOpen = false;

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

