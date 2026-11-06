package com.chua.starter.collect.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 采集数据源出参。
 *
 * <p>接口不下发密码，只用 {@code passwordSet} 表示是否已配置。</p>
 *
 * @param collectDatasourceId          主键ID
 * @param collectDatasourceName        数据源名称
 * @param collectDatasourceType        数据源类型
 * @param collectDatasourceDriverClassName 驱动类名
 * @param collectDatasourceJdbcUrl     JDBC 连接串
 * @param collectDatasourceUsername    用户名
 * @param passwordSet                  是否已配置密码
 * @param collectDatasourceStatus      状态，0 禁用 1 启用
 * @param collectDatasourceRemark      备注
 * @param createTime                   创建时间
 * @param updateTime                   更新时间
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "数据采集-数据源出参")
public record CollectDatasourceVO(
        @Schema(description = "主键ID")
        Integer collectDatasourceId,
        @Schema(description = "数据源名称")
        String collectDatasourceName,
        @Schema(description = "类型")
        String collectDatasourceType,
        @Schema(description = "驱动类名")
        String collectDatasourceDriverClassName,
        @Schema(description = "连接串")
        String collectDatasourceJdbcUrl,
        @Schema(description = "用户名")
        String collectDatasourceUsername,
        @Schema(description = "是否已配置密码")
        Boolean passwordSet,
        @Schema(description = "状态 0禁用 1启用")
        Integer collectDatasourceStatus,
        @Schema(description = "备注")
        String collectDatasourceRemark,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "创建时间")
        LocalDateTime createTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @Schema(description = "更新时间")
        LocalDateTime updateTime) {
}
