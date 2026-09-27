package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.IPv4ConfigVO;
import com.chua.starter.monitor.support.model.NetworkAdapterDetailVO;
import com.chua.starter.monitor.support.service.HostNetworkService;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主机网络（网卡）接口：网卡状态/属性、IPv4 读取与下发。
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "主机网卡")
@RequestMapping(ServerConstants.API_PREFIX + "/host/network")
public class HostNetworkController {

    /**
     * 主机网络服务
     */
    private final HostNetworkService networkService;

    /**
     * 网卡详情（状态/属性）。
     *
     * @param hostId 主机 ID
     * @param name   网卡名称 / 描述 / 索引
     * @return 网卡详情
     */
    @GetMapping("/{hostId}/adapter")
    @Operation(summary = "网卡详情")
    public ReturnResult<NetworkAdapterDetailVO> adapter(@PathVariable Long hostId,
                                                        @RequestParam("name") String name) {
        return networkService.adapterDetail(hostId, name);
    }

    /**
     * 列出主机全部可见网卡。
     *
     * @param hostId 主机 ID
     * @return 网卡列表
     */
    @GetMapping("/{hostId}/adapters")
    @Operation(summary = "网卡列表")
    public ReturnResult<List<NetworkAdapterDetailVO>> adapters(@PathVariable Long hostId) {
        return networkService.adapters(hostId);
    }

    /**
     * 读取 IPv4 配置。
     *
     * @param hostId 主机 ID
     * @param name   网卡名称
     * @return IPv4 配置
     */
    @GetMapping("/{hostId}/ipv4")
    @Operation(summary = "读取 IPv4 配置")
    public ReturnResult<IPv4ConfigVO> ipv4(@PathVariable Long hostId,
                                           @RequestParam("name") String name) {
        return networkService.ipv4(hostId, name);
    }

    /**
     * 下发 IPv4 配置（DHCP / 静态地址、网关、DNS）。
     *
     * @param hostId 主机 ID
     * @param config IPv4 配置
     * @return 结果
     */
    @PutMapping("/{hostId}/ipv4")
    @Operation(summary = "下发 IPv4 配置")
    public ReturnResult<Void> applyIpv4(@PathVariable Long hostId,
                                        @RequestBody IPv4ConfigVO config) {
        return networkService.applyIpv4(hostId, config);
    }
}
