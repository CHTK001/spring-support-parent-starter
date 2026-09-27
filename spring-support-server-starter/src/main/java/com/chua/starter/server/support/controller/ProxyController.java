package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsProxy;
import com.chua.starter.server.support.model.vo.ProxyCheckSummaryVO;
import com.chua.starter.server.support.model.vo.ProxyCurrentVO;
import com.chua.starter.server.support.service.ProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
* 免费代理（业务——代理表 ms_proxy）：
* 获取（抓取→清空→录入——较慢）· 列表（读表——快）· 当前代理（自动——无需业务指定）·
* 台账维护（新增/编辑/删除/清空）· 检测（单条与批量）
* @author CH
* @since 4.0.0
*/
@Tag(name = "资产管理-代理", description = "免费代理台账与探测")
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
    @Operation(summary = "获取免费代理")
    @GetMapping("/free")
    public ReturnResult<List<MsProxy>> free() {
        return ReturnResult.ok(proxyService.fetchFreeProxies());
    }

    /**
     * 代理列表（从数据表读取——快）
     *
     * @param protocol 协议精确过滤，可为空
     * @param status   探测状态过滤（reachable/unreachable/unchecked），可为空
     * @param keyword  代理主机关键词，可为空
     * @param limit    最多返回条数，可为空
     * @return 列表的结果
     */
    @Operation(summary = "代理列表")
    @GetMapping("/list")
    public ReturnResult<List<MsProxy>> list(@RequestParam(required = false) String protocol,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer limit) {
        return ReturnResult.ok(proxyService.listProxies(protocol, status, keyword, limit));
    }

    /**
     * 查询单个代理
     *
     * @param id 代理主键
     * @return 结果
     */
    @Operation(summary = "查询单个代理")
    @GetMapping("/{id}")
    public ReturnResult<MsProxy> get(@PathVariable Long id) {
        return proxyService.get(id);
    }

    /**
     * 新增代理
     *
     * @param proxy 代理
     * @return 结果
     */
    @Operation(summary = "新增代理")
    @PostMapping
    public ReturnResult<MsProxy> create(@RequestBody MsProxy proxy) {
        return proxyService.create(proxy);
    }

    /**
     * 编辑代理（探测结论随之重置为未检测）
     *
     * @param id    代理主键
     * @param proxy 代理
     * @return 结果
     */
    @Operation(summary = "编辑代理")
    @PutMapping("/{id}")
    public ReturnResult<MsProxy> update(@PathVariable Long id, @RequestBody MsProxy proxy) {
        return proxyService.update(id, proxy);
    }

    /**
     * 删除代理
     *
     * @param id 代理主键
     * @return 结果
     */
    @Operation(summary = "删除代理")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return proxyService.delete(id);
    }

    /**
     * 清空代理表
     *
     * @return 删除条数
     */
    @Operation(summary = "清空代理表")
    @DeleteMapping
    public ReturnResult<Integer> clear() {
        return proxyService.clear();
    }

    /**
     * 当前代理（自动——表内第一个——业务无需手动指定）
     *
     * @return 当前的结果
     */
    @Operation(summary = "当前代理")
    @GetMapping("/current")
    public ReturnResult<ProxyCurrentVO> current() {
        String proxy = proxyService.currentProxy();
        return ReturnResult.ok(new ProxyCurrentVO(proxy == null ? "" : proxy));
    }

    /**
     * 批量检测代理（全部探测收敛后返回）
     *
     * @return 结果
     */
    @Operation(summary = "批量检测代理")
    @GetMapping("/check")
    public ReturnResult<String> check() {
        ProxyCheckSummaryVO summary = proxyService.checkProxies();
        return ReturnResult.ok(String.format("批量检测完成，共检测 %d 条，可达 %d 条",
                summary.checked(), summary.reachable()));
    }

    /**
     * 检测单个代理
     *
     * @param id 代理ID
     * @return 结果
     */
    @Operation(summary = "检测单个代理")
    @GetMapping("/check/{id}")
    public ReturnResult<String> checkOne(@PathVariable Long id) {
        if (!proxyService.checkProxy(id)) {
            return ReturnResult.error("代理不存在: " + id);
        }
        return ReturnResult.ok("检测完成");
    }
}
