package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Prometheus 自定义看板。
 *
 * <p>一行 = 一个 Prometheus 数据源的一份布局：布局 JSON（卡片类型、位置、背景色、
 * 自定义 CSS、数据采集脚本、ECharts 参数）由前端定义与解析，后端只负责存取，
 * 因此升级卡片模型不需要动表结构。</p>
 *
 * <p>{@code embed_token} 支撑「生成链接给别的系统嵌入」：公开只读接口凭令牌取
 * 看板与查询数据，不需要登录态。</p>
 *
 * <p>表 {@code datasource_prometheus_dashboard}：业务列以完整表名作前缀；审计字段继承 {@link SysBase}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Prometheus 自定义看板")
@TableName("datasource_prometheus_dashboard")
public class MsPrometheusDashboard extends SysBase {

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
     * 数据源 id（datasource_info.id，prometheus 类型）
     */
    @TableField("datasource_prometheus_dashboard_datasource_id")
    @Schema(description = "数据源 id")
    private Long datasourceId;

    /**
     * 看板名称
     */
    @TableField("datasource_prometheus_dashboard_name")
    @Schema(description = "看板名称")
    private String dashboardName;

    /**
     * 布局配置 JSON 文本
     */
    @TableField("datasource_prometheus_dashboard_config")
    @Schema(description = "布局配置（JSON 文本）")
    private String dashboardConfig;

    /**
     * 嵌入令牌
     */
    @TableField("datasource_prometheus_dashboard_embed_token")
    @Schema(description = "嵌入令牌")
    private String embedToken;
}
