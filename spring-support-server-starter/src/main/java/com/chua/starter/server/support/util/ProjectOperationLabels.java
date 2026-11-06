package com.chua.starter.server.support.util;

import java.util.Map;

/**
 * 项目操作中文名。
 *
 * <p>任务横幅、操作日志共用同一份映射，避免同一个 operation 在两处出现不同中文名。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public final class ProjectOperationLabels {

    /**
     * 操作类型 → 中文名
     */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("create", "创建项目"),
            Map.entry("update", "修改项目"),
            Map.entry("delete", "删除项目"),
            Map.entry("start", "启动"),
            Map.entry("stop", "停止"),
            Map.entry("restart", "重启"),
            Map.entry("upgrade", "升级"),
            Map.entry("backup", "备份"),
            Map.entry("rollback", "回滚"),
            Map.entry("deploy", "Git发布"),
            Map.entry("install", "安装"),
            Map.entry("uninstall", "卸载"),
            Map.entry("install-service", "挂载系统服务"),
            Map.entry("uninstall-service", "卸载系统服务"),
            Map.entry("toggle", "启用/禁用"),
            Map.entry("update-lib", "更新依赖包"),
            Map.entry("copy", "复制到其他服务器")
    );

    private ProjectOperationLabels() {
    }

    /**
     * 取操作中文名。
     *
     * @param operation 操作类型
     * @return 中文名；未登记时原样返回
     */
    public static String label(String operation) {
        if (operation == null || operation.isBlank()) {
            return "操作";
        }
        return LABELS.getOrDefault(operation, operation);
    }
}
