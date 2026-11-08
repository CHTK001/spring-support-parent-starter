package com.chua.starter.httpman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

/**
 * 接口调试控制台 - 环境变量（类似 Postman 的 Environment）
 * <p>对应数据库表 ms_httpman_variable。变量按作用域分组：
 * GLOBAL 全局、COLLECTION 集合级、REQUEST 请求级（由请求的 {{variable}} 模板引用）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("ms_httpman_variable")
public class MsHttpmanVariable extends SysBase {

    /**
     * 主键标识
     */
    @TableId(value = "ms_httpman_variable_id", type = IdType.ASSIGN_UUID)
    /**
     * 变量标识
     */
    private String variableId;

    /**
     * 变份量所属作用域：GLOBAL / COLLECTION / REQUEST
     */
    @TableField("ms_httpman_variable_scope")
    /**
     * 变量作用域
     */
    private String variableScope;

    /**
     * 所属集合标识（COLLECTION 作用域时有效，NULL 表示全局）
     */
    @TableField("ms_httpman_variable_collection_id")
    /**
     * 变量集合ID
     */
    private String variableCollectionId;

    /**
     * 变量名
     */
    @TableField("ms_httpman_variable_name")
    /**
     * 变量名称
     */
    private String variableName;

    /**
     * 变量值
     */
    @TableField("ms_httpman_variable_value")
    /**
     * 变量值
     */
    private String variableValue;

    /**
     * 备注
     */
    @TableField("ms_httpman_variable_remark")
    /**
     * 变量备注
     */
    private String variableRemark;
}
