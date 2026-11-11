package com.chua.starter.device.support.security;

import java.util.List;

/**
 * 设备模块默认白名单声明。
 *
 * <p>webhook 入站端点由外部系统调用，拿不到 monitor 登录态，因此在本模块内
 * 以 {@code @TokenForIgnore} 声明为默认放行（框架鉴权层按注解放行），
 * 而不是写到宿主应用的 {@code plugin.oauth.whitelist} 配置里 —— 后者会让
 * 「引入 device-starter 就得改一次宿主 yaml」成为部署负担，也容易漏配。</p>
 *
 * <p>安全边界：只有这里列出的四个入站路径免登录态，且仍由
 * {@code DeviceWebhookService#verify} 做 appKey 签名 / 有效期 / 主题授权校验；
 * appKey 管理接口 {@code /api/device/webhook/appkeys} 不在清单内，仍需登录态。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class DeviceWebhookDefaults {

    /** webhook 前缀 */
    public static final String WEBHOOK_PREFIX = "/api/device/webhook";

    /** 门禁通行记录入站路径（相对前缀） */
    public static final String PATH_ACCESS = "/access-records";

    /** 告警事件入站路径（相对前缀） */
    public static final String PATH_ALARM = "/alarms";

    /** 过车记录入站路径（相对前缀） */
    public static final String PATH_VEHICLE_PASS = "/vehicle-passes";

    /** 考勤记录入站路径（相对前缀） */
    public static final String PATH_ATTENDANCE = "/attendance-records";

    /** 设备模块默认放行清单（相对宿主的完整 URL） */
    public static final List<String> DEFAULT_INGRESS_WHITELIST = List.of(
            WEBHOOK_PREFIX + PATH_ACCESS,
            WEBHOOK_PREFIX + PATH_ALARM,
            WEBHOOK_PREFIX + PATH_VEHICLE_PASS,
            WEBHOOK_PREFIX + PATH_ATTENDANCE
    );

    /**
     * 常量类不可实例化。
     */
    private DeviceWebhookDefaults() {
    }
}
