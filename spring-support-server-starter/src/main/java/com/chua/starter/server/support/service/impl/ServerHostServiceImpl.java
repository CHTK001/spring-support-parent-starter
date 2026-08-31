package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

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

    /** 探针标识字节长度（32 个十六进制字符） */
    private static final int AGENT_KEY_BYTES = 16;

    /** Linux 内核信息命令 */
    private static final String CMD_UNAME = "uname -sr";

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    /** 身份 Mapper */
    private final MsServerIdentityMapper identityMapper;

    /** 密钥 Mapper */
    private final MsServerKeyMapper keyMapper;

    /** 主机身份关联 Mapper */
    private final MsServerHostIdentityMapper hostIdentityMapper;

    /** 凭据加解密器 */
    private final CredentialCipher cipher;

    /** 探针标识随机源 */
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        // 探针标识服务端生成，保证全局唯一
        host.setServerHostAgentKey(generateAgentKey());
        hostMapper.insert(host);
        return ReturnResult.ok(hostMapper.selectById(host.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        hostMapper.updateById(exists);
        return ReturnResult.ok(hostMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        try (var handle = SshClients.open(host.getServerHostAddress(),
                host.getServerHostPort(), resolved.getData())) {
            handle.client().connect();
            // 登录+认证成功即为连通; banner 仅尽力获取(exec 通道不可用时不影响结论)
            String banner = "connected";
            if ("LINUX".equalsIgnoreCase(host.getServerHostOsType())) {
                try {
                    String uname = handle.client().exec().command(CMD_UNAME).executeAndGetOutput();
                    if (!isBlank(uname)) {
                        banner = uname;
                    }
                } catch (Exception execError) {
                    // 打印完整堆栈便于定位 exec 通道问题(不影响连通性结论)
                    log.info("[server] exec 通道不可用(不影响连通性结论)", execError);
                }
            }
            markOnline(host.getId(), true);
            return ReturnResult.ok(banner, "测试成功");
        } catch (Exception e) {
            markOnline(host.getId(), false);
            log.warn("[server] 连通性测试失败 hostId={} address={}: {}", id, host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("连接失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    public ReturnResult<MsServerHost> detectHardware(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        ReturnResult<SshCredential> resolved = resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        try (var handle = SshClients.open(host.getServerHostAddress(),
                host.getServerHostPort(), resolved.getData())) {
            handle.client().connect();
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
            hostMapper.updateById(host);
            markOnline(hostId, true);
            return ReturnResult.ok(hostMapper.selectById(hostId), "硬件探测成功");
        } catch (Exception e) {
            log.warn("[server] 硬件探测失败 hostId={} address={}: {}", hostId, host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("硬件探测失败: " + e.getMessage());
        }
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
     * @param hostId 主机 ID
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
     * @param hostId 主机 ID
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
