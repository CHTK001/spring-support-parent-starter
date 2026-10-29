package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 动态接口定义（数据源接口管理）。
 *
 * <p>每一行 = 一个「绑定到某个数据源 datasourceId 的 SQL 查询」的对外接口：
 * 请求打到接口地址后，携带参数走 {@code ${name}} 占位符绑定，经
 * {@code /ms/monitor/db/execute} 同口径执行链查库返回结果。</p>
 *
 * <p>数据源可以是普通 JDBC（单体接口），也可以是 {@code type=calcite} 的合并数据源
 * （联合接口：跨库 JOIN 查询）。发布模式双轨支持：{@code embedded} 走 Spring MVC
 * 动态注册进当前应用；{@code standalone} 走 common-starter 的独立 Server。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "动态接口定义")
@TableName("ms_datasource_api")
public class MsDatasourceApi implements Serializable {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 接口名
     */
    @TableField("api_name")
    @Schema(description = "接口名", example = "按日订单汇总")
    private String apiName;

    /**
     * 对外地址段（同数据源下唯一），如 /order/summary
     */
    @TableField("api_path")
    @Schema(description = "对外地址段（同数据源下唯一）", example = "/order/summary")
    private String apiPath;

    /**
     * HTTP 方法：GET / POST / PUT
     */
    @TableField("api_method")
    @Schema(description = "HTTP 方法", example = "GET")
    private String apiMethod;

    /**
     * 绑定的数据源 id；0 = 应用默认主数据源
     */
    @TableField("datasource_id")
    @Schema(description = "数据源 id，0=默认主数据源", example = "3")
    private Long datasourceId;

    /**
     * 描述
     */
    @TableField("description")
    @Schema(description = "描述", example = "按日汇总订单金额与件数")
    private String description;

    /**
     * 保存的查询语句（接口本体）
     */
    @TableField("sql_text")
    @Schema(description = "SQL 查询语句")
    private String sqlText;

    /**
     * 参数定义（JSON 数组文本）：[{name,type,required,example}]
     */
    @TableField("param_defs")
    @Schema(description = "参数定义(JSON)")
    private String paramDefs;

    /**
     * 鉴权方式：oauth / appkey / none
     */
    @TableField("auth_mode")
    @Schema(description = "鉴权方式 oauth/appkey/none", example = "oauth")
    private String authMode;

    /**
     * 结果缓存秒数；0 = 不缓存，>0 开启
     */
    @TableField("cache_ttl_seconds")
    @Schema(description = "缓存秒数，0=不缓存", example = "60")
    private Integer cacheTtlSeconds;

    /**
     * 发布模式：embedded / standalone
     */
    @TableField("publish_mode")
    @Schema(description = "发布模式 embedded/standalone", example = "embedded")
    private String publishMode;

    /**
     * universal 独立 Server 监听端口（standalone 模式使用；0 = 默认端口）
     */
    @TableField("standalone_port")
    @Schema(description = "独立 Server 端口，0=默认", example = "18099")
    private Integer standalonePort;

    /**
     * 是否启用：0 停用、1 启用
     */
    @TableField("enabled")
    @Schema(description = "是否启用 0停用 1启用", example = "1")
    private Integer enabled;

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

    /**
     * 对外可调用地址（嵌入式模式的完整 URL 前缀，运行时注入，不落库）。
     *
     * <p>表里没有对应列：由发布服务在回传前临时拼上，前端展示「复制链接」用。</p>
     */
    @TableField(exist = false)
    @Schema(description = "对外地址（运行时注入）", example = "/ms/datasource/api/execute/1")
    private String externalUrl;
}