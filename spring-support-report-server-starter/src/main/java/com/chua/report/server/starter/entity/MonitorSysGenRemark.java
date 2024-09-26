package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.datasource.annotation.ColumnDesc;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
@TableName(value = "monitor_sys_gen_remark")
public class MonitorSysGenRemark extends SysBase implements Serializable {
    /**
     * ID
     */
    @TableId(value = "remark_id", type = IdType.AUTO)
    @NotNull(message = "ID不能为null")
    private Integer remarkId;

    /**
     * 描述
     */
    @TableField(value = "remark_name")
    @ColumnDesc("描述")
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String remarkName;

    /**
     * genId
     */
    @ColumnDesc("gen_id")
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 表
     */
    @ColumnDesc("表")
    @TableField(value = "remark_table")
    @Size(max = 255,message = "表最大长度要小于 255")
    private String remarkTable;

    /**
     * 数据库
     */
    @ColumnDesc("数据库")
    @TableField(value = "remark_database")
    @Size(max = 255,message = "数据库最大长度要小于 255")
    private String remarkDatabase;


    /**
     * 字段
     */
    @ColumnDesc("字段")
    @TableField(value = "remark_column")
    @Size(max = 255,message = "字段最大长度要小于 255")
    private String remarkColumn;

    private static final long serialVersionUID = 1L;
}