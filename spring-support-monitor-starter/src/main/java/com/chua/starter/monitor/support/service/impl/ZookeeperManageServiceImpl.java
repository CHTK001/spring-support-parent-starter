package com.chua.starter.monitor.support.service.impl;

import com.chua.starter.monitor.support.middleware.MiddlewareNamespace;
import com.chua.starter.monitor.support.middleware.ZookeeperClient;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.model.ZkChildNodeVO;
import com.chua.starter.monitor.support.model.ZkClusterNodeVO;
import com.chua.starter.monitor.support.model.ZkNodeAclVO;
import com.chua.starter.monitor.support.model.ZkNodeDetailVO;
import com.chua.starter.monitor.support.model.ZkNodeVO;
import com.chua.starter.monitor.support.model.ZookeeperTestVO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.model.dto.ZkNodeDTO;
import com.chua.starter.monitor.support.service.ZookeeperManageService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.springframework.stereotype.Service;

/**
 * ZooKeeper 管理实现：全部读写都落到真实节点，失败只回原因，不造演示数据。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZookeeperManageServiceImpl implements ZookeeperManageService {

    /**
     * ZooKeeper 客户端
     */
    private final ZookeeperClient client;

    @Override
    public ZookeeperTestVO test(MiddlewareTestDTO request) {
        try {
            return client.execute(testTarget(request), curator ->
                    new ZookeeperTestVO(true, ZookeeperClient.serverVersion(curator),
                            ZookeeperClient.sessionId(curator), "连接成功"));
        } catch (IllegalStateException e) {
            return new ZookeeperTestVO(false, null, null, e.getMessage());
        }
    }

    @Override
    public List<ZkNodeVO> tree(Long datasourceId, String path) {
        String parent = ZookeeperClient.requirePath(path == null || path.isBlank() ? "/" : path);
        return client.execute(datasourceId, curator -> {
            List<String> names = ZookeeperClient.listChildren(curator, parent);
            List<ZkNodeVO> nodes = new ArrayList<>(names.size());
            for (String name : names) {
                String child = ZookeeperClient.childOf(parent, name);
                Stat stat = curator.checkExists().forPath(child);
                nodes.add(new ZkNodeVO(child, name, isEphemeral(stat), stat == null ? 0 : stat.getNumChildren()));
            }
            return nodes;
        });
    }

    @Override
    public ZkNodeDetailVO node(String path, Long datasourceId) {
        String node = ZookeeperClient.requirePath(path);
        return client.execute(datasourceId, curator -> {
            Stat stat = curator.checkExists().forPath(node);
            if (stat == null) {
                throw new KeeperException.NoNodeException(node);
            }
            String data = ZookeeperClient.decode(curator.getData().forPath(node));
            return new ZkNodeDetailVO(node, isEphemeral(stat),
                    MiddlewareNamespace.format(stat.getCtime()), MiddlewareNamespace.format(stat.getMtime()),
                    stat.getVersion(), stat.getNumChildren(), stat.getDataLength(), data,
                    stat.getCzxid(), stat.getMzxid(), stat.getPzxid(),
                    stat.getCversion(), stat.getAversion(), stat.getEphemeralOwner());
        });
    }

    @Override
    public List<ZkChildNodeVO> children(String path, Long datasourceId) {
        String parent = ZookeeperClient.requirePath(path == null || path.isBlank() ? "/" : path);
        return client.execute(datasourceId, curator -> {
            List<String> names = ZookeeperClient.listChildren(curator, parent);
            List<ZkChildNodeVO> children = new ArrayList<>(names.size());
            for (String name : names) {
                String child = ZookeeperClient.childOf(parent, name);
                Stat stat = curator.checkExists().forPath(child);
                children.add(new ZkChildNodeVO(name, child, isEphemeral(stat), readData(curator, child)));
            }
            return children;
        });
    }

    @Override
    public boolean create(ZkNodeDTO request) {
        String node = ZookeeperClient.requirePath(request == null ? null : request.path());
        byte[] data = bytes(request == null ? null : request.data());
        CreateMode mode = ZookeeperClient.modeOf(request == null ? null : request.ephemeral());
        return client.execute(request == null ? null : request.datasourceId(), curator -> {
            curator.create().creatingParentsIfNeeded().withMode(mode).forPath(node, data);
            return true;
        });
    }

    @Override
    public boolean update(ZkNodeDTO request) {
        String node = ZookeeperClient.requirePath(request == null ? null : request.path());
        byte[] data = bytes(request == null ? null : request.data());
        return client.execute(request == null ? null : request.datasourceId(), curator -> {
            if (curator.checkExists().forPath(node) == null) {
                throw new KeeperException.NoNodeException(node);
            }
            curator.setData().forPath(node, data);
            return true;
        });
    }

    @Override
    public boolean delete(String path, Long datasourceId, boolean recursive) {
        String node = ZookeeperClient.requirePath(path);
        return client.execute(datasourceId, curator -> {
            if (recursive) {
                // 递归删除：Curator 从叶子向根逐个 delete，保证中间节点不空
                curator.delete().guaranteed().deletingChildrenIfNeeded().forPath(node);
            } else {
                curator.delete().forPath(node);
            }
            return true;
        });
    }

    @Override
    public List<ZkClusterNodeVO> cluster(Long datasourceId) {
        List<String> hosts = client.clusterHosts(datasourceId);
        List<ZkClusterNodeVO> result = new ArrayList<>(hosts.size());
        for (String host : hosts) {
            String body = ZookeeperClient.fourLetter(host, "mntr");
            result.add(parseMntr(host, body));
        }
        return result;
    }

    @Override
    public List<ZkNodeAclVO> acl(String path, Long datasourceId) {
        String node = ZookeeperClient.requirePath(path);
        return client.execute(datasourceId, curator -> {
            Stat stat = curator.checkExists().forPath(node);
            if (stat == null) {
                throw new KeeperException.NoNodeException(node);
            }
            List<ACL> acls = curator.getACL().forPath(node);
            List<ZkNodeAclVO> result = new ArrayList<>(acls == null ? 0 : acls.size());
            if (acls != null) {
                for (ACL acl : acls) {
                    result.add(new ZkNodeAclVO(
                            acl.getId().getScheme(), acl.getId().getId(),
                            acl.getPerms(), permsText(acl.getPerms())));
                }
            }
            return result;
        });
    }

    /**
     * 解析 {@code mntr} 回包为单节点指标。
     *
     * <p>回包为 {@code key\tvalue} 每行一条；四字命令未放开时回包为空或报错串，
     * 此时所有指标留空并把原因放进 {@code error} 字段。</p>
     *
     * @param host 节点地址
     * @param body mntr 回包全文
     * @return 节点指标
     */
    private static ZkClusterNodeVO parseMntr(String host, String body) {
        Map<String, String> kv = new HashMap<>();
        if (body != null && !body.isBlank()) {
            for (String line : body.split("\\r?\\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                kv.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
            }
        }
        if (kv.isEmpty()) {
            String reason = body == null || body.isBlank() ? "四字命令 mntr 未放开或不可达" : body.trim();
            return new ZkClusterNodeVO(host, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, reason);
        }
        return new ZkClusterNodeVO(host,
                kv.get("zk_version"),
                longOrNull(kv.get("zk_avg_latency")), longOrNull(kv.get("zk_min_latency")),
                longOrNull(kv.get("zk_max_latency")),
                longOrNull(kv.get("zk_packets_received")), longOrNull(kv.get("zk_packets_sent")),
                longOrNull(kv.get("zk_num_alive_connections")), longOrNull(kv.get("zk_outstanding_requests")),
                longOrNull(kv.get("zk_znode_count")), longOrNull(kv.get("zk_watch_count")),
                longOrNull(kv.get("zk_approximate_data_size")), longOrNull(kv.get("zk_open_file_descriptor_count")),
                emptyToNull(kv.get("zk_server_state")), emptyToNull(kv.get("zk_peer_state")), null);
    }

    /**
     * 把 ACL 权限位掩码解析为可读组合。
     *
     * <p>位定义：1=READ, 2=WRITE, 4=CREATE, 8=DELETE, 16=ADMIN；31 为 ALL。</p>
     *
     * @param perms 权限位掩码
     * @return 可读权限组合
     */
    private static String permsText(int perms) {
        if (perms == 31) {
            return "ALL";
        }
        List<String> parts = new ArrayList<>(5);
        if ((perms & 1) != 0) {
            parts.add("READ");
        }
        if ((perms & 2) != 0) {
            parts.add("WRITE");
        }
        if ((perms & 4) != 0) {
            parts.add("CREATE");
        }
        if ((perms & 8) != 0) {
            parts.add("DELETE");
        }
        if ((perms & 16) != 0) {
            parts.add("ADMIN");
        }
        return parts.isEmpty() ? "NONE" : String.join("|", parts);
    }

    /**
     * 宽松取长整数。
     *
     * @param value 原值
     * @return 长整数，非数字或空为 null
     */
    private static Long longOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 空串转 null。
     *
     * @param value 原值
     * @return 非空原值或 null
     */
    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 解析测试目标：给了地址就即席探测，否则按登记的数据源探测。
     *
     * @param request 测试载荷，可为空
     * @return 数据源配置
     */
    private DataSourceVO testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.requireDatasource(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.requireDatasource(request.datasourceId());
        }
        return new DataSourceVO(null, "ad-hoc", ZookeeperClient.DS_TYPE, request.host().trim(),
                request.port(), request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null);
    }

    /**
     * 是否临时节点（ ZooKeeper 以 ephemeralOwner 非 0 判定）。
     *
     * @param stat 节点状态，可为 null
     * @return 临时节点为真
     */
    private static boolean isEphemeral(Stat stat) {
        return stat != null && stat.getEphemeralOwner() != 0L;
    }

    /**
     * 尽力读取子节点数据，节点在列举后被删除时给占位而不是整页失败。
     *
     * @param curator 客户端
     * @param path    节点路径
     * @return 可展示文本
     */
    private static String readData(CuratorFramework curator, String path) {
        try {
            return ZookeeperClient.decode(curator.getData().forPath(path));
        } catch (Exception e) {
            return "<读取失败: " + e.getClass().getSimpleName() + ">";
        }
    }

    /**
     * 节点数据编码。
     *
     * @param data 原始文本
     * @return 字节数组，空文本按零长度处理以免 Curator 传 null
     */
    private static byte[] bytes(String data) {
        return data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
    }
}
