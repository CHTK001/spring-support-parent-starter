package com.chua.starter.server.support.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 项目日志后端检索结果（在目标主机上执行检索，只回传命中行）。
 *
 * @param keyword   检索关键字
 * @param source    实际检索的日志文件（日志路径配成目录时为目录内最新文件）
 * @param lines     命中的日志行（带行号前缀 {@code 123:内容}）
 * @param matched   命中行数（等于返回条数，截断时小于主机上的真实命中数）
 * @param truncated 是否因超出上限而被截断
 * @param elapsedMs 主机侧检索耗时（毫秒），前端据此提示预计时间
 * @param fileSize  被检索文件的大小（字节），未知时为 null
 * @author CH
 * @since 4.0.0.51
 */
@Schema(description = "项目日志检索结果")
public record ProjectLogSearchVO(
        @Schema(description = "检索关键字")
        String keyword,
        @Schema(description = "实际检索的日志文件")
        String source,
        @Schema(description = "命中的日志行")
        List<String> lines,
        @Schema(description = "命中行数")
        Integer matched,
        @Schema(description = "是否被截断")
        Boolean truncated,
        @Schema(description = "主机侧检索耗时(毫秒)")
        Long elapsedMs,
        @Schema(description = "被检索文件大小(字节)")
        Long fileSize) {
}
