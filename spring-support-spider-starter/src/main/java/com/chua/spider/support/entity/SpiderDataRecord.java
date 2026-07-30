package com.chua.spider.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集数据实体。
 * <p>每条 {@link com.chua.spider.support.model.SpiderResult} 落地一条记录。
 * 爬取深度 {@code spiderDepth} 来自引擎写入的 {@link com.chua.spider.support.model.SpiderResult#getDepth()}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_data_record")
@Schema(description = "采集数据记录")
public class SpiderDataRecord {

    /**
     * 主键，自增
     */
    @TableId(value = "spider_id", type = IdType.AUTO)
    private Long spiderId;

    /**
     * 爬虫业务编码
     */
    @TableField("spider_definition_code")
    private String spiderDefinitionCode;

    /**
     * 执行批次号
     */
    @TableField("spider_execution_no")
    private String spiderExecutionNo;

    /**
     * 目标 URL
     */
    @TableField("spider_target_url")
    private String spiderTargetUrl;

    /**
     * 数据 schema 编码
     */
    @TableField("spider_data_code")
    private String spiderDataCode;

    /**
     * 数据项名称（页面标题等）
     */
    @TableField("spider_data_name")
    private String spiderDataName;

    /**
     * JSON 字符串载荷（含 {@code depth} 字段）
     */
    @TableField("spider_data_payload")
    private String spiderDataPayload;

    /**
     * 状态：SUCCESS / FAILED / SKIPPED
     */
    @TableField("spider_data_status")
    private String spiderDataStatus;

    /**
     * 爬取深度：0=列表页/种子页，1=第一层详情页，依此类推
     */
    @TableField("spider_depth")
    private Integer spiderDepth;

    /**
     * 创建时间，插入自动填充
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}