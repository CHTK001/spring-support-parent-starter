package com.chua.starter.sync.pojo.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列定义
 * <p>
 * 用于输出节点自动建表时的列配置
 * </p>
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列定义")
public class ColumnDefinition {

    /**
     * 列名
     */
    @Schema(description = "列名", example = "user_name")
    private String name;

    /**
     * 列类型
     * <p>
     * 支持: VARCHAR, INT, BIGINT, TEXT, DATETIME, DATE, DECIMAL, BOOLEAN, FLOAT, DOUBLE, BLOB, JSON
     * </p>
     */
    @Schema(description = "列类型", example = "VARCHAR")
    private String type;

    /**
     * 列长度（VARCHAR等类型需要）
     */
    @Schema(description = "列长度", example = "255")
    private Integer length;

    /**
     * 小数位数（DECIMAL类型需要）
     */
    @Schema(description = "小数位数", example = "2")
    private Integer scale;

    /**
     * 是否可为空
     */
    @Schema(description = "是否可为空", example = "true")
    private Boolean nullable;

    /**
     * 默认值
     */
    @Schema(description = "默认值")
    private String defaultValue;

    /**
     * 是否为主键
     */
    @Schema(description = "是否为主键", example = "false")
    private Boolean primaryKey;

    /**
     * 是否自增
     */
    @Schema(description = "是否自增", example = "false")
    private Boolean autoIncrement;

    /**
     * 列注释
     */
    @Schema(description = "列注释", example = "用户名")
    private String comment;

    /**
     * 列顺序
     */
    @Schema(description = "列顺序", example = "1")
    private Integer order;

    /**
     * 源字段名（用于字段映射）
     */
    @Schema(description = "源字段名（用于字段映射）")
    private String sourceField;
}
