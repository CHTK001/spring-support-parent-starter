package com.chua.starter.monitor.support.node;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线节点接口。
 *
 * <p>「节点管理 → 在线节点」页面的数据源：report 客户端通过 sync 服务端注册后，
 * 在此以节点形式呈现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "监控-在线节点", description = "report 客户端注册的在线节点")
@RestController
@RequestMapping("/ms/monitor/node")
@RequiredArgsConstructor
public class MonitorNodeController {

    private final MonitorNodeRegistry registry;

    /**
     * 全部已注册节点（含离线）。
     *
     * @return 节点列表
     */
    @Operation(summary = "节点列表（含离线）")
    @GetMapping("/list")
    public ReturnResult<List<MonitorNodeVO>> list() {
        return ReturnResult.list(registry.list());
    }

    /**
     * 在线节点。
     *
     * @return 在线节点列表
     */
    @Operation(summary = "在线节点")
    @GetMapping("/online")
    public ReturnResult<List<MonitorNodeVO>> online() {
        return ReturnResult.list(registry.online());
    }

    /**
     * 节点统计。
     *
     * @return 统计结果
     */
    @Operation(summary = "节点统计")
    @GetMapping("/stats")
    public ReturnResult<MonitorNodeStatsVO> stats() {
        return ReturnResult.ok(registry.stats());
    }
}
