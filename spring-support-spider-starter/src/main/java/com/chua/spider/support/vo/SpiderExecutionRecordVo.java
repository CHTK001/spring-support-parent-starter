package com.chua.spider.support.vo;

import com.chua.spider.support.entity.SpiderExecutionRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫执行记录视图对象。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "爬虫执行记录视图")
public class SpiderExecutionRecordVo {

    /**
     * 主键
     */
    private Long spiderId;

    /**
     * 执行批次号
     */
    private String spiderExecutionNo;

    /**
     * 爬虫编码
     */
    private String spiderDefinitionCode;

    /**
     * 触发方式
     */
    private String spiderTriggerType;

    /**
     * 执行状态
     */
    private String spiderExecutionStatus;

    /**
     * 本次执行到达的最大深度
     */
    private Integer spiderMaxDepthReached;

    /**
     * 采集条数
     */
    private Integer spiderCollectedCount;

    /**
     * URL 总数
     */
    private Integer spiderUrlTotal;

    /**
     * URL 完成数
     */
    private Integer spiderUrlCompleted;

    /**
     * 开始时间
     */
    private LocalDateTime spiderStartedAt;

    /**
     * 结束时间
     */
    private LocalDateTime spiderFinishedAt;

    /**
     * 错误信息
     */
    private String spiderErrorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 将实体转换为视图对象。
     *
     * @param e 实体
     * @return 视图对象
     */
    public static SpiderExecutionRecordVo from(SpiderExecutionRecord e) {
        if (e == null) {
            return null;
        }
        SpiderExecutionRecordVo v = new SpiderExecutionRecordVo();
        v.spiderId = e.getSpiderId();
        v.spiderExecutionNo = e.getSpiderExecutionNo();
        v.spiderDefinitionCode = e.getSpiderDefinitionCode();
        v.spiderTriggerType = e.getSpiderTriggerType();
        v.spiderExecutionStatus = e.getSpiderExecutionStatus();
        v.spiderMaxDepthReached = e.getSpiderMaxDepthReached();
        v.spiderCollectedCount = e.getSpiderCollectedCount();
        v.spiderUrlTotal = e.getSpiderUrlTotal();
        v.spiderUrlCompleted = e.getSpiderUrlCompleted();
        v.spiderStartedAt = e.getSpiderStartedAt();
        v.spiderFinishedAt = e.getSpiderFinishedAt();
        v.spiderErrorMessage = e.getSpiderErrorMessage();
        v.createTime = e.getCreateTime();
        return v;
    }
}