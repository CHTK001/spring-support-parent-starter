package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模型价格/指标快照查询与刷新端点。
 *
 * <p>前端在「模型定价」面板打开时调用 {@code GET /api/model-metrics} 获取当前快照，
   * 同时通过 web套接字 订阅 {@code model.metrics.push} 主题接收后台定时推送的增量更新。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiModelMetricsController {

    /**
     * 模型价格/指标快照服务
     */
    private final ModelMetricsSnapshotService snapshotService;

    /**
     * 获取当前模型价格/指标快照。
     *
     * <p>优先从 Redis 读取上次构建的快照；Redis 无缓存时现场构建一份返回。
       * 快照结构：{@编码 { 更新_at, 数量, 模型: [{ 提供者_标识, 提供者_名称,
      * platform, 模型, 已启用, description, 源, price_输入_per_musd,
      * price_输出_per_musd, 智能_索引, 输出_速度_tps,
      * 延迟_第一个_令牌_seconds, icon_url, 延迟_ms, 探针_延迟_ms, ... }] }}</p>
     *
     * @return 快照数据
     */
    @GetMapping("/model-metrics")
    public ApiResult<Map<String, Object>> getSnapshot() {
        return ApiResult.ok(snapshotService.latestSnapshot());
    }

    /**
      * 手动触发模型价格/指标快照刷新并推送 web套接字。
     *
     * <p>前端「立即刷新」按钮调用；构建完成后写入 Redis 并广播 WS 主题。</p>
     *
     * @return 刷新后的快照
     */
    @PostMapping("/model-metrics/refresh")
    public ApiResult<Map<String, Object>> refreshSnapshot() {
        log.info("[ModelMetrics] 手动触发快照刷新");
        return ApiResult.ok(snapshotService.buildAndPushSnapshot());
    }
}
