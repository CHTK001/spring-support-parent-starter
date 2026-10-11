package com.chua.starter.datasourcehub.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DatasourceOperLogVO;
import com.chua.starter.datasourcehub.support.service.DatasourceOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 数据源中心操作日志查询。
 *
 * <p>与 {@code GET /v2/log/page} 的关系：那个端点在当前部署上稳定返回
 * {@code S0503C0000 资源不存在}（表不存在，且 {@code @SysLog} 切面发的事件
 * 全仓无监听者，本来也不会有数据）。本端点读本模块自己落的
 * {@code ms_datasource_oper_log}，按数据源分页，是真正能返回数据的那条路。</p>
 *
 * <p>写入侧不在这里：本控制器只读。日志由 {@code @SysLog} 切面 → 监听器写入，
 * 这样新增受管端点时只要加上注解就会自动留痕，不必记得再改一处埋点。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Tag(name = "数据源中心 - 操作日志", description = "数据源台账、隧道与数据库控制台的操作留痕")
@RestController
@RequestMapping("/ms/datasource/oper-log")
@RequiredArgsConstructor
public class DatasourceOperLogController {

    private final DatasourceOperLogService operLogService;

    /**
     * 按数据源分页查操作日志。
     *
     * @param datasourceId 数据源 ID
     * @param action       操作码过滤，空则全部
     * @param from         起始时间（ISO 本地时间），空则不限
     * @param to           截止时间（ISO 本地时间），空则不限
     * @param page         页码，从 1 开始
     * @param size         每页条数，服务端封顶 200
     * @return 分页结果
     */
    @Operation(summary = "数据源操作日志分页", description = "最新在前；按 datasourceId 必填过滤")
    @GetMapping("/{datasourceId}")
    public ReturnResult<Page<DatasourceOperLogVO>> page(
            @PathVariable Long datasourceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return operLogService.page(datasourceId, action, from, to, page, size);
    }
}