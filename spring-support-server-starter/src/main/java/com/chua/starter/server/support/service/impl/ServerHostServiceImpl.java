package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.net.NetUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.nmap.support.NmapScanner;
import com.chua.nmap.support.RustNmapScanner;
import com.chua.nmap.support.bridge.RustNmapBridge;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.connection.WinrmShell;
import com.chua.starter.server.support.connection.WindowsSshShell;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.entity.MsServerKey;
import com.chua.starter.server.support.enums.AuthType;
import com.chua.starter.server.support.enums.ProtocolType;
import com.chua.starter.server.support.event.HostPortScanEvent;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerKeyMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.HostSyncChangeVO;
import com.chua.starter.server.support.model.vo.HostSyncSummaryVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.starter.server.support.spi.HostDetectProviderRegistry;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostRemoteProviderRegistry;
import com.chua.starter.server.support.spi.HostShellDialect;
import com.chua.starter.server.support.spi.RemoteExecResult;
import com.chua.starter.server.support.util.CredentialCipher;
import com.chua.starter.server.support.util.HostPlatforms;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务器主机服务实现
 * <p>
 * 主机台账的增删改查、连通性探测、身份绑定与凭据解析。
 * 凭据解析策略：指定身份优先；未指定时取优先级最高的绑定身份。
 * 解密失败、缺少绑定、密钥缺失均以业务错误返回，不抛裸异常。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerHostServiceImpl implements ServerHostService {

    /**
     * 在线状态：未知
    */
    private static final int ONLINE_UNKNOWN = -1;

    /**
     * 在线状态：在线
    */
    private static final int ONLINE_YES = 1;

    /**
     * 启用状态：启用
    */
    private static final int STATUS_ENABLED = 1;

    /**
     * 默认 SSH 端口
    */
    private static final int DEFAULT_SSH_PORT = 22;

    /**
     * 默认远控模式（经 guacd 中继，与历史行为一致）
    */
    private static final String DEFAULT_REMOTE_MODE = "GUACD";

    /**
     * 远控通道取值：GUACD 中转 / DIRECT 平台原生客户端直连 / NONE 不经平台通道
    */
    private static final Set<String> REMOTE_MODES = Set.of("GUACD", "DIRECT", "NONE");

    /**
     * 平台自研直连通道覆盖的协议：SSH 为 MINA SSHD PTY 桥接，VNC 为 RFB 帧桥接
    */
    private static final Set<String> DIRECT_PROTOCOLS = Set.of("SSH", "VNC");

    /**
     * JSON 序列化器
    */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 探针标识字节长度（32 个十六进制字符）
    */
    private static final int AGENT_KEY_BYTES = 16;

    /**
     * Linux 内核信息命令
    */
    private static final String CMD_UNAME = "uname -sr";

    /**
     * Windows（WinRM）连通性探测命令：回显计算机名即可确认通道可用
    */
    private static final String CMD_COMPUTERNAME = "$env:COMPUTERNAME";

    /**
     * 连通性探测超时（秒）
    */
    private static final int PROBE_TIMEOUT_SECONDS = 15;

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    /**
     * 身份 映射器
    */
    private final MsServerIdentityMapper identityMapper;

    /**
     * 密钥 映射器
    */
    private final MsServerKeyMapper keyMapper;

    /**
     * 主机身份关联 映射器
    */
    private final MsServerHostIdentityMapper hostIdentityMapper;

    /**
     * 凭据加解密器
    */
    private final CredentialCipher cipher;

    /**
     * 连接缓存管理器
    */
    private final ConnectionManager connectionManager;

    /**
     * 主机硬件探测提供者注册中心（按系统类型 SPI 路由）
    */
    private final HostDetectProviderRegistry detectProviderRegistry;

    /**
     * 远程操作提供者注册中心（按系统类型 SPI 路由，连通性测试与部署同源）
    */
    private final HostRemoteProviderRegistry remoteProviderRegistry;

    /**
     * Windows 经 OpenSSH 的执行通道
     */
    private final WindowsSshShell windowsSshShell;

    /**
     * Windows 一次性引导安装 OpenSSH 时的 WinRM 兜底通道
     */
    private final WinrmShell winrmShell;

    /**
     * Spring 事件发布器（后台扫描完成后发布事件，由监控模块转发 Socket）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 端口扫描中的主机集合，防止同一主机并发扫描
     */
    private final Set<Long> portScanning = ConcurrentHashMap.newKeySet();

    /**
     * 端口扫描后台执行器：扫描为阻塞式 native 调用，用虚拟线程承载不占用平台线程
     */
    private final ExecutorService portScanExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("port-scan-").factory());

    /**
     * 探针标识随机源
    */
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建。
     * @param host 主机
     * @return 创建的结果
     */
    public ReturnResult<MsServerHost> create(MsServerHost host) {
        // 参数校验前置
        if (host == null || isBlank(host.getServerHostName()) || isBlank(host.getServerHostAddress())) {
            return ReturnResult.error("主机名称与地址不能为空");
        }
        if (host.getServerHostPort() == null || host.getServerHostPort() <= 0) {
            host.setServerHostPort(DEFAULT_SSH_PORT);
        }
        applyOsType(host);
        applySshEnabledDefault(host);
        if (host.getServerHostStatus() == null) {
            host.setServerHostStatus(STATUS_ENABLED);
        }
        if (host.getServerHostOnline() == null) {
            host.setServerHostOnline(ONLINE_UNKNOWN);
        }
        // 主机级系统配置默认值
        if (host.getServerHostOnlineEnabled() == null) {
            host.setServerHostOnlineEnabled(1);
        }
        if (host.getServerHostOnlineInterval() == null) {
            host.setServerHostOnlineInterval(60);
        }
        if (host.getServerHostCollectEnabled() == null) {
            host.setServerHostCollectEnabled(0);
        }
        if (host.getServerHostCollectMode() == null || isBlank(host.getServerHostCollectMode())) {
            host.setServerHostCollectMode("SCHEDULED");
        }
        // 本机：先校验地址确属本机（填了非本机地址直接拒绝），再把回环/空地址归一为物理网卡 IP、远控强制 VNC
        String localError = validateLocalAddress(host);
        if (localError != null) {
            return ReturnResult.error(localError);
        }
        applyLocalDefaults(host);
        if (host.getServerHostCollectInterval() == null) {
            host.setServerHostCollectInterval(3600);
        }
        if (host.getServerHostAgentEnabled() == null) {
            host.setServerHostAgentEnabled(0);
        }
        if (host.getServerHostRemoteEnabled() == null) {
            host.setServerHostRemoteEnabled(1);
        }
        if (isBlank(host.getServerHostRemoteMode())) {
            host.setServerHostRemoteMode(DEFAULT_REMOTE_MODE);
        }
        String channelError = validateRemoteChannel(host);
        if (channelError != null) {
            return ReturnResult.error(channelError);
        }
        // VNC 口令密文落库（前端提交明文，禁止原样入库）
        if (!isBlank(host.getServerHostVncPassword())) {
            host.setServerHostVncPassword(cipher.encrypt(host.getServerHostVncPassword().trim()));
        }
        // 延迟检测默认值
        if (host.getServerHostDelayEnabled() == null) {
            host.setServerHostDelayEnabled(0);
        }
        if (host.getServerHostDelayInterval() == null) {
            host.setServerHostDelayInterval(60);
        }
        // 探针标识服务端生成，保证全局唯一
        host.setServerHostAgentKey(generateAgentKey());
        // 主机资产编码服务端生成（用户未自定义时），保证可读且全局唯一
        if (isBlank(host.getServerHostCode())) {
            host.setServerHostCode(generateHostCode());
        }
        hostMapper.insert(host);
        // 携带账号密码/PEM 时自动建身份并绑定，保证新增即可测连/远控
        /**
         * auto创建identity。
         * @param host 主机
         */
        autoCreateIdentity(host);
        return ReturnResult.ok(decryptRemoteCredential(hostMapper.selectById(host.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新。
     * @param id 标识
     * @param host 主机
     * @return 更新的结果
     */
    public ReturnResult<MsServerHost> update(Long id, MsServerHost host) {
        MsServerHost exists = hostMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        // 仅覆盖非空字段，避免前端局部提交清空已有数据
        if (!isBlank(host.getServerHostName())) {
            exists.setServerHostName(host.getServerHostName());
        }
        if (!isBlank(host.getServerHostCode())) {
            exists.setServerHostCode(host.getServerHostCode());
        }
        if (!isBlank(host.getServerHostAddress())) {
            exists.setServerHostAddress(host.getServerHostAddress());
        }
        if (host.getServerHostPort() != null && host.getServerHostPort() > 0) {
            exists.setServerHostPort(host.getServerHostPort());
        }
        if (!isBlank(host.getServerHostOsType())) {
            exists.setServerHostOsType(host.getServerHostOsType());
        }
        if (host.getServerHostOsArch() != null) {
            exists.setServerHostOsArch(host.getServerHostOsArch());
        }
        if (host.getMsServerGroupId() != null) {
            exists.setMsServerGroupId(host.getMsServerGroupId());
        }
        if (host.getServerHostTags() != null) {
            exists.setServerHostTags(host.getServerHostTags());
        }
        if (!isBlank(host.getServerHostDescription())) {
            exists.setServerHostDescription(host.getServerHostDescription());
        }
 // 主机级系统配置（开关可为 0/1，用 != 空 覆盖以便显式关闭）
        if (host.getServerHostOnlineEnabled() != null) {
            exists.setServerHostOnlineEnabled(host.getServerHostOnlineEnabled());
        }
        if (host.getServerHostOnlineInterval() != null && host.getServerHostOnlineInterval() > 0) {
            exists.setServerHostOnlineInterval(host.getServerHostOnlineInterval());
        }
        if (host.getServerHostCollectEnabled() != null) {
            exists.setServerHostCollectEnabled(host.getServerHostCollectEnabled());
        }
        if (!isBlank(host.getServerHostCollectMode())) {
            exists.setServerHostCollectMode(host.getServerHostCollectMode());
        }
        applyOsType(exists);
        // 是否开启 SSH：显式提交则覆盖，否则保留/补默认（Linux 默认开、Windows 默认关）
        if (host.getServerHostSshEnabled() != null) {
            exists.setServerHostSshEnabled(host.getServerHostSshEnabled());
        } else {
            applySshEnabledDefault(exists);
        }
        // 本机：先校验地址确属本机（改成非本机地址直接拒绝），再把回环/空地址归一为物理网卡 IP、远控强制 VNC
        String localError = validateLocalAddress(exists);
        if (localError != null) {
            return ReturnResult.error(localError);
        }
        applyLocalDefaults(exists);
        if (host.getServerHostCollectInterval() != null && host.getServerHostCollectInterval() > 0) {
            exists.setServerHostCollectInterval(host.getServerHostCollectInterval());
        }
        if (host.getServerHostAgentEnabled() != null) {
            exists.setServerHostAgentEnabled(host.getServerHostAgentEnabled());
        }
        if (host.getServerHostRemoteEnabled() != null) {
            exists.setServerHostRemoteEnabled(host.getServerHostRemoteEnabled());
        }
        // 远控配置：协议/端口留空表示按系统类型与协议默认值推断，不覆盖已有值
        if (!isBlank(host.getServerHostRemoteProtocol())) {
            exists.setServerHostRemoteProtocol(host.getServerHostRemoteProtocol().trim());
        }
        if (host.getServerHostRemotePort() != null && host.getServerHostRemotePort() > 0) {
            exists.setServerHostRemotePort(host.getServerHostRemotePort());
        }
        if (!isBlank(host.getServerHostRemoteMode())) {
            exists.setServerHostRemoteMode(host.getServerHostRemoteMode().trim());
        } else if (isBlank(exists.getServerHostRemoteMode())) {
            exists.setServerHostRemoteMode(DEFAULT_REMOTE_MODE);
        }
        // VNC 口令：仅在表单提交新值时重新加密覆盖，留空表示保留库中密文
        if (!isBlank(host.getServerHostVncPassword())) {
            exists.setServerHostVncPassword(cipher.encrypt(host.getServerHostVncPassword().trim()));
        }
        // 延迟检测配置（开关可为 0/1，间隔需大于 0）
        if (host.getServerHostDelayEnabled() != null) {
            exists.setServerHostDelayEnabled(host.getServerHostDelayEnabled());
        }
        if (host.getServerHostDelayInterval() != null && host.getServerHostDelayInterval() > 0) {
            exists.setServerHostDelayInterval(host.getServerHostDelayInterval());
        }
 // 透传前端提交的凭据瞬态字段（账号密码/PEM），供 auto创建identity 创建/更新身份
        exists.setServerHostAccount(host.getServerHostAccount());
        exists.setServerHostPassword(host.getServerHostPassword());
        exists.setServerHostPrivateKey(host.getServerHostPrivateKey());
        // 合并后再校验：局部提交可能只改协议或只改通道，单看请求体判不出矛盾组合
        String channelError = validateRemoteChannel(exists);
        if (channelError != null) {
            return ReturnResult.error(channelError);
        }
        hostMapper.updateById(exists);
        // 携带账号密码/PEM 时自动创建/更新登录身份并绑定（编辑时亦可调整凭据）
        /**
         * auto创建identity。
         * @param exists exists
         */
        autoCreateIdentity(exists);
        return ReturnResult.ok(decryptRemoteCredential(hostMapper.selectById(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除。
     * @param id 标识
     * @return 删除的结果
     */
    public ReturnResult<Void> delete(Long id) {
        // 先解除身份绑定再删除主机，避免遗留孤儿关联
        hostIdentityMapper.delete(new LambdaQueryWrapper<MsServerHostIdentity>()
                .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, id));
        hostMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<MsServerHost> get(Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        // 详情接口供编辑弹窗回显，VNC 口令解密后返回（失败降级为空不阻断）
        return ReturnResult.ok(decryptRemoteCredential(host));
    }

    @Override
    public ReturnResult<List<MsServerHost>> query(String keyword, Long groupId, Integer status) {
        var wrapper = new LambdaQueryWrapper<MsServerHost>();
        if (!isBlank(keyword)) {
            String like = "%" + keyword + "%";
            wrapper.and(w -> w.like(MsServerHost::getServerHostName, like)
                    .or().like(MsServerHost::getServerHostCode, like)
                    .or().like(MsServerHost::getServerHostAddress, like));
        }
        if (groupId != null) {
            wrapper.eq(MsServerHost::getMsServerGroupId, groupId);
        }
        if (status != null) {
            wrapper.eq(MsServerHost::getServerHostStatus, status);
        }
        wrapper.orderByDesc(MsServerHost::getId);
        List<MsServerHost> hosts = hostMapper.selectList(wrapper);
        // 列表接口不下发任何凭据（含 VNC 口令密文）
        hosts.forEach(this::mask);
        return ReturnResult.ok(hosts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新状态。
     * @param id 标识
     * @param enabled 启用
     * @return 更新状态的结果
     */
    public ReturnResult<Void> updateStatus(Long id, boolean enabled) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        host.setServerHostStatus(enabled ? STATUS_ENABLED : 0);
        hostMapper.updateById(host);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<String> testConnect(Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        // 采集通道专属实现（如 Agent）优先，其余按平台路由；通道实现无需 SSH 身份
        HostDetectProvider detector = detectProviderRegistry.resolve(host).orElse(null);
        if (detector != null && !detector.requiresCredential()) {
            ReturnResult<String> tested = detector.test(host);
            markOnline(id, tested.isOk());
            return tested;
        }
        List<Long> bound = listBoundIdentityIds(id).getData();
        if (bound == null || bound.isEmpty()) {
            // 无绑定身份时仍走一次解析：纯 VNC 主机的回落凭据在这里给出
            return ReturnResult.error(resolveCredential(id, null).getMsg());
        }
        // 采集侧（SshConnectionCache）按全部凭据逐组重试，这里同口径：一组口令错不该挡住后面的可用身份
        String lastError = null;
        for (Long identityId : bound) {
            ReturnResult<SshCredential> resolved = resolveCredential(id, identityId);
            if (!resolved.isOk()) {
                lastError = resolved.getMsg();
                continue;
            }
            ReturnResult<String> attempt = probeCredential(host, resolved.getData());
            if (attempt.isOk()) {
                return attempt;
            }
            lastError = attempt.getMsg();
            log.info("[server] 身份 {} 未能登录主机 {}: {}", identityId, id, lastError);
        }
        markOnline(id, false);
        return ReturnResult.error((lastError == null ? "连接失败" : lastError)
                + "（已尝试 " + bound.size() + " 组绑定身份）");
    }

    /**
     * 用一组凭据探测主机连通性。
     *
     * @param host       主机
     * @param credential 凭据
     * @return 连通结果，失败时 msg 为原因
     */
    private ReturnResult<String> probeCredential(MsServerHost host, SshCredential credential) {
        Long id = host.getId();
        // Windows 主机没有 SSH 服务，必须按 SPI 走 WinRM，否则会误报「SSH 连接失败」并错标离线
        HostRemoteProvider provider = remoteProviderRegistry.resolve(host).orElse(null);
        if (provider != null && provider.shellDialect() == HostShellDialect.POWERSHELL) {
            return testRemoteExec(host, provider, credential);
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            // 登录+认证成功即为连通；exec 通道不可用时不影响结论，banner 仅尽力获取
            String banner = "connected";
            if ("LINUX".equalsIgnoreCase(host.getServerHostOsType())) {
                try {
                    String uname = handle.client().exec().command(CMD_UNAME).executeAndGetOutput();
                    if (!isBlank(uname)) {
                        banner = uname;
                    }
                } catch (Exception execError) {
                    // 打印完整堆栈便于定位 exec 通道问题（不影响连通性结论）
                    log.info("[server] exec 通道不可用(不影响连通性结论)", execError);
                }
            }
            markOnline(host.getId(), true);
            return ReturnResult.ok(banner, "测试成功");
        } catch (Exception e) {
            markOnline(host.getId(), false);
            log.warn("[server] 连通性测试失败 hostId={} address={}: {}", id, host.getServerHostAddress(), e.getMessage(), e);
            return ReturnResult.error("连接失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(id);
            }
        }
    }

    /**
     * 按远程操作 Provider 判定连通性（Windows 等无 SSH 服务的主机走此路径）
     *
     * @param host       主机
     * @param provider   远程操作实现
     * @param credential 凭据
     * @return 连通结果
     */
    private ReturnResult<String> testRemoteExec(MsServerHost host, HostRemoteProvider provider, SshCredential credential) {
        try {
            RemoteExecResult result = provider.exec(host, credential, CMD_COMPUTERNAME, PROBE_TIMEOUT_SECONDS);
            if (!result.success()) {
                markOnline(host.getId(), false);
                return ReturnResult.error("连接失败: " + result.failureMessage());
            }
            String banner = isBlank(result.stdout()) ? "connected" : result.stdout().trim();
            markOnline(host.getId(), true);
            return ReturnResult.ok(banner, "测试成功");
        } catch (Exception e) {
            markOnline(host.getId(), false);
            log.warn("[server] 连通性测试失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage(), e);
            return ReturnResult.error("连接失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * bindidentity。
     * @param hostId 主机标识
     * @param identityId identityid
     * @param sort 排序
     * @return bindIdentity的结果
     */
    public ReturnResult<Void> bindIdentity(Long hostId, Long identityId, Integer sort) {
        if (hostId == null || identityId == null) {
            return ReturnResult.error("主机ID与身份ID不能为空");
        }
        if (hostMapper.selectById(hostId) == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (identityMapper.selectById(identityId) == null) {
            return ReturnResult.error("身份不存在: " + identityId);
        }
        Long bound = hostIdentityMapper.selectCount(new LambdaQueryWrapper<MsServerHostIdentity>()
                .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                .eq(MsServerHostIdentity::getMsServerHostIdentityIdentityId, identityId));
        if (bound != null && bound > 0) {
            return ReturnResult.error("该身份已绑定此主机");
        }
        var relation = new MsServerHostIdentity();
        relation.setMsServerHostIdentityHostId(hostId);
        relation.setMsServerHostIdentityIdentityId(identityId);
        relation.setMsServerHostIdentitySort(sort == null ? nextSort(hostId) : sort);
        hostIdentityMapper.insert(relation);
        return ReturnResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * unbindidentity。
     * @param hostId 主机标识
     * @param identityId identityid
     * @return unbindIdentity的结果
     */
    public ReturnResult<Void> unbindIdentity(Long hostId, Long identityId) {
        if (hostId == null || identityId == null) {
            return ReturnResult.error("主机ID与身份ID不能为空");
        }
        hostIdentityMapper.delete(new LambdaQueryWrapper<MsServerHostIdentity>()
                .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                .eq(MsServerHostIdentity::getMsServerHostIdentityIdentityId, identityId));
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<Long>> listBoundIdentityIds(Long hostId) {
        List<MsServerHostIdentity> relations = hostIdentityMapper.selectList(
                new LambdaQueryWrapper<MsServerHostIdentity>()
                        .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                        .orderByAsc(MsServerHostIdentity::getMsServerHostIdentitySort));
        List<Long> ids = new ArrayList<>(relations.size());
        for (MsServerHostIdentity relation : relations) {
            ids.add(relation.getMsServerHostIdentityIdentityId());
        }
        return ReturnResult.ok(ids);
    }

    @Override
    public ReturnResult<SshCredential> resolveCredential(Long hostId, Long identityId, String protocol) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        // 生效协议：调用方实际请求优先（SSH 直连传 SSH、VNC 直连传 VNC）。
        // 未显式指定时一律按 SSH 解析，**不再回落主机的远控协议**：
        // 主机远控协议为 VNC 时，回落会把「VNC 口令」当成 SSH 凭据返回（VNC 口令非 AES 密文，
        // 解密失败即得空口令），导致批量执行、Agent 部署、发布投放等 SSH 操作全部
        // 报「No more authentication methods available」。VNC 调用方始终显式传协议，
        // 因此 blank 一律是 SSH 语义。
        String effectiveProtocol = !isBlank(protocol) ? protocol.trim() : ProtocolType.SSH.name();
        boolean isVnc = ProtocolType.VNC.name().equalsIgnoreCase(effectiveProtocol);
        List<MsServerHostIdentity> relations = hostIdentityMapper.selectList(
                new LambdaQueryWrapper<MsServerHostIdentity>()
                        .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                        .orderByAsc(MsServerHostIdentity::getMsServerHostIdentitySort));
        if (relations.isEmpty()) {
            // 纯 VNC 主机（如 Windows 桌面共享）可只配 VNC 口令而不绑定登录身份，
            // 此时回落主机级 VNC 凭据；仅当请求方不是明确的 SSH/RDP 时才允许（blank 与 VNC 都放行）
            boolean vncFallback = !ProtocolType.SSH.name().equals(effectiveProtocol)
                    && !ProtocolType.RDP.name().equals(effectiveProtocol)
                    && !isBlank(host.getServerHostVncPassword());
            if (vncFallback) {
                return ReturnResult.ok(new SshCredential("",
                        decryptQuietly(host.getServerHostVncPassword(), host.getId()), null));
            }
            return ReturnResult.error("主机未绑定登录身份: " + host.getServerHostName());
        }
        // 指定身份时精确匹配绑定关系；未指定时按优先级取第一个
        MsServerHostIdentity chosen = null;
        for (MsServerHostIdentity relation : relations) {
            if (relation.getMsServerHostIdentityIdentityId().equals(identityId)) {
                chosen = relation;
                break;
            }
        }
        if (chosen == null) {
            chosen = identityId == null ? relations.getFirst() : null;
        }
        if (chosen == null) {
            return ReturnResult.error("身份未绑定到该主机: " + identityId);
        }
        MsServerIdentity identity = identityMapper.selectById(chosen.getMsServerHostIdentityIdentityId());
        if (identity == null) {
            return ReturnResult.error("绑定的身份已被删除");
        }
        String username = identity.getMsServerIdentityUsername();
        // VNC 桌面认证与 SSH/RDP 登录口令不同源：仅当本次请求就是 VNC 且主机配了 VNC 口令时才取它；
        // 请求 SSH/RDP 时这里 isVnc=false，一律走下面的绑定身份口令/私钥
        boolean vncOnly = isVnc && !isBlank(host.getServerHostVncPassword());
        if (vncOnly) {
            return ReturnResult.ok(new SshCredential(username,
                    decryptQuietly(host.getServerHostVncPassword(), hostId), null));
        }
        // 公钥认证：解密引用密钥的私钥内容与口令
        if (AuthType.PUBLIC_KEY.name().equals(identity.getMsServerIdentityAuthType())) {
            MsServerKey key = identity.getMsServerIdentityKeyId() == null
                    ? null : keyMapper.selectById(identity.getMsServerIdentityKeyId());
            if (key == null || isBlank(key.getMsServerKeyPrivateKey())) {
                return ReturnResult.error("公钥身份引用的密钥缺失或私钥为空: " + identity.getMsServerIdentityName());
            }
            return ReturnResult.ok(new SshCredential(username,
                    cipher.decrypt(key.getMsServerKeyPassword()),
                    cipher.decrypt(key.getMsServerKeyPrivateKey())));
        }
        // 密码认证：直接解密口令
        return ReturnResult.ok(new SshCredential(username,
                cipher.decrypt(identity.getMsServerIdentityPassword()), null));
    }

    @Override
    public ReturnResult<MsServerHost> scanPorts(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        String address = host.getServerHostAddress();
        if (isBlank(address)) {
            return ReturnResult.error("主机地址为空，无法扫描端口");
        }
        if (!RustNmapBridge.isLoaded()) {
            return ReturnResult.error("Rust Nmap 原生库未加载，请检查 jar 内置 native 库是否随平台分发");
        }
        // 同一主机同时只允许一个扫描任务，重复点击直接拒绝
        if (!portScanning.add(hostId)) {
            return ReturnResult.error("该主机端口扫描正在进行中，请等待本次扫描完成");
        }
        final String addr = address.trim();
        try {
            portScanExecutor.submit(() -> runPortScan(hostId, addr));
        } catch (Exception e) {
            portScanning.remove(hostId);
            return ReturnResult.error("端口扫描任务提交失败: " + e.getMessage());
        }
        // 立即返回，扫描结果落库后由 Socket 事件推送，前端不阻塞等待
        return ReturnResult.ok(hostMapper.selectById(hostId),
                "端口扫描已开始，正在后台扫描 " + addr + " 常用端口，完成后自动更新");
    }

    /**
     * 后台执行端口扫描：扫描结果落库并发布事件；异常折算为失败事件，绝不抛出线程外。
     *
     * @param hostId 主机标识
     * @param addr   主机地址
     */
    private void runPortScan(Long hostId, String addr) {
        long start = System.currentTimeMillis();
        try {
            // 只扫常用端口：1~65535 全量扫描实测超过 240 秒不返回，且期间把同进程采集接口拖慢数倍
            NmapScanner.ScanResult result = new RustNmapScanner().scanCommonPorts(addr);
            List<NmapScanner.PortInfo> openPorts = new ArrayList<>();
            if (result.getPorts() != null) {
                for (NmapScanner.PortInfo p : result.getPorts()) {
                    if (p.getState() == NmapScanner.PortState.OPEN) {
                        openPorts.add(p);
                    }
                }
            }
            String portsJson = toPortsJson(openPorts);
            LocalDateTime scanTime = LocalDateTime.now();
            MsServerHost update = hostMapper.selectById(hostId);
            if (update != null) {
                update.setServerHostPorts(portsJson);
                update.setServerHostLastPortScanTime(scanTime);
                hostMapper.updateById(update);
            }
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            eventPublisher.publishEvent(new HostPortScanEvent(hostId, addr, true, openPorts.size(),
                    result.getTotalPorts(), elapsed, portsJson, scanTime, null));
        } catch (Exception e) {
            log.warn("[server] 端口扫描异常 hostId={} address={}: {}", hostId, addr, e.getMessage());
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            eventPublisher.publishEvent(new HostPortScanEvent(hostId, addr, false, 0, 0,
                    elapsed, null, LocalDateTime.now(), e.getMessage()));
        } finally {
            portScanning.remove(hostId);
        }
    }

    /**
     * 端口扫描结果列表序列化为 JSON 数组字符串
     *
     * @param ports 端口列表
     * @return JSON 字符串
     */
    private String toPortsJson(List<NmapScanner.PortInfo> ports) {
        if (ports == null || ports.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, String>> list = new ArrayList<>(ports.size());
            for (NmapScanner.PortInfo p : ports) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("port", String.valueOf(p.getPort()));
                entry.put("protocol", p.getProtocol() == null ? "tcp" : p.getProtocol().toLowerCase());
                entry.put("service", p.getServiceName() == null ? "unknown" : p.getServiceName());
                entry.put("state", p.getState() == null ? "open" : p.getState().name().toLowerCase());
                list.add(entry);
            }
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("[server] 端口结果序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    @Override
    public ReturnResult<MsServerHost> detectHardware(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        // 按主机路由 SPI：采集通道专属实现（Agent 自报）优先，其余按平台（Linux/macOS 走 SSH、Windows 走 WinRM）
        HostDetectProvider provider = detectProviderRegistry.resolve(host).orElse(null);
        if (provider == null) {
            log.warn("[detect] 未找到采集方式 {} / 系统类型 {} 的探测实现 hostId={}",
                    host.getServerHostCollectMode(), host.getServerHostOsType(), hostId);
            return ReturnResult.error("不支持的探测系统类型: " + host.getServerHostOsType());
        }
        SshCredential credential = null;
        if (provider.requiresCredential()) {
            ReturnResult<SshCredential> resolved = resolveCredential(hostId, null);
            if (!resolved.isOk()) {
                return ReturnResult.error(resolved.getMsg());
            }
            credential = resolved.getData();
        }
        ReturnResult<MsServerHost> detected = provider.detect(host, credential);
        if (!detected.isOk()) {
            return detected;
        }
        // detect 已在库数据 host 上回写硬件/包管理器等探测字段，直接以探测结果为准持久化，
        // 避免重新 selectById 得到未含探测修改的对象而丢失 PM/硬件
        MsServerHost probed = detected.getData();
        if (probed == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        // 统一补采公网 IP（SSH 探测目标出口 IP，失败静默保持原值）
        String publicIp = fetchPublicIp(probed);
        if (StringUtils.isNotBlank(publicIp)) {
            probed.setServerHostPublicIp(publicIp);
        }
        // 存量主机历史编码可能为空，探测时补齐，保证资产编码不再有空数据
        if (isBlank(probed.getServerHostCode())) {
            probed.setServerHostCode(generateHostCode());
        }
        hostMapper.updateById(probed);
        markOnline(hostId, true);
        return ReturnResult.ok(hostMapper.selectById(hostId), "硬件探测成功");
    }
    @Override
    public ReturnResult<String> ensureOpenSsh(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (!"WINDOWS".equalsIgnoreCase(host.getServerHostOsType())) {
            return ReturnResult.error("仅 Windows 主机支持 OpenSSH 引导");
        }
        ReturnResult<SshCredential> rr = resolveCredential(hostId, null);
        if (!rr.isOk()) {
            return ReturnResult.error(rr.getMsg());
        }
        SshCredential cred = rr.getData();
        int targetPort = DEFAULT_SSH_PORT;
        String address = host.getServerHostAddress();
        StringBuilder detail = new StringBuilder();
        try {
            if (isTcpReachable(address, targetPort, 3000)) {
                // 22 已可达：经 OpenSSH 做幂等收敛（开机自启、运行中、防火墙放行）
                String ensure = """
                        $ErrorActionPreference='Stop'
                        $svc = Get-Service -Name sshd -ErrorAction SilentlyContinue
                        if (-not $svc) { Write-Output 'SSHD_MISSING'; exit 2 }
                        Set-Service -Name sshd -StartupType Automatic
                        if ($svc.Status -ne 'Running') { Start-Service sshd }
                        if (-not (Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue)) {
                          New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
                        }
                        Write-Output 'SSHD_READY'
                        """;
                WindowsSshShell.Result r = windowsSshShell.exec(copyForPort(host, targetPort), cred, ensure, 60);
                if (r.exitCode() != 0) {
                    return ReturnResult.error("OpenSSH 收敛失败: " + r.stderr());
                }
                detail.append("OpenSSH 已就绪");
                if (StringUtils.isNotBlank(r.stdout())) {
                    detail.append('\n').append(r.stdout().trim());
                }
            } else {
                // 22 不可达：经 WinRM(5985) 一次性引导安装，之后全部走 OpenSSH
                String bootstrap = """
                        $ErrorActionPreference='Stop'
                        [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
                        $svc = Get-Service -Name sshd -ErrorAction SilentlyContinue
                        if (-not $svc) {
                          if (Get-Command Add-WindowsCapability -ErrorAction SilentlyContinue) {
                            Get-WindowsCapability -Online | Where-Object { $_.Name -like 'OpenSSH.Server*' } | Add-WindowsCapability -Online | Out-Null
                          } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
                            choco install openssh -y --no-progress
                          } else {
                            $base='C:\\Program Files'
                            $zip=Join-Path $env:TEMP 'openssh-win64.zip'
                            Invoke-WebRequest -Uri 'https://github.com/PowerShell/Win32-OpenSSH/releases/latest/download/OpenSSH-Win64.zip' -UseBasicParsing -OutFile $zip
                            Expand-Archive -Path $zip -DestinationPath $base -Force
                            if (Test-Path (Join-Path $base 'OpenSSH')) { Remove-Item (Join-Path $base 'OpenSSH') -Recurse -Force }
                            Rename-Item (Join-Path $base 'OpenSSH-Win64') 'OpenSSH'
                            & (Join-Path $base 'OpenSSH\\install-sshd.ps1')
                          }
                        }
                        Set-Service -Name sshd -StartupType Automatic
                        Start-Service sshd
                        if (-not (Get-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -ErrorAction SilentlyContinue)) {
                          New-NetFirewallRule -Name 'OpenSSH-Server-In-TCP' -DisplayName 'OpenSSH Server (sshd)' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22 | Out-Null
                        }
                        Write-Output 'SSHD_INSTALLED'
                        """;
                WinrmShell.Result r = winrmShell.exec(copyForPort(host, 5985), cred, bootstrap, 600);
                if (r.exitCode() != 0) {
                    return ReturnResult.error("OpenSSH 引导安装失败: " + r.stderr());
                }
                if (!awaitTcpReachable(address, targetPort, 40_000L)) {
                    return ReturnResult.error("安装已完成但 22 端口仍不可达");
                }
                detail.append("OpenSSH 安装并启用");
                if (StringUtils.isNotBlank(r.stdout())) {
                    detail.append('\n').append(r.stdout().trim());
                }
            }
            // 登录端口回写为 22，后续操作全部走 OpenSSH
            if (host.getServerHostPort() == null || host.getServerHostPort() != targetPort) {
                host.setServerHostPort(targetPort);
                hostMapper.updateById(host);
            }
            markOnline(hostId, true);
            return ReturnResult.ok(detail.toString(), "OpenSSH 已就绪");
        } catch (Exception e) {
            log.warn("[ssh] ensure OpenSSH failed hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("OpenSSH 引导异常: " + e.getMessage());
        }
    }

    /**
     * 探测 TCP 端口是否可连
     *
     * @param address   主机地址
     * @param port      端口
     * @param timeoutMs 连接超时（毫秒）
     * @return 可连返回 true
     */
    private boolean isTcpReachable(String address, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 轮询等待 TCP 端口可连
     *
     * @param address 主机地址
     * @param port    端口
     * @param waitMs  最长等待（毫秒）
     * @return 等待期内可连返回 true
     * @throws InterruptedException 等待被中断时抛出
     */
    private boolean awaitTcpReachable(String address, int port, long waitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (isTcpReachable(address, port, 2000)) {
                return true;
            }
            Thread.sleep(2000L);
        }
        return false;
    }

    /**
     * 复制主机并覆盖登录端口（引导时强制走指定通道，不污染库中对象）
     *
     * @param src  原主机
     * @param port 覆盖端口
     * @return 端口覆盖后的浅拷贝
     */
    private MsServerHost copyForPort(MsServerHost src, int port) {
        MsServerHost copy = new MsServerHost();
        BeanUtils.copyProperties(src, copy);
        copy.setServerHostPort(port);
        return copy;
    }

    @Override
    public ReturnResult<String> refreshPublicIp(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        String publicIp = fetchPublicIp(host);
        if (isBlank(publicIp)) {
            return ReturnResult.error("无法获取公网 IP: " + host.getServerHostName());
        }
        host.setServerHostPublicIp(publicIp);
        hostMapper.updateById(host);
        return ReturnResult.ok(publicIp, "公网 IP 已刷新");
    }

    /**
     * 批量同步虚拟线程执行器（SSH 探测为阻塞 IO，虚拟线程并发最合适）
     */
    private static final java.util.concurrent.ExecutorService SYNC_EXECUTOR =
            java.util.concurrent.Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("host-sync-").factory());

    @Override
    public ReturnResult<HostSyncSummaryVO> syncHosts(java.util.List<Long> hostIds) {
        try {
            List<MsServerHost> targets;
            if (hostIds == null || hostIds.isEmpty()) {
                targets = hostMapper.selectList(new LambdaQueryWrapper<MsServerHost>()
                        .eq(MsServerHost::getServerHostStatus, 1));
            } else {
                targets = hostMapper.selectBatchIds(hostIds);
            }
            if (targets == null || targets.isEmpty()) {
                return ReturnResult.error("无可同步的主机");
            }
            java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger skipped = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.List<HostSyncChangeVO> changes =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            java.util.List<java.util.concurrent.CompletableFuture<Void>> tasks =
                    new java.util.ArrayList<>(targets.size());
            for (MsServerHost target : targets) {
                tasks.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        if (syncSingleHost(target, changes)) {
                            ok.incrementAndGet();
                        } else {
                            skipped.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        log.warn("[server] 主机同步失败 hostId={}: {}", target.getId(), e.getMessage());
                    }
                }, SYNC_EXECUTOR));
            }
            java.util.concurrent.CompletableFuture.allOf(tasks.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            HostSyncSummaryVO summary = new HostSyncSummaryVO(targets.size(), ok.get(),
                    skipped.get(), failed.get(), List.copyOf(changes));
            log.info("[server] 批量同步完成: 共 {} 台, 更新 {} 台, 未变更 {} 台, 失败 {} 台",
                    targets.size(), ok.get(), skipped.get(), failed.get());
            return ReturnResult.ok(summary, "同步完成");
        } catch (Exception e) {
            log.error("[server] 批量同步异常", e);
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 同步单台主机资产（SSH 硬件探测 + 公网/内网 IP，信息无变化时跳过回写）
     * <p>
     * 探测到的主机信息 / 硬件规格 / IP 会同步回资产表；当公网 IP 或内网 IP
     * 与库中不一致时触发更新并记录变更明细，其他字段仅在探测值有效且不同时覆盖。
     * </p>
     *
     * @param target  目标主机
     * @param changes 变更明细收集器
     * @return true=有字段更新，false=无变化跳过
     */
    private boolean syncSingleHost(MsServerHost target, java.util.List<HostSyncChangeVO> changes) {
        // 保存探测前 IP 快照（用于判断是否发生变更）
        String oldPublicIp = target.getServerHostPublicIp();
        String oldPrivateIp = target.getServerHostPrivateIp();
        ReturnResult<MsServerHost> detected = detectHardware(target.getId());
        if (!detected.isOk()) {
            throw new IllegalStateException(detected.getMsg() == null ? "硬件探测失败" : detected.getMsg());
        }
        if (detected.getData() == null) {
            return false;
        }
        MsServerHost latest = hostMapper.selectById(target.getId());
        if (latest == null) {
            return false;
        }
        boolean ipChanged = !java.util.Objects.equals(
                oldPublicIp, latest.getServerHostPublicIp())
                || !java.util.Objects.equals(
                oldPrivateIp, latest.getServerHostPrivateIp());
        if (!ipChanged) {
            return false;
        }
        changes.add(new HostSyncChangeVO(latest.getId(), latest.getServerHostName(),
                latest.getServerHostPublicIp(), latest.getServerHostPrivateIp()));
        return true;
    }

/**
 * 采集目标主机出口公网 IP
 * <p>
 * 优先走 SSH（cURL ifconfig.me / ipinfo.io），SSH 不可用时回退到本机出口 IP 探测。
 * 127/localhost 等本地地址直接返回空串不探测。
 * </p>
 *
 * @param host 主机
 * @return 公网 IP，获取失败返回空串
 */
    private String fetchPublicIp(MsServerHost host) {
        String address = host.getServerHostAddress();
        if (isBlank(address) || "127.0.0.1".equals(address.trim()) || "localhost".equalsIgnoreCase(address.trim())) {
            return "";
        }
        // 优先通过 SSH 在目标主机上探测出口 IP
        try {
            ReturnResult<SshCredential> resolved = resolveCredential(host.getId(), null);
            // Windows 主机无 SSH（其端口是 WinRM），跳过 SSH 探测直接走本机回退
            if (resolved.isOk() && !"WINDOWS".equalsIgnoreCase(host.getServerHostOsType())) {
                SshClients.Handle handle = null;
                try {
                    handle = connectionManager.borrow(host, resolved.getData());
                    for (String cmd : new String[]{
                            "curl -s --max-time 5 ifconfig.me",
                            "curl -s --max-time 5 ipinfo.io/ip",
                            "wget -qO- --timeout=5 ifconfig.me"}) {
                        String ip = execQuiet(handle, cmd);
                        if (!isBlank(ip) && isIp(ip.trim())) {
                            return ip.trim();
                        }
                    }
                } finally {
                    if (handle != null) {
                        connectionManager.release(host.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[server] SSH 探测公网IP失败, 回退本机探测 hostId={}", host.getId(), e);
        }
        // 回退：探测本机出口 IP（供公网资产参考）。响应可能被劫持为 HTML 错误页，
        // 必须严格校验为合法 IPv4 才采用，避免超长非 IP 内容写入公网IP列
        try {
            String ip = fetchViaHttp("https://ifconfig.me");
            if (!isIp(ip == null ? "" : ip.trim())) {
                ip = fetchViaHttp("https://api.ipify.org");
            }
            ip = ip == null ? "" : ip.trim();
            return isIp(ip) ? ip : "";
        } catch (Exception e) {
            log.debug("[server] 本机公网IP探测失败 hostId={}", host.getId(), e);
            return "";
        }
    }

    /**
     * 通过 HTTP 获取 获取文本响应
     *
     * @param url 地址
     * @return 响应文本，失败返回空串
     */
    private String fetchViaHttp(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            /**
             * 尝试。
             * @param conn.getInputStream( conn.获取输入流(
             */
            try (var in = conn.getInputStream()) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 判断字符串是否为 ipv4
     *
     * @param value 字符串
     * @return 是否为 IP
     */
    private boolean isIp(String value) {
        if (value == null) {
            return false;
        }
        String[] parts = value.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    @Override
    public MsServerHost autoCreateIdentity(MsServerHost host) {
        if (host == null || host.getId() == null) {
            return host;
        }
        String account = host.getServerHostAccount();
        String password = host.getServerHostPassword();
        String privateKey = host.getServerHostPrivateKey();
        boolean hasPassword = !isBlank(account) && !isBlank(password);
        boolean hasKey = !isBlank(account) && !isBlank(privateKey);
        if (!hasPassword && !hasKey) {
            return host;
        }
        try {
            // 避免同名身份堆积：名称使用 主机名@账号 模板
            String base = (host.getServerHostName() == null ? "host" : host.getServerHostName()) + "@" + account;
            MsServerIdentity identity = new MsServerIdentity();
            identity.setMsServerIdentityName(base);
            identity.setMsServerIdentityUsername(account.trim());
            identity.setMsServerIdentityRemark("新增主机自动创建");
            if (hasKey) {
                // 密钥方式：入库私钥并引用
                MsServerKey key = new MsServerKey();
                key.setMsServerKeyName(base + "-key");
                key.setMsServerKeyType(detectKeyType(privateKey));
                key.setMsServerKeyPrivateKey(cipher.encrypt(privateKey.trim()));
                key.setMsServerKeyRemark("新增主机自动创建");
                keyMapper.insert(key);
                identity.setMsServerIdentityAuthType(AuthType.PUBLIC_KEY.name());
                identity.setMsServerIdentityKeyId(key.getId());
            } else {
                identity.setMsServerIdentityAuthType(AuthType.PASSWORD.name());
                identity.setMsServerIdentityPassword(cipher.encrypt(password));
            }
            identityMapper.insert(identity);
            // 绑定到主机（最高优先级：排最前）
            var relation = new MsServerHostIdentity();
            relation.setMsServerHostIdentityHostId(host.getId());
            relation.setMsServerHostIdentityIdentityId(identity.getId());
            relation.setMsServerHostIdentitySort(1);
            hostIdentityMapper.delete(new LambdaQueryWrapper<MsServerHostIdentity>()
                    .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, host.getId()));
            hostIdentityMapper.insert(relation);
            log.info("[server] 新增主机自动创建身份 hostId={} identityId={} type={}",
                    host.getId(), identity.getId(), identity.getMsServerIdentityAuthType());
        } catch (Exception e) {
            log.warn("[server] 自动创建身份失败 hostId={} account={}: {}", host.getId(), account, e.getMessage());
        }
        return host;
    }

    /**
     * 从 PEM 私钥内容推断密钥类型
     *
     * @param privateKey PEM 私钥
     * @return RSA / ED25519 / ECDSA
     */
    private String detectKeyType(String privateKey) {
        String t = privateKey == null ? "" : privateKey;
        if (t.contains("ED25519")) {
            return "ED25519";
        }
        if (t.contains("EC PRIVATE KEY") || t.contains("ECDSA")) {
            return "ECDSA";
        }
        return "RSA";
    }

    /**
     * 静默执行命令并返回输出（失败返回空串）
     *
     * @param handle  SSH 句柄
     * @param command 命令
     * @return 标准输出，异常返回空串
     */
    private String execQuiet(SshClients.Handle handle, String command) {
        try {
            return handle.client().exec().command(command).executeAndGetOutput();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 判断字符串是否为纯数字
     *
     * @param value 字符串
     * @return 是否纯数字
     */
    private boolean isNumeric(String value) {
        if (value == null) {
            return false;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 回写主机在线状态与最近在线时间
     *
     * @param hostId 主机 标识
     * @param online 是否在线
     */
    private void markOnline(Long hostId, boolean online) {
        try {
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                return;
            }
            host.setServerHostOnline(online ? ONLINE_YES : 0);
            host.setServerHostOnlineTime(LocalDateTime.now());
            hostMapper.updateById(host);
        } catch (Exception e) {
            log.warn("[server] 更新在线状态失败 hostId={}", hostId, e);
        }
    }

    /**
     * 计算主机当前最大绑定优先级加一
     *
     * @param hostId 主机 标识
     * @return 下一个优先级值
     */
    private int nextSort(Long hostId) {
        List<MsServerHostIdentity> relations = hostIdentityMapper.selectList(
                new LambdaQueryWrapper<MsServerHostIdentity>()
                        .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId));
        int max = 0;
        for (MsServerHostIdentity relation : relations) {
            if (relation.getMsServerHostIdentitySort() != null && relation.getMsServerHostIdentitySort() > max) {
                max = relation.getMsServerHostIdentitySort();
            }
        }
        return max + 1;
    }

    /**
     * 生成 32 位十六进制探针标识
     *
     * @return agentKey
     */
    private String generateAgentKey() {
        byte[] bytes = new byte[AGENT_KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成可读且全局唯一的主机资产编码：HOST-XXXXXXXX（8 位大写十六进制）
     *
     * @return hostCode
     */
    private String generateHostCode() {
        byte[] bytes = new byte[4];
        secureRandom.nextBytes(bytes);
        return "HOST-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    /**
     * 静默解密凭据（失败仅告警，避免详情接口/凭据解析被裸异常打断）
     *
     * @param cipherText 密文
     * @param hostId     主机标识（仅日志定位）
     * @return 明文，解密失败返回空
     */
    private String decryptQuietly(String cipherText, Long hostId) {
        if (isBlank(cipherText)) {
            return cipherText;
        }
        try {
            return cipher.decrypt(cipherText);
        } catch (Exception e) {
            log.warn("[server] VNC 口令解密失败 hostId={}: {}", hostId, e.getMessage());
            return null;
        }
    }

    /**
     * 回显远控凭据：把库中 VNC 口令解密成明文供编辑弹窗使用
     *
     * @param host 主机（可为空，原地修改）
     * @return 同一对象（已回填明文，解密失败置空）
     */
    private MsServerHost decryptRemoteCredential(MsServerHost host) {
        if (host == null) {
            return host;
        }
        if (!isBlank(host.getServerHostVncPassword())) {
            host.setServerHostVncPassword(decryptQuietly(host.getServerHostVncPassword(), host.getId()));
        }
        return host;
    }

    /**
     * 脱敏：清除 VNC 口令，列表与导出接口不下发任何凭据
     *
     * @param host 主机
     * @return 同一对象（已脱敏）
     */
    private MsServerHost mask(MsServerHost host) {
        host.setServerHostVncPassword(null);
        return host;
    }

    /**
     * Prometheus 凭据校验：GET 目标主机 /metrics 端点
     *
     * @param id 主机主键
     * @return 校验结果
     */
    @Override
    public ReturnResult<String> testPrometheus(Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        String address = host.getServerHostAddress();
        if (isBlank(address)) {
            return ReturnResult.error("主机地址为空");
        }
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0
                ? 9100 : host.getServerHostPort();
        String url = "http://" + address.trim() + ":" + port + "/metrics";
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return ReturnResult.error("metrics 端点返回 " + code);
            }
            int lines = 0;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    lines++;
                }
            }
            log.info("[server] Prometheus 校验成功 hostId={} url={} lines={}", id, url, lines);
            return ReturnResult.ok(url, "校验成功，共 " + lines + " 行指标");
        } catch (Exception e) {
            log.warn("[server] Prometheus 校验失败 hostId={} url={}: {}", id, url, e.getMessage());
            return ReturnResult.error("连接失败: " + e.getMessage());
        }
    }
    /**
     * 校验远控通道与协议的搭配
     * <p>
     * RDP 只能由 guacd 中转渲染，平台没有原生 RDP 客户端；DIRECT 是平台自研通道，
     * 覆盖 SSH（MINA SSHD PTY 桥接）与 VNC（RFB 帧桥接）；NONE 表示不经平台通道
     * （SSH 用平台终端、VNC 用本机客户端）。
     * </p>
     * <p>
     * VNC 直连只实现 RFB 标准认证（None/VNC 口令），macOS 远程管理的 ARD、TLS、AES 等
     * 扩展安全类型仍需 guacd 中转，属运行时能力边界而非配置约束，这里不拦。
     * </p>
     *
     * @param host 主机（取远控协议、通道与系统类型）
     * @return 错误文案，通过校验返回 null
     */
    private String validateRemoteChannel(MsServerHost host) {
        String mode = upper(host.getServerHostRemoteMode());
        if (mode.isEmpty()) {
            return null;
        }
        if (!REMOTE_MODES.contains(mode)) {
            return "远控通道只能是 GUACD/DIRECT/NONE，当前值: " + mode;
        }
        String protocol = effectiveRemoteProtocol(host);
        if ("RDP".equals(protocol) && !"GUACD".equals(mode)) {
            return "RDP 必须经 guacd 中转通道，远控通道不能设为 " + mode;
        }
        if ("DIRECT".equals(mode) && !DIRECT_PROTOCOLS.contains(protocol)) {
            return protocol + " 不支持平台直连通道，请改为 guacd 中转或无需通道";
        }
        return null;
    }

    /**
     * 校验「本机」主机的地址确实是平台所在机器
     * <p>
     * 本机台账会走 OSHI 自采集并绑 127.0.0.1 上的探针端口，地址填成远端 IP 时
     * 采集到的是本机数据却记在远端 IP 名下，台账与画像全部错位，必须在入口拦住。
     * </p>
     *
     * @param host 主机
     * @return 错误文案，非本机类型或校验通过返回 null
     */
    private String validateLocalAddress(MsServerHost host) {
        if (!"LOCAL".equals(upper(host.getServerHostCollectMode()))) {
            return null;
        }
        String address = host.getServerHostAddress() == null ? "" : host.getServerHostAddress().trim();
        if (address.isEmpty()) {
            // 空地址由入口必填校验统一处理，这里不发误导性的「不是本机地址」
            return null;
        }
        if (isLocalAddress(address)) {
            return null;
        }
        return "采集方式为「本机」但地址 " + address + " 不是本机地址；本机请使用 127.0.0.1 或本机网卡地址（"
                + localAddressesHint() + "）";
    }

    /**
     * 平台列归一：os_type 只承载真实平台，接入方式归 collect_mode。
     *
     * <p>历史口径把 {@code LOCAL} / {@code AGENT} 填进平台列（前端表单同样如此），这里收编为采集方式；
     * 本机自采集的主机即运行本服务的这台机器，平台不由表单决定，一律按本机操作系统覆盖；
     * 其余无法识别的值沿用原默认 LINUX。</p>
     *
     * @param host 主机
     */
    /**
     * 应用「是否开启 SSH」默认值：Linux/macOS 默认开启，Windows 默认关闭（需自行启用 OpenSSH）。
     * 仅在未显式设置时补默认，不覆盖用户已有的显式选择。
     *
     * @param host 主机
     */
    private void applySshEnabledDefault(MsServerHost host) {
        if (host.getServerHostSshEnabled() == null) {
            host.setServerHostSshEnabled(defaultSshEnabled(host.getServerHostOsType()));
        }
    }

    /**
     * 按操作系统类型返回 SSH 默认开关（Linux/macOS=1，Windows=0）
     *
     * @param osType 操作系统类型
     * @return 1=开启 0=关闭
     */
    private int defaultSshEnabled(String osType) {
        return osType != null && "WINDOWS".equalsIgnoreCase(osType.trim()) ? 0 : 1;
    }

    private void applyOsType(MsServerHost host) {
        String raw = host.getServerHostOsType();
        String platform = HostPlatforms.normalize(raw);
        if (platform.isEmpty() && raw != null) {
            String token = upper(raw);
            if (("LOCAL".equals(token) || "AGENT".equals(token)) && isBlank(host.getServerHostCollectMode())) {
                host.setServerHostCollectMode(token);
            }
        }
        if ("LOCAL".equals(upper(host.getServerHostCollectMode()))) {
            String local = HostPlatforms.normalize(System.getProperty("os.name"));
            if (!local.isEmpty()) {
                platform = local;
            }
        }
        host.setServerHostOsType(platform.isEmpty() ? HostPlatforms.LINUX : platform);
    }

    /**
     * 「本机」主机的地址与远控协议归一。
     *
     * <p>本机即运行本服务的机器，远控由平台自身发起：①地址强制为物理网卡的局域网 IP，
     * 不使用回环地址（远程 guacd 收到 127.0.0.1 会回连到 guacd 自身，无法连到本机）；
     * ②本机远控仅支持 VNC，强制协议为 VNC。</p>
     *
     * @param host 主机
     */
    private void applyLocalDefaults(MsServerHost host) {
        if (!"LOCAL".equals(upper(host.getServerHostCollectMode()))) {
            return;
        }
        // 地址为空或回环（guacd 收到 127.0.0.1 会回连自身）时归一为物理网卡 IP；
        // 用户显式填写的本机网卡地址保留，不强行覆盖
        String address = host.getServerHostAddress() == null ? "" : host.getServerHostAddress().trim();
        if (address.isEmpty() || isLoopbackAddress(address)) {
            host.setServerHostAddress(NetUtils.getPreferredLocalIp());
        }
        host.setServerHostRemoteProtocol(ProtocolType.VNC.name());
    }

    /**
     * 判断地址是否为回环地址（127.0.0.1 / localhost / ::1）。
     *
     * @param address 主机地址
     * @return 回环返回 true
     */
    private boolean isLoopbackAddress(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生效远控协议：显式配置优先，留空按系统类型推断（与前端推断口径一致）
     *
     * @param host 主机
     * @return SSH/RDP/VNC
     */
    private String effectiveRemoteProtocol(MsServerHost host) {
        String protocol = upper(host.getServerHostRemoteProtocol());
        if (!protocol.isEmpty()) {
            return protocol;
        }
        String os = upper(host.getServerHostOsType());
        if (os.contains("WIN")) {
            return "RDP";
        }
        if (os.contains("MAC") || os.contains("DARWIN")) {
            return "VNC";
        }
        return "SSH";
    }

    /**
     * 判断地址是否指向本机（回环地址或本机任一网卡地址）
     *
     * @param address 主机地址
     * @return 是否本机地址
     */
    private boolean isLocalAddress(String address) {
        if (address.isEmpty()) {
            return false;
        }
        try {
            InetAddress target = InetAddress.getByName(address);
            if (target.isLoopbackAddress() || target.isAnyLocalAddress()) {
                return true;
            }
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces(); nics.hasMoreElements(); ) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp()) {
                    continue;
                }
                for (InterfaceAddress assigned : nic.getInterfaceAddresses()) {
                    if (assigned.getAddress().equals(target)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[server] 本机地址判定失败 {}: {}", address, e.getMessage());
        }
        return false;
    }

    /**
     * 列出本机网卡地址，供校验失败时给出可直接照抄的提示
     *
     * @return 逗号分隔地址串，取不到时返回「未知」
     */
    private String localAddressesHint() {
        List<String> addresses = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces(); nics.hasMoreElements(); ) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp()) {
                    continue;
                }
                for (InterfaceAddress assigned : nic.getInterfaceAddresses()) {
                    addresses.add(assigned.getAddress().getHostAddress());
                }
            }
        } catch (Exception e) {
            log.debug("[server] 枚举本机网卡失败: {}", e.getMessage());
        }
        return addresses.isEmpty() ? "未知" : String.join(", ", addresses);
    }

    /**
     * 去空白并转大写
     *
     * @param value 原值
     * @return 大写文本，null 折算为空串
     */
    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 空串判断
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
