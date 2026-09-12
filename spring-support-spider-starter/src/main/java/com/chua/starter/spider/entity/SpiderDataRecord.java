package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
* 采集数据记录实体。
*
* <p>对应 {@code spider_data_record} 表，由 spider.output 节点写入采集结果，
* raw_数据 以 JSON 保存完整原始数据，供前端检索。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_data_record")
@Schema(name = "采集数据记录")
public class SpiderDataRecord extends SysBase {

    /**
    * 主键 标识（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID")
    /** 标识 */
    private Long id;

    /**
    * 执行号（追踪标识）
     */
    @TableField("execution_no")
    @Schema(description = "执行号")
    /** 执行no */
    private String executionNo;

    /**
    * 爬虫编码
     */
    @TableField("definition_code")
    @Schema(description = "爬虫编码")
    /** Definition代码 */
    private String definitionCode;

    /**
    * 标题
     */
    @TableField("title")
    @Schema(description = "标题")
    /** 标题 */
    private String title;

    /**
    * 内容
     */
    @TableField("content")
    @Schema(description = "内容")
    /** 内容 */
    private String content;

    /**
    * 来源 URL
     */
    @TableField("source_url")
    @Schema(description = "来源 URL")
    /** 来源URL */
    private String sourceUrl;

    /**
    * 原始采集数据（JSON）
     */
    @TableField("raw_data")
    @Schema(description = "原始采集数据")
    /** RAW数据 */
    private String rawData;

    /**
    * 状态（成功/失败）
     */
    @TableField("status")
    @Schema(description = "状态")
    /** 状态 */
    private String status;

    /**
    * 失败信息
     */
    @TableField("error_message")
    @Schema(description = "失败信息")
    /** 错误消息 */
    private String errorMessage;

    /**
    * 记录时间（写库时间）
     */
    @TableField("record_time")
    @Schema(description = "记录时间")
    /** Record时间 */
    private LocalDateTime recordTime;

    /**
    * 逻辑删除标记（0 未删除 / 1 已删除）
     */
    @TableField("sys_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** SYS删除标记 */
    private Integer sysDeleted;
}