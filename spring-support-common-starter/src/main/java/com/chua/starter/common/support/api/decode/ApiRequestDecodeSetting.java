package com.chua.starter.common.support.api.decode;

import com.chua.starter.common.support.function.Upgrade;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

import java.util.Locale;

/**
 * 请求解码设置
 *
 * @since 2024/12/07
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
public class ApiRequestDecodeSetting extends ApplicationEvent implements Upgrade<ApiRequestDecodeSetting> {

    /**
     * 数据库配置别名：codec请求打开（默认关闭：请求加密属可选增强，不默认启用）
     */
    private boolean codecRequestOpen = false;

    /**
     * 请求加密Key
     */
    private String codecRequestKey = null;

    /**
     * 数据库配置别名：codec分段传输打开（traceId 分片）
     */
    private boolean codecTraceOpen = false;

    /**
     * 数据库配置别名：嗅探（bot 挑战）打开（默认关闭）
     */
    private boolean codecSniffOpen = false;

    /**
     * 数据库配置别名：请求/响应 codec 算法类型（keylog/aes）
     */
    private String codecType;

    /**
     * 数据库配置别名：keylog 服务端长期公钥（X.509 SPKI hex，公钥可公开下发）
     */
    private String codecServerPublicKey;

    /**
     * 数据库配置别名：接口强制签名开关（开启后缺签名头/坏签名一律拒绝，白名单除外）
     */
    private boolean apiSignOpen = false;

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
        this.codecTraceOpen = setting.isCodecTraceOpen();
        this.codecSniffOpen = setting.isCodecSniffOpen();
        this.codecType = setting.getCodecType();
        this.codecServerPublicKey = setting.getCodecServerPublicKey();
        this.apiSignOpen = setting.isApiSignOpen();
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
     * 获取 codec请求Key
     *
     * @return codecRequestKey
     */
    public String getCodecRequestKey() {
        return codecRequestKey;
    }

    /**
     * 设置 codec请求Key
     *
     * @param codecRequestKey codec请求Key
     */
    public void setCodecRequestKey(String codecRequestKey) {
        this.codecRequestKey = codecRequestKey;
    }

    /**
     * 获取 codec分段传输打开
     *
     * @return codecTraceOpen
     */
    public boolean getCodecTraceOpen() {
        return codecTraceOpen;
    }

    /**
     * 判断是否开启 codec 分段传输（traceId 分片）
     *
     * @return codecTraceOpen
     */
    public boolean isCodecTraceOpen() {
        return codecTraceOpen;
    }

    /**
     * 设置 codec分段传输打开
     *
     * @param codecTraceOpen codecTraceOpen
     */
    public void setCodecTraceOpen(boolean codecTraceOpen) {
        this.codecTraceOpen = codecTraceOpen;
    }

    /**
     * 获取嗅探（bot 挑战）开关
     *
     * @return codecSniffOpen
     */
    public boolean getCodecSniffOpen() {
        return codecSniffOpen;
    }

    /**
     * 判断是否开启嗅探（bot 挑战）
     *
     * @return codecSniffOpen
     */
    public boolean isCodecSniffOpen() {
        return codecSniffOpen;
    }

    /**
     * 设置嗅探（bot 挑战）开关
     *
     * @param codecSniffOpen codecSniffOpen
     */
    public void setCodecSniffOpen(boolean codecSniffOpen) {
        this.codecSniffOpen = codecSniffOpen;
    }

    /**
     * 获取 codec 算法类型
     *
     * @return codecType
     */    public String getCodecType() {
        return codecType;
    }

    /**
     * 设置 codec 算法类型
     *
     * @param codecType codecType
     */
    public void setCodecType(String codecType) {
        this.codecType = codecType;
    }

    /**
     * 获取 keylog 服务端长期公钥
     *
     * @return codecServerPublicKey
     */
    public String getCodecServerPublicKey() {
        return codecServerPublicKey;
    }

    /**
     * 设置 keylog 服务端长期公钥
     *
     * @param codecServerPublicKey codecServerPublicKey
     */
    public void setCodecServerPublicKey(String codecServerPublicKey) {
        this.codecServerPublicKey = codecServerPublicKey;
    }

    /**
     * 判断是否开启接口强制签名
     *
     * @return apiSignOpen
     */
    public boolean isApiSignOpen() {
        return apiSignOpen;
    }

    /**
     * 设置接口强制签名开关
     *
     * @param apiSignOpen apiSignOpen
     */
    public void setApiSignOpen(boolean apiSignOpen) {
        this.apiSignOpen = apiSignOpen;
    }

    /**
     * 生效的 codec 算法类型：设置行优先，空回落调用方给定的默认值。
     *
     * @param fallback 默认类型（通常来自 yaml codecType）
     * @return 生效类型（小写）
     */
    public String effectiveCodecType(String fallback) {
        return requireSupportedCodecType(null != codecType && !codecType.isBlank() ? codecType : fallback);
    }

    /**
     * 规整并校验传输算法；空值沿用 AES 默认值，拒绝历史算法与纯编码类型。
     *
     * @param type 配置类型
     * @return keylog 或 aes
     * @throws IllegalArgumentException 类型不受支持时抛出
     */
    public static String requireSupportedCodecType(String type) {
        String normalized = null == type || type.isBlank() ? "aes" : type.trim().toLowerCase(Locale.ROOT);
        if (!"aes".equals(normalized) && !"keylog".equals(normalized)) {
            throw new IllegalArgumentException("传输加解密类型只支持 keylog 和 aes");
        }
        return normalized;
    }

}
