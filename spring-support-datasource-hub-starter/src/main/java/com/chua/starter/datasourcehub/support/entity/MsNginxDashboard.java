package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Nginx 自定义看板。
 *
 * <p>一行 = 一个 Nginx 数据源的一份布局：布局 JSON（卡片类型、位置、背景色、
 * 自定义 CSS、数据采集脚本、ECharts 参数）由前端定义与解析，后端只负责存取，
 * 因此升级卡片模型不需要动表结构。</p>
 *
 * <p>{@code embed_token} 支撑「生成链接给别的系统嵌入」：公开只读接口凭令牌取
 * 看板与查询数据，不需要登录态。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Nginx 自定义看板")
@TableName("ms_nginx_dashboard")
public class MsNginxDashboard implements Serializable {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    /**
     * 数据源 id（ms_datasource.id，nginx 类型）
     */
    @TableField("datasource_id")
    @Schema(description = "数据源 id")
    private Long datasourceId;

    /**
     * 看板名称
     */
    @TableField("dashboard_name")
    @Schema(description = "看板名称")
    private String dashboardName;

    /**
     * 布局配置 JSON 文本
     */
    @TableField("dashboard_config")
    @Schema(description = "布局配置（JSON 文本）")
    private String dashboardConfig;

    /**
     * 嵌入令牌
     */
    @TableField("embed_token")
    @Schema(description = "嵌入令牌")
    private String embedToken;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}