package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫定义实体。
 * <p>记录一个爬虫的全部配置：入口地址、调度策略、深度与去重等。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("spider_definition")
@Schema(description = "爬虫定义")
public class SpiderDefinition {

    /**
     * 主键，自增
     */
    @TableId(value = "spider_id", type = IdType.AUTO)
    private Long spiderId;

    /**
     * 爬虫业务编码，全局唯一
     */
    @TableField("spider_code")
    private String spiderCode;

    /**
     * 爬虫名称
     */
    @TableField("spider_name")
    private String spiderName;

    /**
     * 入口 URL，爬虫起点
     */
    @TableField("spider_entry_url")
    private String spiderEntryUrl;

    /**
     * 站点主机名
     */
    @TableField("spider_site_host")
    private String spiderSiteHost;

    /**
     * 调度类型：ONCE / N_TIMES / CRON
     */
    @TableField("spider_executor_type")
    private String spiderExecutorType;

    /**
     * 调度轮次
     */
    @TableField("spider_executor_rounds")
    private Integer spiderExecutorRounds;

    /**
     * 是否采集种子页内链入的子页面（0=否，1=是）
     */
    @TableField("spider_collect_deep_url")
    private Integer spiderCollectDeepUrl;

    /**
     * 允许的最大爬取深度
     */
    @TableField("spider_max_depth")
    private Integer spiderMaxDepth;

    /**
     * 最大采集页数
     */
    @TableField("spider_max_pages")
    private Integer spiderMaxPages;

    /**
     * 去重策略
     */
    @TableField("spider_dedup_strategy")
    private String spiderDedupStrategy;

    /**
     * URL 存储类型
     */
    @TableField("spider_url_storage_type")
    private String spiderUrlStorageType;

    /**
     * 状态：0 禁用 / 1 启用
     */
    @TableField("spider_status")
    private Integer spiderStatus;

    /**
     * 备注
     */
    @TableField("spider_remark")
    private String spiderRemark;

    /**
     * 创建时间，插入自动填充
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间，插入与更新均自动填充
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
