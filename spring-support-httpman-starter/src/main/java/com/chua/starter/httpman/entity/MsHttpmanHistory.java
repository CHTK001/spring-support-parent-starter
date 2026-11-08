package com.chua.starter.httpman.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

import java.time.Duration;

/**
 * 接口调试控制台 - 请求历史（每次运行的完整记录）
 * <p>对应数据库表 ms_httpman_history。历史记录只追加不修改，用于回溯每次请求的输出版本。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("ms_httpman_history")
public class MsHttpmanHistory extends SysBase {

    /**
     * 主键标识
     */
    @TableId(value = "ms_httpman_history_id", type = IdType.ASSIGN_UUID)
    /**
     * 历史标识
     */
    private String historyId;

    /**
     * 请求标识
     */
    @TableField("ms_httpman_history_request_id")
    /**
     * 历史请求ID
     */
    private String historyRequestId;

    /**
     * 所属集合标识
     */
    @TableField("ms_httpman_history_collection_id")
    /**
     * 历史集合ID
     */
    private String historyCollectionId;

    /**
     * 请求名称（冗余，列表直接展示）
     */
    @TableField("ms_httpman_history_request_name")
    /**
     * 历史请求名称
     */
    private String historyRequestName;

    /**
     * 请求方法（冗余）
     */
    @TableField("ms_httpman_history_method")
    /**
     * 历史请求方法
     */
    private String historyMethod;

    /**
     * 请求 URL（冗余，方便搜索）
     */
    @TableField("ms_httpman_history_url")
    /**
     * 历史请求地址
     */
    private String historyUrl;

    /**
     * 响应状态码
     */
    @TableField("ms_httpman_history_status")
    /**
     * 历史状态码
     */
    private Integer historyStatus;

    /**
     * 响应耗时（毫秒）
     */
    @TableField("ms_httpman_history_cost_ms")
    /**
     * 历史耗时MS
     */
    private Long historyCostMs;

    /**
     * 响应时间
     */
    @TableField("ms_httpman_history_response_time")
    /**
     * 历史响应时间
     */
    private String historyResponseTime;

    /**
     * 请求头（发起时的完整请求头，JSON 数组；敏感头不解析/脱敏后保存）
     */
    @TableField("ms_httpman_history_request_headers")
    /**
     * 历史请求头
     */
    private String historyRequestHeaders;

    /**
     * 请求正文（冗余，列表不展示 Content-Type，详情时整体读回）
     */
    @TableField("ms_httpman_history_request_body")
    /**
     * 历史请求正文
     */
    private String historyRequestBody;

    /**
     * 响应头（JSON 数组）
     */
    @TableField("ms_httpman_history_response_headers")
    /**
     * 历史响应头
     */
    private String historyResponseHeaders;

    /**
     * 响应正文（LONGTEXT，详情时整体读回；列表只读摘要）
     */
    @TableField("ms_httpman_history_response_body")
    /**
     * 历史响应正文
     */
    private String historyResponseBody;

    /**
     * 错误信息（请求失败时无）
     */
    @TableField("ms_httpman_history_error")
    /**
     * 历史错误信息
     */
    private String historyError;

    /**
     * 是否被标记为报警（HTTP 状态码 >= 400 或耗时超过阈值）
     */
    @TableField("ms_httpman_history_alert")
    /**
     * 历史报警
     */
    private Boolean historyAlert;
}
