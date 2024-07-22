package com.chua.starter.monitor.server.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorProxy;
import com.chua.starter.monitor.server.service.MonitorProxyService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代理
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/protocol")
@Tag(name = "代理")
@RequiredArgsConstructor
public class MonitorProxyController extends AbstractSwaggerController<MonitorProxyService, MonitorProxy> {

    private final MonitorProxyService monitorProxyService;


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
        MonitorProxy monitorProxy = monitorProxyService.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已开启，则返回错误信息
        if(null != monitorProxy.getProxyStatus() && 1 == monitorProxy.getProxyStatus()) {
            return ReturnResult.error("代理已开启");
        }
        // 开启监控代理，并返回操作结果
        return monitorProxyService.start(monitorProxy);
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
        MonitorProxy monitorProxy = monitorProxyService.getById(id);
        // 检查监控代理实例是否存在
        if(null == monitorProxy) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }

        // 检查代理状态，若已停止，则返回错误信息
        if(null != monitorProxy.getProxyStatus() && 0 == monitorProxy.getProxyStatus()) {
            return ReturnResult.error("代理已停止");
        }
        // 停止监控代理，并返回操作结果
        return monitorProxyService.stop(monitorProxy);
    }

    @Override
    public MonitorProxyService getService() {
        return monitorProxyService;
    }
}
