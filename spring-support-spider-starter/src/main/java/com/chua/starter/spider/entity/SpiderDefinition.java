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
 * 爬虫定义实体。
 *
 * <p>对应 {@code spider_definition} 表，描述一个爬虫的基本信息：编码、名称、
 * 状态、关联流程与定时调度表达式。cron_expression 为空时不参与自动调度。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_definition")
@Schema(name = "爬虫定义")
public class SpiderDefinition extends SysBase {

    /**
     * 主键 标识（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID")
    /** 标识 */
    private Long id;

    /**
    * 爬虫唯一编码
    */
    @TableField("code")
    @Schema(description = "爬虫唯一编码")
    /** 代码 */
    private String code;

    /**
    * 爬虫名称
    */
    @TableField("name")
    @Schema(description = "爬虫名称")
    /** 名称 */
    private String name;

    /**
    * 描述
    */
    @TableField("description")
    @Schema(description = "描述")
    /** 描述 */
    private String description;

    /**
    * 关联流程 标识（蜘蛛_流.标识）
    */
    @TableField("flow_id")
    @Schema(description = "关联流程 ID")
    /** 流标识 */
    private Long flowId;

    /**
    * 是否启用（1 启用 / 0 停用）
    */
    @TableField("enabled")
    @Schema(description = "是否启用")
    /** 是否启用 */
    private Integer enabled;

    /**
    * 定时 cron 表达式，为空则不自动调度
    */
    @TableField("cron_expression")
    @Schema(description = "定时 cron 表达式")
    /** Cron表达式 */
    private String cronExpression;

    /**
    * 执行类型（ONCE 一次 / 调度 定时），为空时按 cron 是否配置自动判定
    */
    @TableField("executor_type")
    @Schema(description = "执行类型（ONCE 一次 / SCHEDULE 定时）")
    /** 执行器类型 */
    private String executorType;

    /**
    * 上次执行时间
    */
    @TableField("last_run_at")
    @Schema(description = "上次执行时间")
    /** 最后RUNAT */
    private LocalDateTime lastRunAt;

    /**
    * 上次执行状态（成功/失败/RUNNING）
    */
    @TableField("last_status")
    @Schema(description = "上次执行状态")
    /** 最后状态 */
    private String lastStatus;

    /**
    * 上次执行消息
    */
    @TableField("last_message")
    @Schema(description = "上次执行消息")
    /** 最后消息 */
    private String lastMessage;

    /**
    * 入口 URL
    */
    @TableField("entry_url")
    @Schema(description = "入口 URL")
    /** entryurl */
    private String entryUrl;

    /**
    * 站点主机
    */
    @TableField("site_host")
    @Schema(description = "站点主机")
    /** Site主机 */
    private String siteHost;

    /**
    * 是否深度采集（1/0）
    */
    @TableField("collect_deep_url")
    @Schema(description = "是否深度采集")
    /** collectdeepurl */
    private Integer collectDeepUrl;

    /**
    * 最大深度
    */
    @TableField("max_depth")
    @Schema(description = "最大深度")
    /** 最大值深度 */
    private Integer maxDepth;

    /**
    * 最大页数
    */
    @TableField("max_pages")
    @Schema(description = "最大页数")
    /** 最大值pages */
    private Integer maxPages;

    /**
    * 执行轮次
    */
    @TableField("executor_rounds")
    @Schema(description = "执行轮次")
    /** 执行器rounds */
    private Integer executorRounds;

    /**
    * 备注
    */
    @TableField("remark")
    @Schema(description = "备注")
    /** 评论 */
    private String remark;

    /**
    * 逻辑删除标记（0 未删除 / 1 已删除）
    */
    @TableField("sys_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** SYS删除标记 */
    private Integer sysDeleted;
}
