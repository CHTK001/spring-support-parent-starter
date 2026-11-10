package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.operlog.OperLogTimeParser;
import com.chua.starter.monitor.support.server.model.ServerOperLogVO;
import com.chua.starter.monitor.support.service.ServerOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 服务管理节点操作日志查询。
 *
 * <p>写入侧不在这里：本控制器只读。日志由 {@code @SysLog} 切面 → 监听器写入，
 * 这样新增受管端点时只要加上注解就会自动留痕，不必记得再改一处埋点。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Tag(name = "监控-服务管理 - 操作日志", description = "服务器节点的创建/更新/启停/删除留痕")
@RestController
@RequestMapping("/ms/monitor/server/oper-log")
@RequiredArgsConstructor
public class ServerOperLogController {

    private final ServerOperLogService operLogService;

    /**
     * 按节点分页查操作日志。
     *
     * @param id     节点 ID
     * @param action 操作码过滤，空则全部
     * @param from   起始时间（{@code 2026-11-10T05:00:00} 或 {@code 2026-11-10 05:00:00}），空则不限
     * @param to     截止时间（同上），空则不限
     * @param page   页码，从 1 开始
     * @param size   每页条数，服务端封顶 200
     * @return 分页结果
     */
    @Operation(summary = "服务节点操作日志分页", description = "最新在前；按节点 ID 必填过滤")
    @GetMapping("/{id}")
    public ReturnResult<Page<ServerOperLogVO>> page(
            @PathVariable Long id,
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
        return operLogService.page(id, action, fromTime, toTime, page, size);
    }
}
