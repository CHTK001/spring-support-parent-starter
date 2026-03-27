package com.chua.starter.common.support.api.decode;

import com.chua.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * 请求解码设置
 *
 * @author CH
 * @since 2024/12/07
 */
@EqualsAndHashCode(callSuper = true)
public class ApiRequestDecodeSetting extends ApplicationEvent implements Upgrade<ApiRequestDecodeSetting> {

    /**
     * 数据库配置别名：CodecRequestOpen
     */
    private boolean codecRequestOpen = true;

    /**
     * 请求加密key
     */
    private String codecRequestKey = null;

    /**
     * 无参构造方法
     */
    public ApiRequestDecodeSetting() {
        this(true);
    }

    /**
     * 带参构造方法
     *
     * @param source 事件源对象
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
        this.codecRequestOpen = setting.isEnable();
        this.codecRequestKey = setting.getCodecRequestKey();
    }
    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return isEnable();
    }

    /**
     * 判断是否开启请求解密
     *
     * @return enable
     */
    public boolean isEnable() {
        return codecRequestOpen;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.codecRequestOpen = enable;
    }

    /**
     * 获取 codecRequestOpen
     *
     * @return codecRequestOpen
     */
    public boolean getCodecRequestOpen() {
        return codecRequestOpen;
    }

    /**
     * 判断是否开启请求解密
     *
     * @return codecRequestOpen
     */
    public boolean isCodecRequestOpen() {
        return codecRequestOpen;
    }

    /**
     * 设置 codecRequestOpen
     *
     * @param codecRequestOpen codecRequestOpen
     */
    public void setCodecRequestOpen(boolean codecRequestOpen) {
        this.codecRequestOpen = codecRequestOpen;
    }

    /**
     * 获取 codecRequestKey
     *
     * @return codecRequestKey
     */
    public String getCodecRequestKey() {
        return codecRequestKey;
    }

    /**
     * 设置 codecRequestKey
     *
     * @param codecRequestKey codecRequestKey
     */
    public void setCodecRequestKey(String codecRequestKey) {
        this.codecRequestKey = codecRequestKey;
    }


}
