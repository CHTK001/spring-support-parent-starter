package com.chua.starter.datasourcehub.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 操作日志列表项。
 *
 * @param id             主键
 * @param datasourceId   数据源 id
 * @param datasourceName 数据源名称
 * @param action         操作码
 * @param actionName     操作中文名
 * @param target         操作对象
 * @param detail         补充说明
 * @param success        1 成功 / 0 失败 / null 未判定
 * @param costMs         耗时（毫秒）
 * @param operator       操作人
 * @param clientIp       客户端 IP
 * @param requestUrl     请求路径
 * @param createTime     操作时间
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "数据源中心操作日志列表项")
public record DatasourceOperLogVO(
        @Schema(description = "主键") Long id,
        @Schema(description = "数据源 ID") Long datasourceId,
        @Schema(description = "数据源名称") String datasourceName,
        @Schema(description = "操作码") String action,
        @Schema(description = "操作中文名") String actionName,
        @Schema(description = "操作对象") String target,
        @Schema(description = "补充说明") String detail,
        @Schema(description = "1 成功 0 失败") Integer success,
        @Schema(description = "耗时（毫秒）") Long costMs,
        @Schema(description = "操作人") String operator,
        @Schema(description = "客户端 IP") String clientIp,
        @Schema(description = "请求路径") String requestUrl,
        @Schema(description = "操作时间") LocalDateTime createTime) {
}