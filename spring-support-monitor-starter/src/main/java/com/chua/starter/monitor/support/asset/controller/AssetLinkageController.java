package com.chua.starter.monitor.support.asset.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.asset.model.AssetDetailView;
import com.chua.starter.monitor.support.asset.service.AssetLinkageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资产联动控制器 —— 主机台账与服务节点的聚合查询。
 *
 * <p>给监控台提供「资产为中心」的统一视角：按资产查看其服务节点、通道状态，
 * 或按关键字检索资产。统一路由前缀 {@code /ms/monitor/asset}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "监控-资产联动", description = "主机台账与服务节点/通道的聚合查询")
@RestController
@RequestMapping("/ms/monitor/asset")
@RequiredArgsConstructor
public class AssetLinkageController {

    /** 资产联动服务 */
    private final AssetLinkageService assetLinkageService;

    /**
    * 资产聚合详情：基础信息 + 在线/通道状态 + 服务节点清单。
    *
    * @param hostId 主机 标识
    * @return 资产聚合视图
    */
    @Operation(summary = "资产聚合详情")
    @GetMapping("/{hostId}")
    public ReturnResult<AssetDetailView> assetDetail(@PathVariable Long hostId) {
        return assetLinkageService.assetDetail(hostId);
    }

    /**
     * 按关键字检索资产（名称/IP 模糊），用于监控台搜索与导航。
     *
     * @param keyword 关键字（可空，空则返回全部）
     * @return 资产简表列表
     * @param false false
     */
    @Operation(summary = "资产检索")
    @GetMapping("/search")
    public ReturnResult<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword) {
        return assetLinkageService.searchAssets(keyword);
    }
}
