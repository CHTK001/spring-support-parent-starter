package com.chua.starter.datasourcehub.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 备份文件批量删除结果。
 *
 * <p>删除是**部分成功**语义：一批名字里某个文件名非法或远端删除失败，
 * 不该让已删掉的回滚，也不该让整批报错——界面需要知道「哪些删了、哪些没删及原因」，
 * 所以成功与失败分开返回，而不是一个布尔。</p>
 *
 * @param deleted 成功删除（或本就不存在，幂等）的备份文件名
 * @param failed  未删除的条目，格式 {@code "文件名: 原因"}
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "备份批量删除结果")
public record NginxBackupDeleteVO(
        @Schema(description = "成功删除的备份文件名") List<String> deleted,
        @Schema(description = "删除失败的条目（文件名: 原因）", example = "nginx.conf_20260928-101530.bak: 备份文件名非法") List<String> failed
) {
}
