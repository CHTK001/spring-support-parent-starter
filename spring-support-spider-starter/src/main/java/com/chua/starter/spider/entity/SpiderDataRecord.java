package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集数据记录实体。
 *
 * <p>对应 {@code spider_data_record} 表，由 spider.output 节点写入采集结果，
 * raw_data 以 JSON 保存完整原始数据，供前端检索。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("spider_data_record")
public class SpiderDataRecord extends SpiderBase {

    /**
     * 执行号（traceId）
     */
    private String executionNo;

    /**
     * 爬虫编码
     */
    private String definitionCode;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 来源 URL
     */
    private String sourceUrl;

    /**
     * 原始采集数据（JSON）
     */
    private String rawData;

    /**
     * 状态（SUCCESS/FAIL）
     */
    private String status;

    /**
     * 失败信息
     */
    private String errorMessage;

    /**
     * 记录时间（写库时间）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime recordTime;
}