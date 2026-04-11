package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.utils.DigestUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.mapper.ServerHostMapper;
import com.chua.starter.server.support.service.ServerHostService;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerHostServiceImpl extends ServiceImpl<ServerHostMapper, ServerHost> implements ServerHostService {

    private final ServerAuditExecutor auditExecutor;

    /**
     * 查询服务器列表，并按最近更新时间倒序返回。
     */
    @Override
    public List<ServerHost> listHosts(String keyword, String serverType, Boolean enabled) {
        return list(Wrappers.<ServerHost>lambdaQuery()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(ServerHost::getServerName, keyword)
                        .or()
                        .like(ServerHost::getServerCode, keyword)
                        .or()
                        .like(ServerHost::getHost, keyword)
                        .or()
                        .like(ServerHost::getTags, keyword))
                .eq(StringUtils.hasText(serverType), ServerHost::getServerType, serverType)
                .eq(enabled != null, ServerHost::getEnabled, enabled)
                .orderByDesc(ServerHost::getUpdateTime, ServerHost::getCreateTime, ServerHost::getServerId));
    }

    /**
     * 统计服务器总数、启用数以及本机/远程分布。
     */
    @Override
    public Map<String, Object> getSummary() {
        long total = count();
        long enabled = count(Wrappers.<ServerHost>lambdaQuery().eq(ServerHost::getEnabled, Boolean.TRUE));
        long local = count(Wrappers.<ServerHost>lambdaQuery().eq(ServerHost::getServerType, "LOCAL"));
        long remote = count(Wrappers.<ServerHost>lambdaQuery().ne(ServerHost::getServerType, "LOCAL"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("enabled", enabled);
        summary.put("disabled", Math.max(total - enabled, 0));
        summary.put("local", local);
        summary.put("remote", remote);
        return summary;
    }

    /**
     * 按主键读取服务器定义。
     */
    @Override
    public ServerHost getHost(Integer id) {
        return getById(id);
    }

    /**
     * 保存服务器配置，并统一补齐默认值与系统审计上下文。
     */
    @Override
    public ServerHost saveHost(ServerHost host) {
        normalize(host);
        if (host.getServerId() == null) {
            auditExecutor.run(() -> save(host));
        } else {
            auditExecutor.run(() -> updateById(host));
        }
        return getById(host.getServerId());
    }

    /**
     * 启用或停用指定服务器。
     */
    @Override
    public ServerHost updateEnabled(Integer id, Boolean enabled) {
        ServerHost host = requireHost(id);
        host.setEnabled(Boolean.TRUE.equals(enabled));
        auditExecutor.run(() -> updateById(host));
        return getById(id);
    }

    /**
     * 删除指定服务器。
     */
    @Override
    public void deleteHost(Integer id) {
        auditExecutor.run(() -> removeById(id));
    }

    /**
     * 读取服务器并在缺失时直接抛出业务异常。
     */
    private ServerHost requireHost(Integer id) {
        ServerHost host = getById(id);
        if (host == null) {
            throw new IllegalStateException("服务器不存在");
        }
        return host;
    }

    /**
     * 对前端提交的服务器配置做统一标准化，补齐协议默认值。
     */
    private void normalize(ServerHost host) {
        host.setServerName(trim(host.getServerName()));
        host.setServerCode(resolveCode(host));
        host.setServerType(resolveServerType(host.getServerType()));
        host.setOsType(trim(host.getOsType()));
        host.setArchitecture(trim(host.getArchitecture()));
        host.setHost(resolveHost(host));
        host.setPort(resolvePort(host));
        host.setUsername(trim(host.getUsername()));
        host.setBaseDirectory(resolveBaseDirectory(host));
        host.setTags(resolveTags(host));
        host.setDescription(trim(host.getDescription()));
        host.setMetadataJson(trim(host.getMetadataJson()));
        if (host.getEnabled() == null) {
            host.setEnabled(Boolean.TRUE);
        }
    }

    /**
     * 根据主机地址与账号生成稳定编码，并阻止重复主机账号组合。
     */
    private String resolveCode(ServerHost host) {
        String hostValue = resolveHost(host);
        String usernameValue = trim(host.getUsername());
        if (!StringUtils.hasText(usernameValue)) {
            usernameValue = "LOCAL".equalsIgnoreCase(resolveServerType(host.getServerType())) ? "local" : "anonymous";
        }
        String candidate = DigestUtils.md5Hex(
                (StringUtils.hasText(hostValue) ? hostValue.trim().toLowerCase() : "")
                        + usernameValue.trim().toLowerCase());
        if (existsOtherCode(candidate, host.getServerId())) {
            throw new IllegalStateException("相同主机与账号的服务器已存在");
        }
        return candidate;
    }

    /**
     * 规范化服务器类型，缺省按本机处理。
     */
    private String resolveServerType(String serverType) {
        return StringUtils.hasText(serverType) ? serverType.trim().toUpperCase() : "LOCAL";
    }

    /**
     * 本机默认回落到 127.0.0.1，远程主机保留用户输入。
     */
    private String resolveHost(ServerHost host) {
        if ("LOCAL".equalsIgnoreCase(host.getServerType())) {
            return StringUtils.hasText(host.getHost()) ? host.getHost().trim() : "127.0.0.1";
        }
        return trim(host.getHost());
    }

    /**
     * 不同接入协议补齐默认端口。
     */
    private Integer resolvePort(ServerHost host) {
        if (host.getPort() != null) {
            return host.getPort();
        }
        return switch (resolveServerType(host.getServerType())) {
            case "SSH" -> 22;
            case "WINRM" -> 5985;
            default -> 0;
        };
    }

    /**
     * 自动推断基础目录，避免本机与远程新增时必须手工填写。
     */
    private String resolveBaseDirectory(ServerHost host) {
        String value = trim(host.getBaseDirectory());
        if (StringUtils.hasText(value)) {
            return value;
        }
        String osType = trim(host.getOsType());
        if (StringUtils.hasText(osType) && osType.toLowerCase().contains("win")) {
            return "C:/";
        }
        return "/";
    }

    /**
     * 去掉空白并把空串转成 null。
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 兼容标签字符串和列表两种输入，统一落成逗号分隔文本。
     */
    private String resolveTags(ServerHost host) {
        List<String> tags = host.getTagsList();
        if (tags == null || tags.isEmpty()) {
            String text = trim(host.getTags());
            if (!StringUtils.hasText(text)) {
                return null;
            }
            tags = Arrays.stream(text.split("[,，]"))
                    .map(this::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            tags = tags.stream()
                    .map(this::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        }
        host.setTagsList(tags);
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    /**
     * 检查编码是否已被其他服务器占用。
     */
    private boolean existsOtherCode(String code, Integer currentId) {
        return count(Wrappers.<ServerHost>lambdaQuery()
                .eq(ServerHost::getServerCode, code)
                .ne(currentId != null, ServerHost::getServerId, currentId)) > 0;
    }
}
