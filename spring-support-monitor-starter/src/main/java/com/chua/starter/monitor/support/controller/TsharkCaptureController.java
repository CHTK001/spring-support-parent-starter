package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.service.impl.TsharkCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 网络抓包接口
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "网络抓包", description = "网卡抓包会话/数据包/统计")
@RestController
@RequestMapping("/ms/monitor/tshark")
public class TsharkCaptureController {

    private final TsharkCaptureService tsharkCaptureService; // tsharkcapture服务

    /**
     * tsharkcapture控制器。
     * @param tsharkCaptureService tsharkcapture服务
     */
    public TsharkCaptureController(TsharkCaptureService tsharkCaptureService) {
        this.tsharkCaptureService = tsharkCaptureService;
    }

    @Operation(summary = "检查 tshark 可用性")
    @GetMapping("/check")
    /**
     * 检查。
     * @return 检查的结果
     */
    public ReturnResult<Boolean> check() {
        return this.tsharkCaptureService.checkTshark();
    }

    @Operation(summary = "列出可用网卡")
    @GetMapping("/interfaces")
    /**
     * 接口。
     * @return 接口的结果
     */
    public ReturnResult<List<String>> interfaces() {
        return this.tsharkCaptureService.listInterfaces();
    }

    @Operation(summary = "启动抓包会话")
    @PostMapping("/capture")
    /**
     * 启动。
     * @param body 主体
     * @return 启动的结果
     */
    public ReturnResult<MsTsharkSession> start(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        String iface = str(body.get("captureInterface"));
        String filter = str(body.get("captureFilter"));
        Integer count = intOf(body.get("count"));
        Integer timeout = intOf(body.get("timeout"));
        return this.tsharkCaptureService.startCapture(name, iface, filter, count, timeout);
    }

    @Operation(summary = "停止抓包会话")
    @PostMapping("/capture/{id}/stop")
    /**
     * 停止。
     * @param id 标识
     * @return 停止的结果
     */
    public ReturnResult<Void> stop(@PathVariable Long id) {
        return this.tsharkCaptureService.stopCapture(id);
    }

    @Operation(summary = "删除抓包会话(含数据包)")
    @DeleteMapping("/sessions/{id}")
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return this.tsharkCaptureService.deleteSession(id);
    }

    @Operation(summary = "抓包会话分页列表")
    @GetMapping("/sessions")
    /**
     * 会话。
     * @param "1" "1"
     * @return 会话的结果
     */
    public ReturnResult<IPage<MsTsharkSession>> sessions(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return this.tsharkCaptureService.pageSessions(page, size);
    }

    @Operation(summary = "数据包分页列表")
    @GetMapping("/sessions/{id}/packets")
    public ReturnResult<IPage<MsTsharkPacket>> packets(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return this.tsharkCaptureService.pagePackets(id, page, size);
    }

    @Operation(summary = "数据包详情")
    @GetMapping("/packets/{id}")
    /**
     * 数据包。
     * @param id 标识
     * @return 数据包的结果
     */
    public ReturnResult<MsTsharkPacket> packet(@PathVariable Long id) {
        return this.tsharkCaptureService.getPacket(id);
    }

    @Operation(summary = "协议分布统计")
    @GetMapping("/sessions/{id}/stats/protocol")
    /**
     * 协议stats。
     * @param id 标识
     * @return 协议stats的结果
     */
    public ReturnResult<List<Map<String, Object>>> protocolStats(@PathVariable Long id) {
        return this.tsharkCaptureService.protocolStats(id);
    }

    @Operation(summary = "会话 Top 流统计")
    @GetMapping("/sessions/{id}/stats/top")
    public ReturnResult<List<Map<String, Object>>> topFlows(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "10") int limit) {
        return this.tsharkCaptureService.topFlows(id, limit);
    }

    /**
     * str。
     * @param o o
     * @return str的结果
     */
    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * int的。
     * @param o o
     * @return int的的结果
     */
    private Integer intOf(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
