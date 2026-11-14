package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 服务管理 —— 节点统计摘要（左栏计数与表单重名校验用）。
 *
 * <p>分页接口只返回当前页的行，页面左栏的计数与新建表单的重名校验却需要
 * 「全部节点」的规模，所以单独给一个轻量摘要接口：一次请求同时拿到计数与名称表，
 * 避免为了两个数字再拉一遍全量视图。</p>
 *
 * <p>计数口径与 {@code monitor 前端 utils/nodeList.ts#summarizeNodes} 保持一致：</p>
 * <ul>
 *   <li>{@code status} 只有 RUNNING / STOPPED / ERROR 三种，枚举外的历史数据单独计入
 *       {@code unknown}，否则「分项之和 ≠ 总数」而没人说得清多出来的是什么；</li>
 *   <li>{@code plugins} 累加各节点的过滤器个数，不按启用状态去重 ——
 *       展示的是「挂了多少个」而不是「生效了多少个」。</li>
 * </ul>
 *
 * @param total    节点总数
 * @param running  运行中
 * @param stopped  已停止
 * @param error    异常
 * @param unknown  状态未知（枚举外的值）
 * @param plugins  挂载的过滤器总数
 * @param names    全部节点名称（新建 / 编辑表单做重名校验）
 * @author CH
 * @since 4.0.0.56
 */
@Schema(description = "服务管理-节点统计摘要")
public record ServerNodeSummary(
        @Schema(description = "节点总数") long total,
        @Schema(description = "运行中") long running,
        @Schema(description = "已停止") long stopped,
        @Schema(description = "异常") long error,
        @Schema(description = "状态未知（枚举外的值）") long unknown,
        @Schema(description = "挂载的过滤器总数") long plugins,
        @Schema(description = "全部节点名称") List<String> names) {
}
