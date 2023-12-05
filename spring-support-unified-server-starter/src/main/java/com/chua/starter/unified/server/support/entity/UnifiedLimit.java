package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 限流
 */
@Data
@TableName(value = "unified_limit")
public class UnifiedLimit {
    @TableId(value = "unified_limit_id", type = IdType.AUTO)
    private Integer unifiedLimitId;

    /**
     * 限流类型; URL, REMOTE
     */
    @TableField(value = "unified_limit_type")
    private String unifiedLimitType;

    /**
     * 限流地址
     */
    @TableField(value = "unified_limit_mapping")
    private String unifiedLimitMapping;

    /**
     * 每秒次数
     */
    @TableField(value = "unified_limit_permits")
    private String unifiedLimitPermits;

    /**
     * 解释器类型
     */
    @TableField(value = "unified_limit_resolver")
    private String unifiedLimitResolver;

    /**
     * 状态: 0： 禁用
     */
    @TableField(value = "unified_limit_status")
    private Integer unifiedLimitStatus;

    /**
     * 配置环境
     */
    @TableField(value = "unified_limit_profile")
    private String unifiedLimitProfile;

    /**
     * 应用名称
     */
    @TableField(value = "unified_appname")
    private String unifiedAppname;

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
     * REMOTE模式下的黑名单
     */
    @TableField(value = "unified_limit_remote_list")
    private String unifiedLimitRemoteList;
}