package com.chua.starter.monitor.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 备份与当前配置的差异结果。
 *
 * @param backupName 备份文件名
 * @param targetPath 对比目标（当前配置文件）的绝对路径
 * @param added      新增行数（只存在于当前文件）
 * @param removed    删除行数（只存在于备份）
 * @param lines      unified-diff 风格行序列，{@code +} 新增、{@code -} 删除、空格前缀为未变
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "备份差异")
public record NginxBackupDiffVO(
        @Schema(description = "备份文件名", example = "nginx.conf_20260928-101530.bak") String backupName,
        @Schema(description = "对比目标路径", example = "/etc/nginx/nginx.conf") String targetPath,
        @Schema(description = "新增行数", example = "3") long added,
        @Schema(description = "删除行数", example = "1") long removed,
        @Schema(description = "差异行序列") List<String> lines
) {
}
