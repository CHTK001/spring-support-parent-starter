package com.chua.starter.monitor.server.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.WIndicator;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorTerminal;
import com.chua.starter.monitor.server.pojo.IdQuery;
import com.chua.starter.monitor.server.pojo.Last;
import com.chua.starter.monitor.server.service.MonitorTerminalBaseService;
import com.chua.starter.monitor.server.service.MonitorTerminalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 终端
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/terminal/base")
@Tag(name = "终端信息")
@RequiredArgsConstructor
@Getter
public class MonitorTerminalBaseController  {


    private final MonitorTerminalBaseService service;
    private final MonitorTerminalService monitorTerminalService;
    /**
     * 开始监控代理。
     *
     * @param id 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "删除基本信息")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> baseDelete(String id) {
        return ReturnResult.of(service.removeById(id));
    }
    /**
     * 查询基本信息。
     *
     * @param idQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询的活动信息")
    @PutMapping("w")
    public ReturnResult<List<WIndicator>> w(@RequestBody IdQuery idQuery) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(idQuery.getId())) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = monitorTerminalService.getById(idQuery.getId());
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return service.w(monitorProxy);
    }
    /**
     * 查询基本信息。
     *
     * @param idQuery 监控代理的唯一标识符。
     * @return 返回操作结果，如果操作成功，返回true；否则返回false，并附带错误信息。
     */
    @Operation(summary = "查询的登录信息")
    @PutMapping("last")
    public ReturnResult<List<Last>> last(@RequestBody IdQuery idQuery) {
        // 检查ID是否为空
        if(StringUtils.isEmpty(idQuery.getId())) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        // 根据ID获取监控代理实例
        MonitorTerminal monitorProxy = monitorTerminalService.getById(idQuery.getId());
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getTerminalStatus() && 0 == monitorProxy.getTerminalStatus()) {
            return ReturnResult.error("会话已停止");
        }
        // 开启监控代理，并返回操作结果
        return service.last(monitorProxy);
    }
}
