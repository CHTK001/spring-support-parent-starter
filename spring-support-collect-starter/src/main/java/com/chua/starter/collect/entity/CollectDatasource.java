package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集数据源。
 *
 * <p>供 SQL 节点查询与采集结果导出到业务表使用，密码按 {@code CollectSecrets} 的 b64 前缀格式存储，
 * 接口返回时不下发。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_datasource")
@Schema(description = "数据采集-数据源")
public class CollectDatasource extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_datasource_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectDatasourceId;

    /**
     * 数据源名称。
     */
    @Schema(description = "数据源名称")
    private String collectDatasourceName;

    /**
     * 数据源类型。
     */
    @Schema(description = "类型 MYSQL/H2/POSTGRESQL")
    private String collectDatasourceType;

    /**
     * 驱动类名。
     */
    @Schema(description = "驱动类名")
    private String collectDatasourceDriverClassName;

    /**
     * JDBC 连接串。
     */
    @Schema(description = "连接串")
    private String collectDatasourceJdbcUrl;

    /**
     * 用户名。
     */
    @Schema(description = "用户名")
    private String collectDatasourceUsername;

    /**
     * 密码（Base64）。
     */
    @Schema(description = "密码Base64")
    private String collectDatasourcePassword;

    /**
     * 状态，0 禁用 1 启用。
     */
    @Schema(description = "状态 0禁用 1启用")
    private Integer collectDatasourceStatus;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String collectDatasourceRemark;
}
