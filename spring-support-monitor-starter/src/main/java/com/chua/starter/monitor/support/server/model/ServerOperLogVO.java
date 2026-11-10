package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 服务管理节点操作日志列表项。
 *
 * @param id         主键
 * @param nodeId     节点 id
 * @param nodeName   节点名称
 * @param action     操作码
 * @param actionName 操作中文名
 * @param target     操作对象
 * @param detail     补充说明
 * @param success    1 成功 / 0 失败 / null 未判定
 * @param costMs     耗时（毫秒）
 * @param operator   操作人
 * @param clientIp   客户端 IP
 * @param requestUrl 请求路径
 * @param createTime 操作时间
 * @author CH
 * @since 4.0.0.55
 */
@Schema(description = "服务管理节点操作日志列表项")
public record ServerOperLogVO(
        @Schema(description = "主键") Long id,
        @Schema(description = "节点 ID") Long nodeId,
        @Schema(description = "节点名称") String nodeName,
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
