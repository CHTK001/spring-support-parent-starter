package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.unit.name.NamingCase;
import lombok.Data;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * sys-gen表
 *
 * @author CH
 * @since 2023/09/21
 */
@Data
@TableName(value = "sys_gen_table")
public class SysGenTable implements Serializable {
    /**
     * 表ID
     */
    @TableId(value = "tab_id", type = IdType.AUTO)
    private Integer tabId;

    /**
     * 表名称
     */
    @TableField(value = "tab_name")
    private String tabName;

    /**
     * 描述
     */
    @TableField(value = "tab_desc")
    private String tabDesc;

    /**
     * 所属数据源名称, 用于处理内部数据源
     */
    @TableField(value = "gen_name")
    private String genName;

    /**
     * 所属数据源
     */
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 实体类名称
     */
    @TableField(value = "tab_class_name")
    private String tabClassName;

    /**
     * 生成包路径
     */
    @TableField(value = "tab_package_name")
    private String tabPackageName;

    /**
     * 生成模块名
     */
    @TableField(value = "tab_module_name")
    private String tabModuleName;

    /**
     * 生成业务名
     */
    @TableField(value = "tab_business_name")
    private String tabBusinessName;

    /**
     * 生成功能名
     */
    @TableField(value = "tab_function_name")
    private String tabFunctionName;

    /**
     * 生成功能作者
     */
    @TableField(value = "tab_function_author")
    private String tabFunctionAuthor;

    /**
     * 生成代码方式（0zip压缩包 1自定义路径）
     */
    @TableField(value = "tab_gen_type")
    private String tabGenType;

    /**
     * 生成路径（不填默认项目路径）
     */
    @TableField(value = "tab_gen_path")
    private String tabGenPath;

    /**
     * 备注
     */
    @TableField(value = "tab_remark")
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