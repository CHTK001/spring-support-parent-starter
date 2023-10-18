package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@TableName(value = "sys_gen_remark")
public class SysGenRemark implements Serializable {
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
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String remarkName;

    /**
     * genId
     */
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 表
     */
    @TableField(value = "remark_table")
    @Size(max = 255,message = "表最大长度要小于 255")
    private String remarkTable;

    /**
     * 数据库
     */
    @TableField(value = "remark_database")
    @Size(max = 255,message = "数据库最大长度要小于 255")
    private String remarkDatabase;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * 字段
     */
    @TableField(value = "remark_column")
    @Size(max = 255,message = "字段最大长度要小于 255")
    private String remarkColumn;

    private static final long serialVersionUID = 1L;
}