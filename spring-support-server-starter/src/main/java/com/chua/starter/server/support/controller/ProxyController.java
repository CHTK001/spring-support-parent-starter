package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsProxy;
import com.chua.starter.server.support.service.ProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
* 免费代理（业务——代理表 ms_代理）：
* 获取（抓取→清空→录入——较慢）· 列表（读表——快）· 当前代理（自动——无需业务指定）
* @author CH
* @since 4.0.0
*/
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService; // 代理服务

    /**
     * 获取免费代理（抓取→清空→录入——较慢——前端加载等待动画）
     *
     * @return free的结果
     */
    @GetMapping("/free")
    public ReturnResult<List<MsProxy>> free() {
        return ReturnResult.ok(proxyService.fetchFreeProxies());
    }

    /**
     * 免费代理列表（从数据表读取——快）
     *
     * @return 列表的结果
     */
    @GetMapping("/list")
    public ReturnResult<List<MsProxy>> list() {
        return ReturnResult.ok(proxyService.listProxies());
    }

    /**
     * 当前代理（自动——表内第一个——业务无需手动指定）
     *
     * @return 当前的结果
     */
    @GetMapping("/current")
    public ReturnResult<Map<String, String>> current() {
        return ReturnResult.ok(Map.of("proxy",
                proxyService.currentProxy() == null ? "" : proxyService.currentProxy()));
    }

    /**
     * 批量检测代理（全部探测收敛后返回）
     *
     * @return 结果
     */
    @GetMapping("/check")
    public ReturnResult<String> check() {
        return ReturnResult.ok("批量检测完成，共检测 " + proxyService.checkProxies() + " 条");
    }

    /**
     * 检测单个代理
     *
     * @param id 代理ID
     * @return 结果
     */
    @GetMapping("/check/{id}")
    public ReturnResult<String> checkOne(@PathVariable Long id) {
        if (!proxyService.checkProxy(id)) {
            return ReturnResult.error("代理不存在: " + id);
        }
        return ReturnResult.ok("检测完成");
    }
}