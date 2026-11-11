package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.HostSyncSummaryVO;

import java.util.List;

/**
 * 服务器主机服务
 * <p>
 * 提供主机台账 CRUD、连通性测试、身份绑定管理，
 * 以及供远控/SFTP/批量执行共用的凭据解析能力。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ServerHostService {

    /**
     * 新增主机（自动生成探针标识 Agent键）
     *
     * @param host 主机
     * @return 结果
     */
    ReturnResult<MsServerHost> create(MsServerHost host);

    /**
     * 更新主机
     *
     * @param id   主键
     * @param host 主机
     * @return 结果
     */
    ReturnResult<MsServerHost> update(Long id, MsServerHost host);

    /**
     * 删除主机（同时解除全部身份绑定）
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 查询单个主机
     *
     * @param id 主键
     * @return 结果
     */
    ReturnResult<MsServerHost> get(Long id);

    /**
     * 条件查询主机列表
     *
     * @param keyword 关键词，匹配名称/编码/地址，可为空
     * @param groupId 分组 标识，可为空表示不过滤
     * @param status  启用状态 1/0，可为空表示不过滤
     * @return 主机列表
     */
    ReturnResult<List<MsServerHost>> query(String keyword, Long groupId, Integer status);

    /**
     * 启用/停用主机
     *
     * @param id      主键
     * @param enabled 是否启用
     * @return 结果
     */
    ReturnResult<Void> updateStatus(Long id, boolean enabled);

    /**
     * 校验主机当前是否允许发起远控
     *
     * <p>「禁用主机」是平台侧的停用开关：禁用后不再采集、不再做在线检测，
     * 也就不应再放行远程终端（SSH / VNC / RDP / Telnet / guacd）。
     * 「停用远控入口」（{@code serverHostRemoteEnabled=0}）同样在这里拦住。
     * 两者都只是开关，不会改动主机本身的连接配置，重新启用即恢复。</p>
     *
     * @param hostId 主机 标识
     * @return 允许时返回成功；否则 message 说明被拦原因
     */
    ReturnResult<Void> checkRemoteAccess(Long hostId);

    /**
     * 连通性测试：按已绑定身份尝试 SSH 登录并回显内核信息
     *
     * @param id 主键
     * @return 测试结果（message 含 uname 输出或失败原因）
     */
    ReturnResult<String> testConnect(Long id);

    /**
     * 绑定身份到主机
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @param sort       优先级，越小越优先；为空默认取当前最大值加一
     * @return 结果
     */
    ReturnResult<Void> bindIdentity(Long hostId, Long identityId, Integer sort);

    /**
     * 解除主机上的身份绑定
     *
     * @param hostId     主机 标识
     * @param identityId 身份 标识
     * @return 结果
     */
    ReturnResult<Void> unbindIdentity(Long hostId, Long identityId);

    /**
     * 查询主机已绑定的身份 标识 列表（按优先级升序）
     *
     * @param hostId 主机 标识
     * @return 身份 标识 列表
     */
    ReturnResult<List<Long>> listBoundIdentityIds(Long hostId);

    /**
     * 解析主机登录凭据
     * <p>
     * 按 identityid 精确匹配绑定关系；未指定时按优先级取第一个绑定身份。
     * 返回对象含解密后的明文凭据，仅限运行时使用，禁止落库或输出日志。
     * </p>
     *
     * @param hostId     主机 标识
     * @param identityId 指定身份 标识，可为空
     * @return 凭据结果，data 为解密后的凭据
     */
    default ReturnResult<SshCredential> resolveCredential(Long hostId, Long identityId) {
        return resolveCredential(hostId, identityId, null);
    }

    /**
     * 解析主机登录凭据（按调用方实际请求的协议取凭据）
     * <p>
     * VNC 桌面口令与 SSH/RDP 登录口令不同源：仅当请求协议为 VNC 且主机配了 VNC 口令时
     * 才回退 VNC 口令；请求 SSH/RDP 时一律使用绑定登录身份的口令/私钥，避免主机当前协议
     * 配成 VNC 后，SSH 直连误拿 VNC 口令导致认证失败。protocol 为空时回退按主机当前远控协议判断。
     * </p>
     *
     * @param hostId     主机 标识
     * @param identityId 指定身份 标识，可为空
     * @param protocol   调用方实际协议（SSH/RDP/VNC），可为空
     * @return 凭据结果，data 为解密后的凭据
     */
    ReturnResult<SshCredential> resolveCredential(Long hostId, Long identityId, String protocol);

    /**
     * 硬件探测：SSH 连上目标主机，自动采集 CPU/内存/磁盘/系统版本/IP 并回写
     *
     * @param hostId 主机 标识
     * @return 更新后的主机（含硬件信息）
     */
    ReturnResult<MsServerHost> detectHardware(Long hostId);

    /**
     * 确保 Windows 主机已安装并启用 OpenSSH Server（sshd）。
     * <p>
     * 已可经 22 连通时做幂等收敛（开机自启、运行中、防火墙放行）；未连通时经 WinRM
     * 一次性引导安装（新版 Add-WindowsCapability / choco / Win32-OpenSSH 兜底），完成后
     * 把主机登录端口回写为 22。除 RDP 外的 Windows 管理操作之后全部走 OpenSSH。
     * </p>
     *
     * @param hostId 主机标识
     * @return 收敛结果，data 为说明信息
     */
    ReturnResult<String> ensureOpenSsh(Long hostId);

    /**
     * 刷新公网 IP：通过 ifconfig.me/ipinfo.io 探测目标主机出口公网 IP 并回写
     *
     * @param hostId 主机 标识
     * @return 刷新后的公网 IP
     */
    ReturnResult<String> refreshPublicIp(Long hostId);

    /**
     * 端口扫描：依赖本机 nmap 对目标主机做 TCP 连接扫描，解析开放端口并回写
     * 主机资产表的 {@code serverHostPorts}（JSON 数组）与扫描时间。
     *
     * <p>只扫常用端口集，同步接口不接受 1~65535 全端口范围。</p>
     *
     * @param hostId 主机 标识
     * @return 更新后的主机（含端口扫描结果）
     */
    ReturnResult<MsServerHost> scanPorts(Long hostId);

    /**
     * 新增主机时自动创建登录身份并绑定
     * <p>
     * 优先使用主机上直接携带的账号密码 / 私钥（pem），
     * 未携带时回退为第一顺位已绑定身份（保证可测连）。
     * </p>
     *
     * @param host 主机（含 服务端主机账户/密码/私募键 瞬态字段）
     * @return 处理后的主机
     */
    MsServerHost autoCreateIdentity(MsServerHost host);

    /**
     * 批量同步主机资产：对指定/全部启用主机进行 SSH 硬件探测（含公网/内网 IP），
     * 将探测到的不变信息（主机信息、硬件规格、IP）同步回资产表；
     * 若目标公网/内网 IP 与库中不一致，仅在该场景触发更新。
     * 使用虚拟线程并发执行，避免阻塞请求线程。
     *
     * @param hostIds 目标主机 ID 列表；为空则同步全部启用主机
     * @return 同步结果概要（成功数 / 跳过数 / 失败数 / 变更明细）
     */
    ReturnResult<HostSyncSummaryVO> syncHosts(List<Long> hostIds);

    /**
     * Prometheus 凭据校验：GET 目标主机 {@code http://{address}:{port}/metrics}，
     * 验证 node_exporter 等指标端点是否可达并返回 200。
     *
     * @param id 主机主键
     * @return 校验结果（成功时回显端点地址与指标行数）
     */
    ReturnResult<String> testPrometheus(Long id);

    /**
     * 安装 nginx（仅支持 SSH 开启的 Linux/macOS 主机）。
     * <p>
     * 通过 SSH 登录目标主机，按发行版包管理器安装 nginx 并做版本自检。
     * 目前仅支持 nginx；其它软件暂不开放。Windows 主机（无 OpenSSH）与
     * SSH 关闭的主机直接拒绝，避免误报「已安装」。
     * </p>
     *
     * @param id 主机主键
     * @return 安装结果（成功时回显 nginx -v 输出）
     */
    ReturnResult<String> installNginx(Long id);
}
