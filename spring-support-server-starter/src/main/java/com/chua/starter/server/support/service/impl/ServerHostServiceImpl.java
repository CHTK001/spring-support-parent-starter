package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.nmap.support.NmapScanner;
import com.chua.nmap.support.RustNmapScanner;
import com.chua.nmap.support.bridge.RustNmapBridge;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.entity.MsServerKey;
import com.chua.starter.server.support.enums.AuthType;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerKeyMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.CredentialCipher;
import com.chua.starter.server.support.util.SshClients;
import com.chua.winrm.support.client.WinRmExecClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 在线状态：未知 */
    private static final int ONLINE_UNKNOWN = -1;

    /** 在线状态：在线 */
    private static final int ONLINE_YES = 1;

    /** 启用状态：启用 */
    private static final int STATUS_ENABLED = 1;

    /** 默认 SSH 端口 */
    private static final int DEFAULT_SSH_PORT = 22;

    /** JSON 序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 探针标识字节长度（32 个十六进制字符） */
    private static final int AGENT_KEY_BYTES = 16;

    /** Linux 内核信息命令 */
    private static final String CMD_UNAME = "uname -sr";

    /** 主机 映射器 */
    private final MsServerHostMapper hostMapper;

    /** 身份 映射器 */
    private final MsServerIdentityMapper identityMapper;

    /** 密钥 映射器 */
    private final MsServerKeyMapper keyMapper;

    /** 主机身份关联 映射器 */
    private final MsServerHostIdentityMapper hostIdentityMapper;

    /** 凭据加解密器 */
    private final CredentialCipher cipher;

    /** 连接缓存管理器 */
    private final ConnectionManager connectionManager;

    /** 探针标识随机源 */
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
        if (isBlank(host.getServerHostOsType())) {
            host.setServerHostOsType("LINUX");
        }
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
        if (host.getServerHostCollectInterval() == null) {
            host.setServerHostCollectInterval(3600);
        }
        if (host.getServerHostAgentEnabled() == null) {
            host.setServerHostAgentEnabled(0);
        }
        if (host.getServerHostRemoteEnabled() == null) {
            host.setServerHostRemoteEnabled(1);
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
        hostMapper.insert(host);
        // 携带账号密码/PEM 时自动建身份并绑定，保证新增即可测连/远控
        /**
         * auto创建identity。
         * @param host 主机
         */
        autoCreateIdentity(host);
        return ReturnResult.ok(hostMapper.selectById(host.getId()));
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
        if (host.getServerHostCollectInterval() != null && host.getServerHostCollectInterval() > 0) {
            exists.setServerHostCollectInterval(host.getServerHostCollectInterval());
        }
        if (host.getServerHostAgentEnabled() != null) {
            exists.setServerHostAgentEnabled(host.getServerHostAgentEnabled());
        }
        if (host.getServerHostRemoteEnabled() != null) {
            exists.setServerHostRemoteEnabled(host.getServerHostRemoteEnabled());
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
        hostMapper.updateById(exists);
        // 携带账号密码/PEM 时自动创建/更新登录身份并绑定（编辑时亦可调整凭据）
        /**
         * auto创建identity。
         * @param exists exists
         */
        autoCreateIdentity(exists);
        return ReturnResult.ok(hostMapper.selectById(id));
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
        return ReturnResult.ok(host);
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
        return ReturnResult.ok(hostMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新状态。
     * @param id 标识
     * @param enabled 已启用
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
        ReturnResult<SshCredential> resolved = resolveCredential(id, null);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
 // 登录+认证成功即为连通; banner 仅尽力获取(执行 通道不可用时不影响结论)
            String banner = "connected";
            if ("LINUX".equalsIgnoreCase(host.getServerHostOsType())) {
                try {
                    String uname = handle.client().exec().command(CMD_UNAME).executeAndGetOutput();
                    if (!isBlank(uname)) {
                        banner = uname;
                    }
                } catch (Exception execError) {
 // 打印完整堆栈便于定位 执行 通道问题(不影响连通性结论)
                    log.info("[server] exec 通道不可用(不影响连通性结论)", execError);
                }
            }
            /**
              * markonline。
             * @param host.getId( 主机.获取标识(
             */
            markOnline(host.getId(), true);
            return ReturnResult.ok(banner, "测试成功");
        } catch (Exception e) {
            /**
              * markonline。
             * @param host.getId( 主机.获取标识(
             */
            markOnline(host.getId(), false);
            log.warn("[server] 连通性测试失败 hostId={} address={}: {}", id, host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("连接失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(id);
            }
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
    public ReturnResult<SshCredential> resolveCredential(Long hostId, Long identityId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        List<MsServerHostIdentity> relations = hostIdentityMapper.selectList(
                new LambdaQueryWrapper<MsServerHostIdentity>()
                        .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                        .orderByAsc(MsServerHostIdentity::getMsServerHostIdentitySort));
        if (relations.isEmpty()) {
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
        try {
            RustNmapScanner scanner = new RustNmapScanner();
            NmapScanner.ScanResult result = scanner.scanAllPorts(address.trim());
            List<NmapScanner.PortInfo> openPorts = new ArrayList<>();
            if (result.getPorts() != null) {
                for (NmapScanner.PortInfo p : result.getPorts()) {
                    if (p.getState() == NmapScanner.PortState.OPEN) {
                        openPorts.add(p);
                    }
                }
            }
            host.setServerHostPorts(toPortsJson(openPorts));
            host.setServerHostLastPortScanTime(LocalDateTime.now());
            hostMapper.updateById(host);
            return ReturnResult.ok(hostMapper.selectById(hostId),
                    String.format("端口扫描完成，发现 %d 个开放端口", openPorts.size()));
        } catch (Exception e) {
            log.warn("[server] 端口扫描异常 hostId={} address={}: {}", hostId, address, e.getMessage());
            return ReturnResult.error("端口扫描异常: " + e.getMessage());
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
        ReturnResult<SshCredential> resolved = resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        // Windows 主机走 WinRM（PowerShell）采集，其余走 SSH
        if ("WINDOWS".equalsIgnoreCase(host.getServerHostOsType())) {
            return detectHardwareViaWinrm(host, resolved.getData());
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
            // 采集 CPU 核心
            String cpu = execQuiet(handle, "nproc");
            if (isNumeric(cpu)) {
                host.setServerHostCpuCores(Integer.valueOf(cpu.trim()));
            }
            // 采集内存(MB)
            String mem = execQuiet(handle, "free -m | awk '/^Mem:/{print $2}'");
            if (isNumeric(mem)) {
                host.setServerHostMemoryMb(Long.valueOf(mem.trim()));
            }
            // 采集磁盘(GB, 根分区)
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (isNumeric(disk)) {
                host.setServerHostDiskGb(Long.valueOf(disk.trim()));
            }
            // 采集系统版本
            String os = execQuiet(handle, "cat /etc/os-release 2>/dev/null | grep -m1 PRETTY_NAME | cut -d'=' -f2 | tr -d '\"'");
            if (isBlank(os)) {
                os = execQuiet(handle, "uname -sr");
            }
            if (!isBlank(os)) {
                host.setServerHostOsVersion(os.trim());
                // 从版本关键字推断系统类型
                String lower = os.toLowerCase();
                if (lower.contains("windows") || lower.contains("microsoft")) {
                    host.setServerHostOsType("WINDOWS");
                } else if (lower.contains("darwin") || lower.contains("mac")) {
                    host.setServerHostOsType("MACOS");
                } else {
                    host.setServerHostOsType("LINUX");
                }
            }
            // 采集架构
            String arch = execQuiet(handle, "uname -m");
            if (!isBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
            // 采集内网 IP
            String ip = execQuiet(handle, "hostname -I 2>/dev/null | awk '{print $1}'");
            if (!isBlank(ip)) {
                host.setServerHostPrivateIp(ip.trim());
            }
            // 采集公网 IP（SSH 探测目标出口 IP，失败静默保持原值）
            String publicIp = fetchPublicIp(host);
            if (!isBlank(publicIp)) {
                host.setServerHostPublicIp(publicIp);
            }
            hostMapper.updateById(host);
            /**
              * markonline。
             * @param hostId 主机标识
             * @param true true
             */
            markOnline(hostId, true);
            return ReturnResult.ok(hostMapper.selectById(hostId), "硬件探测成功");
        } catch (Exception e) {
            log.warn("[server] 硬件探测失败 hostId={} address={}: {}", hostId, host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("硬件探测失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(hostId);
            }
        }
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

    /** 批量同步虚拟线程执行器（SSH 探测为阻塞 IO，虚拟线程并发最合适） */
    private static final java.util.concurrent.ExecutorService SYNC_EXECUTOR =
            java.util.concurrent.Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("host-sync-").factory());

    @Override
    public ReturnResult<java.util.Map<String, Object>> syncHosts(java.util.List<Long> hostIds) {
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
            java.util.List<java.util.Map<String, Object>> changes =
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
            java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>(8);
            summary.put("total", targets.size());
            summary.put("updated", ok.get());
            summary.put("unchanged", skipped.get());
            summary.put("failed", failed.get());
            summary.put("changes", changes);
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
    private boolean syncSingleHost(MsServerHost target, java.util.List<java.util.Map<String, Object>> changes) {
        // 保存探测前 IP 快照（用于判断是否发生变更）
        String oldPublicIp = target.getServerHostPublicIp();
        String oldPrivateIp = target.getServerHostPrivateIp();
        ReturnResult<MsServerHost> detected = detectHardware(target.getId());
        if (!detected.isOk() || detected.getData() == null) {
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
        java.util.Map<String, Object> change = new java.util.LinkedHashMap<>(4);
        change.put("hostId", latest.getId());
        change.put("hostName", latest.getServerHostName());
        change.put("publicIp", latest.getServerHostPublicIp());
        change.put("privateIp", latest.getServerHostPrivateIp());
        changes.add(change);
        return true;
    }

    /**
     * 通过 WinRM（Windows Remote Management）采集 Windows 主机硬件信息
     * <p>
     * 使用 WinRM PowerShell 批量一行输出，避免逐命令多次握手。
     * 窗口主机默认 WinRM 端口 5985(HTTP)/5986(HTTPS)，优先取 registry 中
     * 已配置的 WinRM 端口（registry key: winrm.port），缺省 5985。
     * </p>
     *
     * @param host     主机（必须已存在，systemType=WINDOWS）
     * @param credential 解密后的登录凭据
     * @return 更新后的主机（含硬件信息）
     */
    private ReturnResult<MsServerHost> detectHardwareViaWinrm(MsServerHost host, SshCredential credential) {
        // WinRM 端口：优先 host 的"登录端口"，否则回退 5985（HTTP）
        int winrmPort = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : 5985;
        try (WinRmExecClient winrm = WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(winrmPort)
                .username(credential.username())
                .password(credential.password())
                .build()) {
            winrm.connect();
            // 一次 PowerShell 输出所有硬件字段（以 | 分隔，避免多条命令）
            String ps = "('CPU='+((Get-CimInstance Win32_Processor).NumberOfCores)) "
                    + "+('|MEM='+[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1MB)) "
                    + "+('|DISK='+[math]::Round((Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | Measure-Object -Property Size -Sum).Sum/1GB)) "
                    + "+('|OS='+(Get-CimInstance Win32_OperatingSystem).Caption) "
                    + "+('|ARCH='+$env:PROCESSOR_ARCHITECTURE) "
                    + "+('|IP='+((Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*'} | Select-Object -First 1).IPAddress))";
            String output = winrm.exec().command(ps).executeAndGetOutput();
            parseWinrmOutput(output, host);
            hostMapper.updateById(host);
            markOnline(host.getId(), true);
            return ReturnResult.ok(hostMapper.selectById(host.getId()), "WinRM 硬件探测成功");
        } catch (Exception e) {
            log.warn("[server] WinRM 探测失败 hostId={} address={}: {}", host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("WinRM 硬件探测失败: " + e.getMessage());
        }
    }

    /**
     * 解析 WinRM PowerShell 输出为硬件字段（| 分隔的 k=v 段）
     *
     * @param output 原始输出
     * @param host   待回写的主机
     */
    private void parseWinrmOutput(String output, MsServerHost host) {
        if (output == null || output.isBlank()) {
            return;
        }
        for (String seg : output.split("\\|")) {
            int eq = seg.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = seg.substring(0, eq).trim().toLowerCase();
            String value = seg.substring(eq + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                switch (key) {
                    case "cpu" -> {
                        int cores = Integer.parseInt(value);
                        if (cores > 0) {
                            host.setServerHostCpuCores(cores);
                        }
                    }
                    case "mem" -> {
                        long memMb = Long.parseLong(value);
                        if (memMb > 0) {
                            host.setServerHostMemoryMb(memMb);
                        }
                    }
                    case "disk" -> {
                        long diskGb = (long) Math.floor(Double.parseDouble(value));
                        if (diskGb > 0) {
                            host.setServerHostDiskGb(diskGb);
                        }
                    }
                    case "os" -> host.setServerHostOsVersion(value);
                    case "arch" -> host.setServerHostOsArch(value);
                    case "ip" -> {
                        if (value.contains(".") || value.contains(":")) {
                            host.setServerHostPrivateIp(value);
                        }
                    }
                    default -> { /* 忽略未知字段 */ }
                }
            } catch (NumberFormatException nfe) {
                log.debug("[server] WinRM 字段解析失败 key={}: {}", key, value);
            }
        }
        if (isBlank(host.getServerHostOsVersion())) {
            host.setServerHostOsVersion("Windows");
        }
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
            if (resolved.isOk()) {
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
        // 回退：探测本机出口 IP（供公网资产参考）
        try {
            String ip = fetchViaHttp("https://ifconfig.me");
            if (isBlank(ip)) {
                ip = fetchViaHttp("https://api.ipify.org");
            }
            return isBlank(ip) ? "" : ip.trim();
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
                /**
                 * 字符串。
                 * @param in.readAllBytes( 入.读取全部bytes(
                 * @return 字符串的结果
                 */
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
     * 空串判断
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
