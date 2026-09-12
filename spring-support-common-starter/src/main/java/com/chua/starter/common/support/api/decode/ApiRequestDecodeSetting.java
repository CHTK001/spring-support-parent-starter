package com.chua.starter.common.support.api.decode;

import com.chua.starter.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * 请求解码设置
 *
 * @since 2024/12/07
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
public class ApiRequestDecodeSetting extends ApplicationEvent implements Upgrade<ApiRequestDecodeSetting> {

    /**
      * 数据库配置别名：codec请求打开
     */
    private boolean codecRequestOpen = true;

    /**
      * 请求加密钥匙
     */
    private String codecRequestKey = null;

    /**
     * 无参构造方法
     */
    public ApiRequestDecodeSetting() {
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
    public ApiRequestDecodeSetting(Object source) {
        /**
         * 父。
         * @param source 源
         */
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
      * 获取 codec请求打开
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
      * 设置 codec请求打开
     *
     * @param codecRequestOpen codec请求打开
     */
    public void setCodecRequestOpen(boolean codecRequestOpen) {
        this.codecRequestOpen = codecRequestOpen;
    }

    /**
      * 获取 codec请求钥匙
     *
     * @return codecRequestKey
     */
    public String getCodecRequestKey() {
        return codecRequestKey;
    }

    /**
      * 设置 codec请求钥匙
     *
     * @param codecRequestKey codec请求钥匙
     */
    public void setCodecRequestKey(String codecRequestKey) {
        this.codecRequestKey = codecRequestKey;
    }


}
