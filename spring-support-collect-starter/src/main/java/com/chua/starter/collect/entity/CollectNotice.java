package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集执行通知配置。
 *
 * <p>一条配置对应一个渠道：任务落到终态后按 {@code timing} 决定是否推送。
 * 通知是平台级配置，没有流程维度，库里的每一条都对该次执行生效。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_notice")
@Schema(description = "数据采集-执行通知")
public class CollectNotice extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_notice_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectNoticeId;

    /**
     * 通知名称。
     */
    @Schema(description = "通知名称")
    private String collectNoticeName;

    /**
     * 渠道标识，取值见 {@code CollectNotifyChannel#channel()}。
     */
    @Schema(description = "渠道 email/webhook")
    private String collectNoticeChannel;

    /**
     * 收件邮箱（逗号分隔）或回调地址。
     */
    @Schema(description = "收件邮箱或回调地址")
    private String collectNoticeTarget;

    /**
     * 触发时机 - SUCCESS/FAILURE/ALWAYS。
     */
    @Schema(description = "触发时机 SUCCESS/FAILURE/ALWAYS")
    private String collectNoticeTiming;

    /**
     * 标题模板。
     */
    @Schema(description = "标题模板")
    private String collectNoticeSubject;

    /**
     * 正文模板。
     */
    @Schema(description = "正文模板")
    private String collectNoticeContent;

    /**
     * 是否启用，0 停用 1 启用。
     */
    @Schema(description = "是否启用 0停用 1启用")
    private Integer collectNoticeEnabled;

    /**
     * 最近一次发送结果。
     */
    @Schema(description = "最近一次发送结果")
    private String collectNoticeLastResult;

    /**
     * 最近一次发送时间。
     */
    @Schema(description = "最近一次发送时间")
    private LocalDateTime collectNoticeLastTime;
}
