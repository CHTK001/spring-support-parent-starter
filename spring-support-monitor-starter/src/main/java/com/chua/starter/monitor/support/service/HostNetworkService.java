package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.IPv4ConfigVO;
import com.chua.starter.monitor.support.model.NetworkAdapterDetailVO;

import java.util.List;

/**
 * 主机网络（网卡）管理服务：参考 Windows「网络连接(ncpa.cpl)」。
 *
 * <p>网卡状态/属性查看、IPv4（DHCP / 静态地址、网关、DNS）读取与下发，
 * 全部经当前采集通道（本机 / agent / SSH / WinRM）执行，不在 Controller 拼命令。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface HostNetworkService {

    /**
     * 读取网卡详情（状态/属性）。
     *
     * @param hostId      主机 ID
     * @param adapterName 网卡名称 / 描述 / 索引
     * @return 网卡详情
     */
    ReturnResult<NetworkAdapterDetailVO> adapterDetail(Long hostId, String adapterName);

    /**
     * 列出主机全部可见网卡（Windows 经 Get-NetAdapter，名称与系统一致）。
     *
     * @param hostId 主机 ID
     * @return 网卡列表（轻量：名称 / 状态 / MAC / IPv4 / 速率）
     */
    ReturnResult<List<NetworkAdapterDetailVO>> adapters(Long hostId);

    /**
     * 读取网卡 IPv4 配置。
     *
     * @param hostId      主机 ID
     * @param adapterName 网卡名称 / 描述 / 索引
     * @return IPv4 配置
     */
    ReturnResult<IPv4ConfigVO> ipv4(Long hostId, String adapterName);

    /**
     * 下发 IPv4 配置（可能造成该主机网络短暂中断）。
     *
     * @param hostId 主机 ID
     * @param config IPv4 配置
     * @return 结果
     */
    ReturnResult<Void> applyIpv4(Long hostId, IPv4ConfigVO config);
}
