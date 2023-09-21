package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.unit.name.NamingCase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author CH
 */
@ApiModel(description = "sys_gen_table")
@Schema
@Data
@TableName(value = "sys_gen_table")
public class SysGenTable implements Serializable {
    /**
     * 表ID
     */
    @TableId(value = "tab_id", type = IdType.INPUT)
    @ApiModelProperty(value = "表ID")
    @Schema(description = "表ID")
    private Integer tabId;

    /**
     * 表名称
     */
    @TableField(value = "tab_name")
    @ApiModelProperty(value = "表名称")
    @Schema(description = "表名称")
    private String tabName;

    /**
     * 描述
     */
    @TableField(value = "tab_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    private String tabDesc;

    /**
     * 所属数据源名称, 用于处理内部数据源
     */
    @TableField(value = "gen_name")
    @ApiModelProperty(value = "所属数据源名称, 用于处理内部数据源")
    @Schema(description = "所属数据源名称, 用于处理内部数据源")
    private String genName;

    /**
     * 所属数据源
     */
    @TableField(value = "gen_id")
    @ApiModelProperty(value = "所属数据源")
    @Schema(description = "所属数据源")
    private Integer genId;

    /**
     * 实体类名称
     */
    @TableField(value = "tab_class_name")
    @ApiModelProperty(value = "实体类名称")
    @Schema(description = "实体类名称")
    private String tabClassName;

    /**
     * 生成包路径
     */
    @TableField(value = "tab_package_name")
    @ApiModelProperty(value = "生成包路径")
    @Schema(description = "生成包路径")
    private String tabPackageName;

    /**
     * 生成模块名
     */
    @TableField(value = "tab_module_name")
    @ApiModelProperty(value = "生成模块名")
    @Schema(description = "生成模块名")
    private String tabModuleName;

    /**
     * 生成业务名
     */
    @TableField(value = "tab_business_name")
    @ApiModelProperty(value = "生成业务名")
    @Schema(description = "生成业务名")
    private String tabBusinessName;

    /**
     * 生成功能名
     */
    @TableField(value = "tab_function_name")
    @ApiModelProperty(value = "生成功能名")
    @Schema(description = "生成功能名")
    private String tabFunctionName;

    /**
     * 生成功能作者
     */
    @TableField(value = "tab_function_author")
    @ApiModelProperty(value = "生成功能作者")
    @Schema(description = "生成功能作者")
    private String tabFunctionAuthor;

    /**
     * 生成代码方式（0zip压缩包 1自定义路径）
     */
    @TableField(value = "tab_gen_type")
    @ApiModelProperty(value = "生成代码方式（0zip压缩包 1自定义路径）")
    @Schema(description = "生成代码方式（0zip压缩包 1自定义路径）")
    private String tabGenType;

    /**
     * 生成路径（不填默认项目路径）
     */
    @TableField(value = "tab_gen_path")
    @ApiModelProperty(value = "生成路径（不填默认项目路径）")
    @Schema(description = "生成路径（不填默认项目路径）")
    private String tabGenPath;

    /**
     * 备注
     */
    @TableField(value = "tab_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    private String tabRemark;

    private static final long serialVersionUID = 1L;

    /**
     * 创建sys-gen表
     *
     * @param genId
     * @param tableName      查询
     * @param tableResultSet 表格结果集
     * @return {@link com.chua.starter.gen.support.entity.SysGenTable}
     */
    public static com.chua.starter.gen.support.entity.SysGenTable createSysGenTable(Integer genId, String tableName, ResultSet tableResultSet) throws SQLException {
        com.chua.starter.gen.support.entity.SysGenTable sysGenTable = new com.chua.starter.gen.support.entity.SysGenTable();
        sysGenTable.setGenId(genId);
        sysGenTable.setTabDesc(tableResultSet.getString(4));
        sysGenTable.setTabName(tableName);
        sysGenTable.setTabClassName(NamingCase.toHyphenUpperCamel(sysGenTable.getTabName()));

        return sysGenTable;
    }
}