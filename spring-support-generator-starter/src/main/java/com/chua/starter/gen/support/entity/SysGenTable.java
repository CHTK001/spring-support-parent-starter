package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.database.entity.TableResult;
import com.chua.common.support.unit.name.NamingCase;
import com.chua.starter.gen.support.properties.GenProperties;
import lombok.Data;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

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
    /**
     * 使用的模板（crud单表操作 tree树表操作 sub主子表操作）
     */
    @TableField(value = "tab_tpl_category")
    private String tabTplCategory;


    @TableField(exist = false)
    private SysGenColumn tabPkColumn;


    @TableField(exist = false)
    private List<SysGenColumn> tabColumns;

    @TableField(exist = false)
    private String tabDb;


    private static final long serialVersionUID = 1L;

    /**
     * 创建sys-gen表
     *
     * @param genId
     * @param tableName      查询
     * @param tableResultSet 表格结果集
     * @param genProperties
     * @return {@link com.chua.starter.gen.support.entity.SysGenTable}
     */
    public static com.chua.starter.gen.support.entity.SysGenTable createSysGenTable(Integer genId,
                                                                                    String tableName,
                                                                                    TableResult tableResultSet,
                                                                                    GenProperties genProperties) throws SQLException {
        com.chua.starter.gen.support.entity.SysGenTable sysGenTable = new com.chua.starter.gen.support.entity.SysGenTable();
        sysGenTable.setGenId(genId);
        sysGenTable.setTabDesc(tableResultSet.getRemark());
        sysGenTable.setTabDb(tableResultSet.getDatabase());
        sysGenTable.setTabName(tableName);
        sysGenTable.setTabClassName(NamingCase.toHyphenUpperCamel(sysGenTable.getTabName()));
        sysGenTable.setTabPackageName(genProperties.getPackageName());
        sysGenTable.setTabModuleName(getModuleName(genProperties.getPackageName()));
        sysGenTable.setTabBusinessName(getBusinessName(sysGenTable.getTabName()));
        sysGenTable.setTabFunctionName(replaceText(sysGenTable.getTabRemark()));
        sysGenTable.setTabFunctionAuthor(genProperties.getAuthor());
        return sysGenTable;
    }

    /**
     * 获取模块名
     *
     * @param packageName 包名
     * @return 模块名
     */
    public static String getModuleName(String packageName) {
        packageName = StringUtils.defaultString(packageName, "com");
        int lastIndex = packageName.lastIndexOf(".");
        int nameLength = packageName.length();
        return StringUtils.substring(packageName, lastIndex + 1, nameLength);
    }

    /**
     * 关键字替换
     *
     * @param text 需要被替换的名字
     * @return 替换后的名字
     */
    public static String replaceText(String text) {
        return RegExUtils.replaceAll(text, "(?:表|若依)", "");
    }

    /**
     * 获取业务名
     *
     * @param tableName 表名
     * @return 业务名
     */
    public static String getBusinessName(String tableName) {
        int firstIndex = tableName.indexOf("_");
        int nameLength = tableName.length();
        String businessName = StringUtils.substring(tableName, firstIndex + 1, nameLength);
        businessName = NamingCase.toCamelCase(businessName);
        return businessName;
    }

}