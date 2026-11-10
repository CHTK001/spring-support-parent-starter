package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.operlog.OperLogTimeParser;
import com.chua.starter.monitor.support.server.model.ApmOperLogVO;
import com.chua.starter.monitor.support.service.ApmOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * APM 操作日志查询。
 *
 * <p>写入侧不在这里：本控制器只读。APM 接口不走 Spring MVC，
 * 日志由 {@code ApmDispatcher} → {@code ApmOperLogSink} 写入。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Tag(name = "监控-APM - 操作日志", description = "APM 设置变更的留痕")
@RestController
@RequestMapping("/ms/monitor/apm/oper-log")
@RequiredArgsConstructor
public class ApmOperLogController {

    private final ApmOperLogService operLogService;

    /**
     * 分页查 APM 操作日志。
     *
     * @param action 操作码过滤，空则全部
     * @param from   起始时间（{@code 2026-11-10T05:00:00} 或 {@code 2026-11-10 05:00:00}），空则不限
     * @param to     截止时间（同上），空则不限
     * @param page   页码，从 1 开始
     * @param size   每页条数，服务端封顶 200
     * @return 分页结果
     */
    @Operation(summary = "APM 操作日志分页", description = "最新在前")
    @GetMapping
    public ReturnResult<Page<ApmOperLogVO>> page(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LocalDateTime fromTime;
        LocalDateTime toTime;
        try {
            fromTime = OperLogTimeParser.parse(from);
            toTime = OperLogTimeParser.parse(to);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
        return operLogService.page(action, fromTime, toTime, page, size);
    }
}
