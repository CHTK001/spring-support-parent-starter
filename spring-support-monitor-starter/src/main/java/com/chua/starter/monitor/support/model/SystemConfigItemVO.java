package com.chua.starter.monitor.support.model;

/**
 * 主机系统配置项视图。
 *
 * <p>画像「系统配置」页签只展示目标机真实存在、且本系统能读写的配置项：
 * 读不到的不编造，不支持的不出现在列表里。每一项都带「生效方式」与「风险提示」，
 * 前端据此决定是否弹二次确认、以及提示用户改动是立即生效还是需要重新登录 / 重启。</p>
 *
 * @param key             配置键（应用时原样回传，服务端按白名单校验）
 * @param label           展示名
 * @param value           当前值文本
 * @param unit            单位，纯文本展示（如「个」「八进制」）
 * @param scope           作用域（内核 / 用户会话 / 全局）
 * @param source          实际读取来源（如 sysctl fs.file-max、/etc/security/limits.conf）
 * @param editable        是否允许在本系统内修改
 * @param hot             是否支持热更新（立即对后续行为生效，不需要重启 / 重新登录）
 * @param requiresConfirm 修改前是否需要二次确认（高风险项）
 * @param tip             操作提示：生效范围、风险与回退方式
 * @author CH
 * @since 4.0.0.51
 */
public record SystemConfigItemVO(
        String key,
        String label,
        String value,
        String unit,
        String scope,
        String source,
        boolean editable,
        boolean hot,
        boolean requiresConfirm,
        String tip) {
}
