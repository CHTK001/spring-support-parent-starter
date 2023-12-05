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
    * 限流日志
    */
@Data
@TableName(value = "unified_limit_log")
public class UnifiedLimitLog {
    @TableId(value = "unified_limit_log_id", type = IdType.AUTO)
    private Integer unifiedLimitLogId;

    /**
     * 限流的应用
     */
    @TableField(value = "unified_limit_log_appname")
    private String unifiedLimitLogAppname;

    /**
     * 请求地址
     */
    @TableField(value = "unified_limit_log_request_url")
    private String unifiedLimitLogRequestUrl;

    /**
     * 请求的地址
     */
    @TableField(value = "unified_limit_log_request_address")
    private String unifiedLimitLogRequestAddress;

    /**
     * 请求时间
     */
    @TableField(value = "create_time")
    private Date createTime;
}