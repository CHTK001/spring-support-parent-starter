package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerHostIdentity;
import com.chua.starter.server.support.entity.MsServerIdentity;
import com.chua.starter.server.support.mapper.MsServerHostIdentityMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerIdentityMapper;
import com.chua.starter.server.support.model.vo.HostArchiveImportVO;
import com.chua.starter.server.support.model.vo.HostArchiveVO;
import com.chua.starter.server.support.service.HostArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主机资产归档实现。
 *
 * <p>三层数据的关联键刻意都不用自增 ID：</p>
 * <ul>
 *   <li>主机 → {@code address + port}（同一地址不同端口是两台主机，这是台账的既有语义）；</li>
 *   <li>身份 → {@code name}（{@code ms_server_identity_name} 是用户可读的唯一标识）；</li>
 *   <li>绑定 → 上述两者组合。</li>
 * </ul>
 *
 * <p><b>口令只搬密文，不重新加密</b>：导出时 {@code ms_server_identity_password} 已经是
 * {@code CredentialCipher} 的 AES hex；导入端若再走一遍 {@code encrypt}，库里的值就变成
 * 「密文的密文」，连接时必然解密失败。所以这里 {@code identityMapper.insert} 直接写原值。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostArchiveServiceImpl implements HostArchiveService {

    /**
     * 默认 SSH 端口
     */
    private static final int DEFAULT_PORT = 22;

    /**
     * 默认系统类型
     */
    private static final String DEFAULT_OS_TYPE = "LINUX";

    /**
     * 默认采集方式
     */
    private static final String DEFAULT_COLLECT_MODE = "SCHEDULED";

    /**
     * 默认远控模式
     */
    private static final String DEFAULT_REMOTE_MODE = "GUACD";

    /**
     * 默认认证类型
     */
    private static final String DEFAULT_AUTH_TYPE = "PASSWORD";

    /**
     * 新建主机时固定取值：启用 / 在线状态未知
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 在线状态未知
     */
    private static final int ONLINE_UNKNOWN = -1;

    /**
     * 迁移注意事项：随归档一起下发，前端原样展示。
     */
    private static final String ARCHIVE_NOTE =
            "口令字段为 AES 密文，只能导入到 plugin.server.support.credential-key 相同的环境；"
                    + "换密钥的环境导入后主机与绑定正常，但这些口令需重新填写。";

    private final MsServerHostMapper hostMapper;

    private final MsServerIdentityMapper identityMapper;

    private final MsServerHostIdentityMapper hostIdentityMapper;

    @Override
    public HostArchiveVO export() {
        List<MsServerHost> hosts = hostMapper.selectList(null);
        List<MsServerIdentity> identities = identityMapper.selectList(null);
        List<MsServerHostIdentity> relations = hostIdentityMapper.selectList(null);

        Map<Long, MsServerHost> hostById = new HashMap<>(16);
        List<HostArchiveVO.HostItem> hostItems = new ArrayList<>(hosts.size());
        for (MsServerHost h : hosts) {
            hostById.put(h.getId(), h);
            hostItems.add(toHostItem(h));
        }

        Map<Long, MsServerIdentity> identityById = new HashMap<>(16);
        List<HostArchiveVO.IdentityItem> identityItems = new ArrayList<>(identities.size());
        for (MsServerIdentity i : identities) {
            identityById.put(i.getId(), i);
            identityItems.add(toIdentityItem(i));
        }

        List<HostArchiveVO.BindingItem> bindings = new ArrayList<>(relations.size());
        for (MsServerHostIdentity rel : relations) {
            MsServerHost host = hostById.get(rel.getMsServerHostIdentityHostId());
            MsServerIdentity identity = identityById.get(rel.getMsServerHostIdentityIdentityId());
            if (host == null || identity == null) {
                // 台账里可能残留已删身份的关系行，导出时直接略过
                continue;
            }
            bindings.add(new HostArchiveVO.BindingItem(
                    host.getServerHostAddress(), host.getServerHostPort(),
                    identity.getMsServerIdentityName(), rel.getMsServerHostIdentitySort()));
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new HostArchiveVO(HostArchiveVO.FORMAT, HostArchiveVO.VERSION, now, ARCHIVE_NOTE,
                hostItems, identityItems, bindings);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HostArchiveImportVO importArchive(HostArchiveVO archive) {
        List<String> warnings = new ArrayList<>();
        if (archive == null) {
            throw new IllegalArgumentException("导入内容为空");
        }
        if (archive.format() != null && !HostArchiveVO.FORMAT.equals(archive.format())) {
            warnings.add("归档格式标识不是 " + HostArchiveVO.FORMAT + "，仍按兼容方式尝试导入");
        }

        // 主机：address+port 去重
        Map<String, Long> hostIdByKey = new HashMap<>(16);
        int hostsCreated = 0;
        int hostsMatched = 0;
        for (HostArchiveVO.HostItem item : nullToEmpty(archive.hosts())) {
            if (item == null || isBlank(item.address())) {
                warnings.add("跳过一条缺少地址的主机记录");
                continue;
            }
            String address = item.address().trim();
            int port = item.port() == null || item.port() <= 0 ? DEFAULT_PORT : item.port();
            String key = hostKey(address, port);
            if (hostIdByKey.containsKey(key)) {
                continue;
            }
            MsServerHost exists = hostMapper.selectOne(new LambdaQueryWrapper<MsServerHost>()
                    .eq(MsServerHost::getServerHostAddress, address)
                    .eq(MsServerHost::getServerHostPort, port)
                    .last("LIMIT 1"));
            if (exists != null) {
                hostsMatched++;
                hostIdByKey.put(key, exists.getId());
                continue;
            }
            String osType = isBlank(item.osType()) ? DEFAULT_OS_TYPE : item.osType().trim();
            MsServerHost host = new MsServerHost();
            host.setServerHostName(isBlank(item.name()) ? address : item.name().trim());
            host.setServerHostCode(item.code());
            host.setServerHostAddress(address);
            host.setServerHostPort(port);
            host.setServerHostOsType(osType);
            host.setServerHostOsArch(item.osArch());
            host.setServerHostOsVersion(item.osVersion());
            host.setServerHostCpuCores(item.cpuCores());
            host.setServerHostMemoryMb(item.memoryMb());
            host.setServerHostDiskGb(item.diskGb());
            host.setServerHostPublicIp(item.publicIp());
            host.setServerHostPrivateIp(item.privateIp());
            host.setServerHostTags(item.tags());
            host.setServerHostDescription(item.description());
            host.setServerHostAccount(item.account());
            // SSH 开关没带时按系统类型推断，与新增主机弹窗的默认值保持一致
            host.setServerHostSshEnabled(item.sshEnabled() == null
                    ? ("WINDOWS".equalsIgnoreCase(osType) ? 0 : 1)
                    : item.sshEnabled());
            host.setServerHostRemoteEnabled(item.remoteEnabled());
            host.setServerHostRemoteProtocol(item.remoteProtocol());
            host.setServerHostRemotePort(item.remotePort());
            host.setServerHostRemoteMode(isBlank(item.remoteMode()) ? DEFAULT_REMOTE_MODE : item.remoteMode());
            // 采集/检测类开关：没带就按平台默认，不能留 null 让定时任务读空指针
            host.setServerHostCollectEnabled(item.collectEnabled() == null ? 0 : item.collectEnabled());
            host.setServerHostCollectMode(isBlank(item.collectMode()) ? DEFAULT_COLLECT_MODE : item.collectMode());
            host.setServerHostCollectInterval(item.collectInterval() == null ? 3600 : item.collectInterval());
            host.setServerHostOnlineEnabled(1);
            host.setServerHostOnlineInterval(60);
            host.setServerHostDelayEnabled(0);
            host.setServerHostDelayInterval(60);
            host.setServerHostStatus(STATUS_ENABLED);
            host.setServerHostOnline(ONLINE_UNKNOWN);
            hostMapper.insert(host);
            hostsCreated++;
            hostIdByKey.put(key, host.getId());
        }

        // 身份：name 去重，口令密文原样写回
        Map<String, Long> identityIdByName = new HashMap<>(16);
        int identitiesCreated = 0;
        int identitiesMatched = 0;
        for (HostArchiveVO.IdentityItem item : nullToEmpty(archive.identities())) {
            if (item == null || isBlank(item.name()) || isBlank(item.username())) {
                warnings.add("跳过一条缺少名称或登录用户的身份记录");
                continue;
            }
            String name = item.name().trim();
            if (identityIdByName.containsKey(name)) {
                continue;
            }
            MsServerIdentity exists = identityMapper.selectOne(new LambdaQueryWrapper<MsServerIdentity>()
                    .eq(MsServerIdentity::getMsServerIdentityName, name)
                    .last("LIMIT 1"));
            if (exists != null) {
                identitiesMatched++;
                identityIdByName.put(name, exists.getId());
                continue;
            }
            MsServerIdentity identity = new MsServerIdentity();
            identity.setMsServerIdentityName(name);
            identity.setMsServerIdentityUsername(item.username().trim());
            identity.setMsServerIdentityAuthType(isBlank(item.authType()) ? DEFAULT_AUTH_TYPE : item.authType());
            // 关键：这里是密文，直接落库，不要再 encrypt 一次
            identity.setMsServerIdentityPassword(item.password());
            identity.setMsServerIdentityKeyId(item.keyId());
            identity.setMsServerIdentityRemark(item.remark());
            identity.setMsServerIdentityEmailSource(item.emailSource());
            identity.setMsServerIdentityProvider(item.provider());
            identity.setMsServerIdentityImapHost(item.imapHost());
            identity.setMsServerIdentityImapPort(item.imapPort());
            identityMapper.insert(identity);
            identitiesCreated++;
            identityIdByName.put(name, identity.getId());
        }

        // 绑定：主机 address+port + 身份 name
        int bindingsCreated = 0;
        int bindingsSkipped = 0;
        for (HostArchiveVO.BindingItem item : nullToEmpty(archive.bindings())) {
            if (item == null || isBlank(item.hostAddress()) || isBlank(item.identityName())) {
                warnings.add("跳过一条缺少主机地址或身份名称的绑定");
                bindingsSkipped++;
                continue;
            }
            int port = item.hostPort() == null || item.hostPort() <= 0 ? DEFAULT_PORT : item.hostPort();
            Long hostId = hostIdByKey.get(hostKey(item.hostAddress().trim(), port));
            if (hostId == null) {
                warnings.add("绑定未导入：归档里没有主机 " + item.hostAddress() + ":" + port);
                bindingsSkipped++;
                continue;
            }
            Long identityId = identityIdByName.get(item.identityName().trim());
            if (identityId == null) {
                warnings.add("绑定未导入：归档里没有身份「" + item.identityName() + "」");
                bindingsSkipped++;
                continue;
            }
            Long bound = hostIdentityMapper.selectCount(new LambdaQueryWrapper<MsServerHostIdentity>()
                    .eq(MsServerHostIdentity::getMsServerHostIdentityHostId, hostId)
                    .eq(MsServerHostIdentity::getMsServerHostIdentityIdentityId, identityId));
            if (bound != null && bound > 0) {
                bindingsSkipped++;
                continue;
            }
            MsServerHostIdentity relation = new MsServerHostIdentity();
            relation.setMsServerHostIdentityHostId(hostId);
            relation.setMsServerHostIdentityIdentityId(identityId);
            relation.setMsServerHostIdentitySort(item.sort() == null ? 1 : item.sort());
            hostIdentityMapper.insert(relation);
            bindingsCreated++;
        }

        log.info("[server] 主机归档导入完成 主机+{}/命中{} 身份+{}/命中{} 绑定+{}/跳过{}",
                hostsCreated, hostsMatched, identitiesCreated, identitiesMatched,
                bindingsCreated, bindingsSkipped);
        return new HostArchiveImportVO(hostsCreated, hostsMatched, identitiesCreated,
                identitiesMatched, bindingsCreated, bindingsSkipped, warnings);
    }

    /**
     * 台账 → 归档主机条目。
     *
     * @param h 台账行
     * @return 归档条目
     */
    private HostArchiveVO.HostItem toHostItem(MsServerHost h) {
        return new HostArchiveVO.HostItem(
                h.getServerHostName(), h.getServerHostCode(), h.getServerHostAddress(),
                h.getServerHostPort(), h.getServerHostOsType(), h.getServerHostOsArch(),
                h.getServerHostOsVersion(), h.getServerHostCpuCores(), h.getServerHostMemoryMb(),
                h.getServerHostDiskGb(), h.getServerHostPublicIp(), h.getServerHostPrivateIp(),
                h.getServerHostSshEnabled(), h.getServerHostCollectMode(), h.getServerHostCollectEnabled(),
                h.getServerHostCollectInterval(), h.getServerHostRemoteEnabled(),
                h.getServerHostRemoteProtocol(), h.getServerHostRemotePort(), h.getServerHostRemoteMode(),
                h.getServerHostAccount(), h.getServerHostTags(), h.getServerHostDescription());
    }

    /**
     * 身份 → 归档身份条目（包含口令密文，故不走 list 的脱敏路径）。
     *
     * @param i 身份行
     * @return 归档条目
     */
    private HostArchiveVO.IdentityItem toIdentityItem(MsServerIdentity i) {
        return new HostArchiveVO.IdentityItem(
                i.getMsServerIdentityName(), i.getMsServerIdentityAuthType(),
                i.getMsServerIdentityUsername(), i.getMsServerIdentityPassword(),
                i.getMsServerIdentityKeyId(), i.getMsServerIdentityRemark(),
                i.getMsServerIdentityEmailSource(), i.getMsServerIdentityProvider(),
                i.getMsServerIdentityImapHost(), i.getMsServerIdentityImapPort());
    }

    /**
     * 主机关联键。
     *
     * @param address 地址
     * @param port    端口
     * @return address:port
     */
    private String hostKey(String address, Integer port) {
        return address + ":" + (port == null ? DEFAULT_PORT : port);
    }

    /**
     * null 集合转空列表。
     *
     * @param list 原列表
     * @param <T>  元素类型
     * @return 非空列表
     */
    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * 空白判断。
     *
     * @param value 文本
     * @return true 表示 null / 空串 / 全空白
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
