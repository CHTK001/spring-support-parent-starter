package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * Prometheus 配置保存/恢复的结果。
 *
 * <p>与 Nginx 侧同语义：一次保存是一条流水线（临时落盘 → promtool 校验 → 备份 →
 * 覆盖 → 重载），每一步写进 {@code steps}，失败时用户能直接看到卡在哪一步。</p>
 *
 * @param success    整体是否成功（校验通过且已覆盖写入）
 * @param message    面向用户的结果摘要
 * @param backupPath 备份文件路径，未备份时为 null
 * @param validated  是否通过 promtool 校验；null 表示未校验（promtool 不可用或未开启）
 * @param reloaded   是否成功触发重载
 * @param output     校验/重载的输出原文（promtool 的报错在这里）
 * @param steps      执行步骤文本
 * @author CH
 * @since 4.0.0.42
 */
public record PrometheusApplyResultVO(
        boolean success,
        String message,
        String backupPath,
        Boolean validated,
        boolean reloaded,
        String output,
        List<String> steps
) {
}
