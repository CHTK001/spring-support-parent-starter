package com.chua.starter.account.sendlog.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一发送日志持久化实体（邮件 / 短信通道级）。
 *
 * <p>由 {@code MailboxService} 发信门面在每次真实发送后异步写入：
 * 成功与失败都落一条，记录账号、通道、收件人、完整内容与上游结果，
 * 供「发送日志」管理页排查发送链路问题。</p>
 *
 * <p>业务侧留痕（验证码日志 / 消息推送日志）仍在各业务表，本表只记录
 * 通道视角的发送事实，两边维度互补。</p>
 *
 * <p>列名统一以表名 {@code sys_account_send_log_} 作前缀，与账号模块
 * 其它实体保持一致。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@Data
@TableName("sys_account_send_log")
public class SysAccountSendLog implements Serializable {

    /**
     * 串行版本
     */
    private static final long serialVersionUID = 1L;

    /**
     * 日志主键（UUID，写入时生成）
     */
    @TableId(value = "sys_account_send_log_id", type = IdType.INPUT)
    private String sendLogId;

    /**
     * 发送时间
     */
    @TableField("sys_account_send_log_time")
    private LocalDateTime sendLogTime;

    /**
     * 账号种类（EMAIL / SMS）
     */
    @TableField("sys_account_send_log_kind")
    private String sendLogKind;

    /**
     * 发信账号 ID
     */
    @TableField("sys_account_send_log_account_id")
    private String sendLogAccountId;

    /**
     * 发信账号名称
     */
    @TableField("sys_account_send_log_account_name")
    private String sendLogAccountName;

    /**
     * 发送通道（email / alibaba-sms / tencent-sms / baidu-sms）
     */
    @TableField("sys_account_send_log_provider")
    private String sendLogProvider;

    /**
     * 收件人（邮箱地址 / 手机号）
     */
    @TableField("sys_account_send_log_target")
    private String sendLogTarget;

    /**
     * 抄送（仅邮件，逗号分隔）
     */
    @TableField("sys_account_send_log_cc")
    private String sendLogCc;

    /**
     * 主题（邮件）
     */
    @TableField("sys_account_send_log_subject")
    private String sendLogSubject;

    /**
     * 完整正文
     */
    @TableField("sys_account_send_log_content")
    private String sendLogContent;

    /**
     * 模板编码
     */
    @TableField("sys_account_send_log_template_code")
    private String sendLogTemplateCode;

    /**
     * 是否成功：{@code true} 成功 / {@code false} 失败
     */
    @TableField("sys_account_send_log_success")
    private Boolean sendLogSuccess;

    /**
     * 服务商消息标识
     */
    @TableField("sys_account_send_log_message_id")
    private String sendLogMessageId;

    /**
     * 失败原因
     */
    @TableField("sys_account_send_log_error")
    private String sendLogError;

    /**
     * 耗时（毫秒）
     */
    @TableField("sys_account_send_log_cost_ms")
    private Long sendLogCostMs;

    /**
     * 调用来源（mailbox / system:sms-code / system:message-push）
     */
    @TableField("sys_account_send_log_source")
    private String sendLogSource;

    /**
     * 写入时间
     */
    @TableField("sys_account_send_log_created_at")
    private LocalDateTime sendLogCreatedAt;
}
