// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelRecord;
import org.springframework.util.StringUtils;

/**
 * 页面输入的统一校验与归一工具，供各 {@code TunnelProvider} 实现复用。
 *
 * <p>把这些逻辑抽出来是因为改造前 ngrok / cloudflared 两个 Service 里各写了一份
 * 几乎逐字相同的 {@code applyInput}：端口范围校验、名称兜底、空串转 null、
 * 默认值兜底。差异只有「支持的模式集合」和「专属字段」，那部分留在实现里。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public final class TunnelInputs {

    /**
     * 工具类，禁止实例化。
     */
    private TunnelInputs() {
    }

    /**
     * 校验本地端口。
     *
     * @param input 页面提交
     * @return 合法的端口
     * @throws IllegalArgumentException 为空或越界
     */
    public static int requirePort(TunnelRecord input) {
        if (input == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        int port = input.getLocalPort();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("本地端口必须在 1 - 65535 之间");
        }
        return port;
    }

    /**
     * 应用公共字段（各提供方共有的部分）。
     *
     * <p>调用方应先自行校验并写入 {@code mode} 与专属字段，最后调用本方法
     * 补齐名称、备注、开关等，避免公共字段被实现方遗漏。</p>
     *
     * @param target   目标记录
     * @param input    页面提交
     * @param provider 提供方标识
     */
    public static void applyCommon(TunnelRecord target, TunnelRecord input, String provider) {
        int port = requirePort(input);
        target.setLocalPort(port);
        target.setName(StringUtils.hasText(input.getName())
                ? input.getName().trim()
                : provider + "-" + port);
        target.setRemark(trimToNull(input.getRemark()));
        target.setAutoStart(input.isAutoStart());
        target.setEnabled(input.isEnabled());
        target.setProvider(provider);
    }

    /**
     * 去空白，空串返回 null。
     *
     * @param value 原值
     * @return 处理后的值
     */
    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 默认值兜底并去空白。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 处理后的值
     */
    public static String normalize(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 校验模式是否在允许集合内。
     *
     * @param mode     待校验模式（已小写）
     * @param allowed  允许集合
     * @param provider 提供方展示名
     * @return 模式本身
     * @throws IllegalArgumentException 不支持
     */
    public static String requireMode(String mode, java.util.Collection<String> allowed, String provider) {
        if (!allowed.contains(mode)) {
            throw new IllegalArgumentException("不支持的 " + provider + " 模式: " + mode
                    + "，可选: " + allowed);
        }
        return mode;
    }
}