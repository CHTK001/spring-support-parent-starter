package com.chua.starter.message.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
* 消息推送日志实体
* <p>
* 对应表 sys_消息_日志，记录消息推送历史，包含发送内容、结果、耗时等。
* </p>
*
* @since 2026/08/03
* @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_log")
public class SysMessageLog extends SysBase {

    /**
    * 日志标识 - 主键自增
    */
    @TableId(value = "sys_message_log_id", type = IdType.AUTO)
    /** SYS消息日志标识 */
    private Integer sysMessageLogId;

    /**
    * 接收人 - 手机号/邮箱/用户标识等
    */
    private String sysMessageLogTarget;

    /**
    * 模板标识 - 使用的模板
    */
    private Integer sysMessageTemplateId;

    /**
    * 提供商标识 - 使用的消息提供商
    */
    private Integer sysMessageProviderId;

    /**
    * 渠道标识 - 实际发送渠道
    */
    private String sysMessageLogChannel;

    /**
    * 发送内容 - 最终发送的消息内容
    */
    private String sysMessageLogContent;

    /**
    * 模板参数 - JSON格式的模板变量
    */
    private String sysMessageLogParam;

    /**
    * 耗时 - 发送耗时(毫秒)
    */
    private BigDecimal sysMessageCost;

    /**
    * 返回码 - 成功/失败
    */
    private String sysMessageCode;

    /**
    * 返回消息 - 错误信息
    */
    private String sysMessageMessage;

    /**
    * 消息主题 - 邮件标题等
    */
    private String sysMessageLogSubject;

    /**
    * 消息标识 - 服务商返回的消息标识
    */
    private String sysMessageLogMessageId;

    /**
    * 批次号 - 同一次发送请求的接收人共享
    */
    private String sysMessageBatchId;

    /**
    * 追踪标识 - 邮件阅读追踪 令牌，用于回写已读状态
    */
    private String sysMessageTrackToken;

    /**
    * 已读状态 - 0:未读, 1:已读
    */
    private Integer sysMessageReadStatus;

    /**
    * 已读时间 - 收件人打开邮件触发追踪的时间
    */
    private java.time.LocalDateTime sysMessageReadTime;

    /**
    * 是否删除 - 0:未删除, 1:已删除
    */
    private Integer sysMessageLogDelete;
}
