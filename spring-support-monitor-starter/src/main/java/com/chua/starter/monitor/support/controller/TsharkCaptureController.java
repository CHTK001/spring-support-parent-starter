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
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "网络抓包", description = "网卡抓包会话/数据包/统计")
@RestController
@RequestMapping("/ms/monitor/tshark")
public class TsharkCaptureController {

    private final TsharkCaptureService tsharkCaptureService; // tshark抓包服务

    /**
     * 构造器，注入 tshark 抓包服务
     *
     * @param tsharkCaptureService tshark 抓包服务
     */
    public TsharkCaptureController(TsharkCaptureService tsharkCaptureService) {
        this.tsharkCaptureService = tsharkCaptureService;
    }

    /**
     * 检查 tshark 可用性
     *
     * @return tshark 是否可用
     */
    @Operation(summary = "检查 tshark 可用性")
    @GetMapping("/check")
    public ReturnResult<Boolean> check() {
        return this.tsharkCaptureService.checkTshark();
    }

    /**
     * 列出可用网卡
     *
     * @return 可用网卡名列表
     */
    @Operation(summary = "列出可用网卡")
    @GetMapping("/interfaces")
    public ReturnResult<List<String>> interfaces() {
        return this.tsharkCaptureService.listInterfaces();
    }

    /**
     * 启动抓包会话
     *
     * @param body 抓包请求（name/captureInterface/captureFilter/count/timeout）
     * @return 新建的抓包会话
     */
    @Operation(summary = "启动抓包会话")
    @PostMapping("/capture")
    public ReturnResult<MsTsharkSession> start(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        String iface = str(body.get("captureInterface"));
        String filter = str(body.get("captureFilter"));
        Integer count = intOf(body.get("count"));
        Integer timeout = intOf(body.get("timeout"));
        return this.tsharkCaptureService.startCapture(name, iface, filter, count, timeout);
    }

    /**
     * 停止抓包会话
     *
     * @param id 会话 ID
     * @return 操作结果
     */
    @Operation(summary = "停止抓包会话")
    @PostMapping("/capture/{id}/stop")
    public ReturnResult<Void> stop(@PathVariable Long id) {
        return this.tsharkCaptureService.stopCapture(id);
    }

    /**
     * 删除抓包会话（含数据包）
     *
     * @param id 会话 ID
     * @return 操作结果
     */
    @Operation(summary = "删除抓包会话(含数据包)")
    @DeleteMapping("/sessions/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return this.tsharkCaptureService.deleteSession(id);
    }

    /**
     * 抓包会话分页列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @Operation(summary = "抓包会话分页列表")
    @GetMapping("/sessions")
    public ReturnResult<IPage<MsTsharkSession>> sessions(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return this.tsharkCaptureService.pageSessions(page, size);
    }

    /**
     * 数据包分页列表
     *
     * @param id 会话 ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @Operation(summary = "数据包分页列表")
    @GetMapping("/sessions/{id}/packets")
    public ReturnResult<IPage<MsTsharkPacket>> packets(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return this.tsharkCaptureService.pagePackets(id, page, size);
    }

    /**
     * 数据包详情
     *
     * @param id 数据包 ID
     * @return 数据包详情
     */
    @Operation(summary = "数据包详情")
    @GetMapping("/packets/{id}")
    public ReturnResult<MsTsharkPacket> packet(@PathVariable Long id) {
        return this.tsharkCaptureService.getPacket(id);
    }

    /**
     * 协议分布统计
     *
     * @param id 会话 ID
     * @return 协议分布列表
     */
    @Operation(summary = "协议分布统计")
    @GetMapping("/sessions/{id}/stats/protocol")
    public ReturnResult<List<Map<String, Object>>> protocolStats(@PathVariable Long id) {
        return this.tsharkCaptureService.protocolStats(id);
    }

    /**
     * 会话 Top 流统计
     *
     * @param id 会话 ID
     * @param limit 返回条数
     * @return Top 流列表
     */
    @Operation(summary = "会话 Top 流统计")
    @GetMapping("/sessions/{id}/stats/top")
    public ReturnResult<List<Map<String, Object>>> topFlows(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "10") int limit) {
        return this.tsharkCaptureService.topFlows(id, limit);
    }

    /**
     * 取字符串值（null 安全）
     *
     * @param o 原始值
     * @return 字符串形式
     */
    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 取整数值（null 安全，非法格式返回 null）
     *
     * @param o 原始值
     * @return 整数形式
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
