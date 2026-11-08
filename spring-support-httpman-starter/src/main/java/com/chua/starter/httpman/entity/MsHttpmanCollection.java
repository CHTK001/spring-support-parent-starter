package com.chua.starter.httpman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

/**
 * 接口调试控制台 - 请求集合
 * <p>对应数据库表 ms_httpman_collection。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("ms_httpman_collection")
public class MsHttpmanCollection extends SysBase {

    /**
     * 主键标识
     */
    @TableId(value = "ms_httpman_collection_id", type = IdType.ASSIGN_UUID)
    /**
     * 集合标识
     */
    private String collectionId;

    /**
     * 父级集合标识（默认为根目录）
     */
    @TableField("ms_httpman_collection_parent_id")
    /**
     * 集合父ID
     */
    private String collectionParentId;

    /**
     * 集合名称
     */
    @TableField("ms_httpman_collection_name")
    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 备注
     */
    @TableField("ms_httpman_collection_remark")
    /**
     * 集合备注
     */
    private String collectionRemark;

    /**
     * 排序
     */
    @TableField("ms_httpman_collection_order")
    /**
     * 集合排序
     */
    private Integer collectionOrder;

    /**
     * 是否展开（菜单树默认展开层数）
     */
    @TableField("ms_httpman_collection_expand")
    /**
     * 集合展开
     */
    private Boolean collectionExpand;
}
