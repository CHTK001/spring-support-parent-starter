package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SshClient;
import com.chua.ssh.support.client.SshTunnel;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceTunnel;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceTunnelMapper;
import com.chua.starter.datasourcehub.support.model.DataSourceTunnelVO;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceTunnelDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import com.chua.starter.datasourcehub.support.support.DatasourceSecrets;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源 SSH 隧道服务实现。
 *
 * <p>隧道按数据源维度缓存：{@link #openIfEnabled(Long)} 命中已打开的隧道时直接复用，
 * 避免每测一次连通性就重连一次 SSH。隧道随应用关闭而释放（{@link #closeAll()}）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceTunnelServiceImpl implements DatasourceTunnelService {

    /**
     * 隧道配置映射器
     */
    private final MsDatasourceTunnelMapper tunnelMapper;

    /**
     * 已打开的隧道：datasourceId -&gt; 持有者。
     *
     * <p>用 ConcurrentHashMap 而非缓存连接池：隧道是长连接且数量等于配置了隧道的
     * 数据源数，天然是「少而长」，不需要 LRU 淘汰。</p>
     */
    private final Map<Long, TunnelHolder> openTunnels = new ConcurrentHashMap<>();

    /**
     * 私钥内容落盘的临时文件前缀（相对 {@code java.io.tmpdir}）。
     */
    private static final String TEMP_KEY_PREFIX = "datasource-tunnel-key-";

    @Override
    public TunnelEndpoint openIfEnabled(Long datasourceId) {
        MsDatasourceTunnel config = findConfig(datasourceId);
        if (config == null || !Integer.valueOf(1).equals(config.getTunnelStatus())) {
            return null;
        }
        TunnelHolder existing = openTunnels.get(datasourceId);
        if (existing != null && existing.alive()) {
            return new TunnelEndpoint(existing.bindAddress, existing.localPort);
        }
        return doOpen(datasourceId, config);
    }

    /**
     * 真正建立隧道连接。
     *
     * @param datasourceId 数据源 ID
     * @param config       隧道配置
     * @return 隧道本地端点；失败返回 null
     */
    private TunnelEndpoint doOpen(Long datasourceId, MsDatasourceTunnel config) {
        Path tempKey = null;
        try {
            int sshPort = config.getSshPort() == null || config.getSshPort() <= 0
                    ? DatasourceHubConstants.SSH_DEFAULT_PORT : config.getSshPort();
            var builder = SshClient.builder()
                    .host(config.getSshHost())
                    .port(sshPort)
                    .username(config.getSshUsername());
            if (DatasourceHubConstants.SSH_AUTH_KEY.equalsIgnoreCase(trim(config.getSshAuthType()))) {
                tempKey = materializePrivateKey(config.getSshPrivateKey());
                builder.privateKey(tempKey.toAbsolutePath().toString());
            } else {
                builder.password(DatasourceSecrets.decode(config.getSshPassword()));
            }
            SshClient client = builder.build().connect();

            int localPort = config.getLocalPort() == null ? 0 : config.getLocalPort();
            String bindAddress = isBlank(config.getBindAddress())
                    ? DatasourceHubConstants.TUNNEL_DEFAULT_BIND : config.getBindAddress().trim();
            var definition = buildDefinition(config, localPort);
            SshTunnel tunnel = new SshTunnel(client, definition, bindAddress);
            int actualPort = tunnel.open();
            if (actualPort <= 0) {
                client.close();
                throw new IllegalStateException("隧道未返回本地端口");
            }
            openTunnels.put(datasourceId, new TunnelHolder(client, tunnel, bindAddress, actualPort));
            log.info("数据源 {} SSH 隧道已打开: {}:{} -> {}:{}", datasourceId, bindAddress, actualPort,
                    config.getRemoteHost(), config.getRemotePort());
            return new TunnelEndpoint(bindAddress, actualPort);
        } catch (Exception e) {
            log.warn("数据源 {} SSH 隧道打开失败: {}", datasourceId, e.getMessage());
            closeQuietly(datasourceId);
            return null;
        } finally {
            // 私钥以内容形式配置时需落临时文件供 MINA SSHD 读取，连接建立后即可删除
            deleteQuietly(tempKey);
        }
    }

    /**
     * 按配置构造隧道定义。
     *
     * @param config    隧道配置
     * @param localPort 本地端口，0 表示自动分配
     * @return 隧道定义
     */
    private SshClient.TunnelDefinition buildDefinition(MsDatasourceTunnel config, int localPort) {
        String type = trim(config.getTunnelType()).toUpperCase();
        String remoteHost = config.getRemoteHost() == null ? "" : config.getRemoteHost().trim();
        int remotePort = config.getRemotePort() == null ? 0 : config.getRemotePort();
        return switch (type) {
            case DatasourceHubConstants.TUNNEL_REMOTE ->
                    SshClient.TunnelDefinition.remote(remotePort, remoteHost, localPort);
            case DatasourceHubConstants.TUNNEL_DYNAMIC -> SshClient.TunnelDefinition.dynamic(localPort);
            default -> SshClient.TunnelDefinition.local(localPort, remoteHost, remotePort);
        };
    }

    @Override
    public ReturnResult<DataSourceTunnelVO> get(Long datasourceId) {
        if (datasourceId == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        return ReturnResult.ok(toVO(findConfig(datasourceId)));
    }

    @Override
    public ReturnResult<DataSourceTunnelVO> save(Long datasourceId, DataSourceTunnelDTO data) {
        if (datasourceId == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        String error = validate(data);
        if (error != null) {
            return ReturnResult.error(error);
        }
        MsDatasourceTunnel config = findConfig(datasourceId);
        boolean creating = config == null;
        if (creating) {
            config = new MsDatasourceTunnel();
            config.setDatasourceId(datasourceId);
            config.setCreateTime(LocalDateTime.now());
        }
        copy(data, config);
        config.setUpdateTime(LocalDateTime.now());
        if (creating) {
            tunnelMapper.insert(config);
        } else {
            tunnelMapper.updateById(config);
        }
        // 配置变了，旧隧道必须重建，否则会继续用旧端口/旧目标
        closeQuietly(datasourceId);
        return ReturnResult.ok(toVO(config));
    }

    @Override
    public ReturnResult<Boolean> delete(Long datasourceId) {
        if (datasourceId == null) {
            return ReturnResult.error("数据源 ID 不能为空");
        }
        closeQuietly(datasourceId);
        var wrapper = new LambdaQueryWrapper<MsDatasourceTunnel>()
                .eq(MsDatasourceTunnel::getDatasourceId, datasourceId);
        tunnelMapper.delete(wrapper);
        return ReturnResult.ok(true);
    }

    /**
     * 关闭全部已打开的隧道，应用停止时调用。
     */
    @PreDestroy
    public void closeAll() {
        openTunnels.keySet().forEach(this::closeQuietly);
        openTunnels.clear();
    }

    /**
     * 关闭并移除某数据源的隧道。
     *
     * @param datasourceId 数据源 ID
     */
    private void closeQuietly(Long datasourceId) {
        TunnelHolder holder = openTunnels.remove(datasourceId);
        if (holder == null) {
            return;
        }
        try {
            holder.tunnel().close();
        } catch (Exception e) {
            log.debug("关闭隧道失败: {}", e.getMessage());
        }
        try {
            holder.client().close();
        } catch (Exception e) {
            log.debug("关闭 SSH 连接失败: {}", e.getMessage());
        }
    }

    /**
     * 查询数据源的隧道配置。
     *
     * @param datasourceId 数据源 ID
     * @return 隧道配置；未配置时 null
     */
    private MsDatasourceTunnel findConfig(Long datasourceId) {
        if (datasourceId == null) {
            return null;
        }
        var wrapper = new LambdaQueryWrapper<MsDatasourceTunnel>()
                .eq(MsDatasourceTunnel::getDatasourceId, datasourceId);
        return tunnelMapper.selectOne(wrapper);
    }

    /**
     * 校验隧道配置。
     *
     * @param data 隧道配置
     * @return 错误提示，通过时返回 null
     */
    private String validate(DataSourceTunnelDTO data) {
        if (data == null) {
            return "隧道配置不能为空";
        }
        if (isBlank(data.sshHost())) {
            return "SSH 服务器地址不能为空";
        }
        if (isBlank(data.sshUsername())) {
            return "SSH 用户名不能为空";
        }
        String type = trim(data.type()).toUpperCase();
        if (isBlank(type) || !DatasourceHubConstants.TUNNEL_TYPES.contains(type)) {
            return "隧道类型必须是 LOCAL/REMOTE/DYNAMIC 之一";
        }
        if (!DatasourceHubConstants.TUNNEL_DYNAMIC.equals(type)) {
            if (isBlank(data.remoteHost())) {
                return "远端目标主机不能为空";
            }
            if (data.remotePort() == null || data.remotePort() <= 0) {
                return "远端目标端口必须大于 0";
            }
        }
        if (DatasourceHubConstants.SSH_AUTH_KEY.equalsIgnoreCase(trim(data.sshAuthType()))
                && isBlank(data.sshPrivateKey())) {
            return "私钥认证时私钥不能为空（可填 PEM 文本或私钥文件路径）";
        }
        return null;
    }

    /**
     * 把请求字段拷贝到实体。
     *
     * @param data   请求
     * @param config 实体
     */
    private void copy(DataSourceTunnelDTO data, MsDatasourceTunnel config) {
        config.setTunnelName(nullToEmpty(data.name()));
        config.setTunnelType(trim(data.type()).toUpperCase());
        config.setSshHost(data.sshHost().trim());
        config.setSshPort(data.sshPort());
        config.setSshUsername(data.sshUsername().trim());
        config.setSshAuthType(isBlank(data.sshAuthType())
                ? DatasourceHubConstants.SSH_AUTH_PASSWORD : trim(data.sshAuthType()).toUpperCase());
        config.setSshPassword(mergeSecret(data.sshPassword(), config.getSshPassword()));
        config.setSshPrivateKey(nullToEmpty(data.sshPrivateKey()));
        config.setSshPassphrase(mergeSecret(data.sshPassphrase(), config.getSshPassphrase()));
        config.setLocalPort(data.localPort());
        config.setRemoteHost(nullToEmpty(data.remoteHost()));
        config.setRemotePort(data.remotePort());
        config.setBindAddress(isBlank(data.bindAddress())
                ? DatasourceHubConstants.TUNNEL_DEFAULT_BIND : data.bindAddress().trim());
        config.setTunnelStatus(data.status() == null ? 0 : data.status());
    }

    /**
     * 私钥内容落临时文件。
     *
     * <p>MINA SSHD 的客户端构造只接受私钥文件路径。用户若直接粘贴 PEM 文本，
     * 这里落一个临时文件供其读取；若是路径则原样返回、不复制。</p>
     *
     * @param privateKey 私钥 PEM 文本或文件路径
     * @return 可供读取的私钥文件路径
     * @throws IOException 落盘失败
     */
    private Path materializePrivateKey(String privateKey) throws IOException {
        String value = nullToEmpty(privateKey);
        if (!value.contains("BEGIN")) {
            return Path.of(value);
        }
        Path temp = Files.createTempFile(TEMP_KEY_PREFIX, ".key");
        Files.writeString(temp, value, StandardCharsets.UTF_8);
        return temp;
    }

    /**
     * 删除临时文件。
     *
     * @param path 文件路径，可为 null
     */
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除临时私钥文件失败: {}", e.getMessage());
        }
    }

    /**
     * 实体转视图。
     *
     * @param row 实体
     * @return 视图；实体为 null 时返回 null
     */
    private DataSourceTunnelVO toVO(MsDatasourceTunnel row) {
        if (row == null) {
            return null;
        }
        return new DataSourceTunnelVO(row.getId(), row.getDatasourceId(), row.getTunnelName(),
                row.getTunnelType(), row.getSshHost(), row.getSshPort(), row.getSshUsername(),
                row.getSshAuthType(), row.getSshPrivateKey(), row.getLocalPort(), row.getRemoteHost(),
                row.getRemotePort(), row.getBindAddress(), row.getTunnelStatus(),
                row.getCreateTime() == null ? null : row.getCreateTime().toString(),
                row.getUpdateTime() == null ? null : row.getUpdateTime().toString());
    }

    /**
     * 去除首尾空白，null 安全。
     *
     * @param text 文本
     * @return 结果，null 时为空串
     */
    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * null 转空串。
     *
     * @param text 文本
     * @return 结果
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    /**
     * 是否为空白串。
     *
     * @param text 文本
     * @return true 表示空白
     */
    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * 口令类字段的合并：请求里为空则沿用库里已存的值。
     *
     * <p>查询接口不回显 SSH 口令与私钥口令（避免明文外泄），前端自然回显不出来；
     * 若保存时把空值直接覆盖上去，用户只是改了隧道名就会把已配好的凭据悄悄抹空。
     * 这里按「空 = 不改」处理，与 {@code sshAuthType} / {@code bindAddress}
     * 的空值兜底同一条口径。</p>
     *
     * @param incoming 请求值
     * @param stored   库里已有值
     * @return 入库值
     */
    private static String mergeSecret(String incoming, String stored) {
        return isBlank(incoming) ? nullToEmpty(stored) : incoming;
    }

    /**
     * 已打开隧道的持有者。
     *
     * @param client      SSH 客户端
     * @param tunnel      隧道
     * @param bindAddress 本地绑定地址
     * @param localPort   实际本地端口
     */
    private record TunnelHolder(SshClient client, SshTunnel tunnel, String bindAddress, int localPort) {

        /**
         * 隧道是否仍存活。
         *
         * @return true 表示可复用
         */
        boolean alive() {
            return client.isConnected();
        }
    }
}
