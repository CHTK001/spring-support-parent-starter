package com.chua.starter.monitor.server.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.entity.MonitorTerminalBase;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 终端
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/terminal")
@Tag(name = "代理")
@RequiredArgsConstructor
@Getter
public class MonitorTerminalController extends AbstractSwaggerController<MonitorTerminalService, MonitorTerminal> {

    private final MonitorTerminalService service;

    /**
     * 查询基本信息。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询公网ip")
    @GetMapping("ifconfig")
    public ReturnResult<String> ifconfig(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return ReturnResult.of(service.ifconfig(monitorProxy));
    }
    /**
     * 查询基本信息。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询基本信息")
    @GetMapping("base")
    public ReturnResult<List<MonitorTerminalBase>> base(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return ReturnResult.of(service.base(monitorProxy));
    }
    /**
     * 查询基本信息。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "更新基本信息")
    @PutMapping("base")
    public ReturnResult<List<MonitorTerminalBase>> baseUpgrade(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return ReturnResult.of(service.baseUpgrade(monitorProxy));
    }
    /**
     * 刷新指标。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "刷新指标")
    @GetMapping("indicator")
    public ReturnResult<Boolean> indicator(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return ReturnResult.of(service.indicator(monitorProxy));
    }

    /**
     * 开始监控代理。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "开始")
    @GetMapping("start")
    public ReturnResult<Boolean> start(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已开启，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 1 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已开启");
        }
        // 开启监控代理，并返回操作结果
        return service.start(monitorProxy);
    }

    /**
     * 停止监控代理。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "停止")
    @GetMapping("stop")
    public ReturnResult<Boolean> stop(String id) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = service.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 停止监控代理，并返回操作结果
        return service.stop(monitorProxy);
    }

}
