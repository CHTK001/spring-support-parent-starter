package com.chua.starter.datapool.support.service.impl;

import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperAclInfo;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperChildNodeInfo;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperClusterNode;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperManageProvider;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperManageTarget;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperNodeDetail;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperNodeInfo;
import com.chua.datasource.support.middleware.zookeeper.ZookeeperTestResult;
import com.chua.starter.datapool.support.middleware.ZookeeperManageSupport;
import com.chua.starter.datapool.support.model.ZkChildNodeVO;
import com.chua.starter.datapool.support.model.ZkClusterNodeVO;
import com.chua.starter.datapool.support.model.ZkNodeAclVO;
import com.chua.starter.datapool.support.model.ZkNodeDetailVO;
import com.chua.starter.datapool.support.model.ZkNodeVO;
import com.chua.starter.datapool.support.model.ZookeeperTestVO;
import com.chua.starter.datapool.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datapool.support.model.dto.ZkNodeDTO;
import com.chua.starter.datapool.support.service.ZookeeperManageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ZooKeeper 管理实现：本模块只做「数据源行 → 连接目标 → VO」的编排，
 * 具体节点读写由 {@link ZookeeperManageProvider}（{@code utils-support-zookeeper-starter}
 * 或同等的 OSGi bundle）承担，因此数据源中心不携带 Curator/ZooKeeper 客户端。
 *
 * <p>失败只回原因，不造演示数据；实现未安装时提示「功能未开放」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZookeeperManageServiceImpl implements ZookeeperManageService {

    /**
     * ZooKeeper 管理面取用入口
     */
    private final ZookeeperManageSupport support;

    @Override
    public ZookeeperTestVO test(MiddlewareTestDTO request) {
        ZookeeperManageProvider provider;
        ZookeeperManageTarget target;
        try {
            provider = support.provider();
            target = support.testTarget(request);
        } catch (IllegalStateException | MiddlewareNotInstalledException e) {
            return new ZookeeperTestVO(false, null, null, e.getMessage());
        }
        ZookeeperTestResult result = provider.test(target);
        return new ZookeeperTestVO(result.connected(), result.version(), result.sessionId(), result.message());
    }

    @Override
    public List<ZkNodeVO> tree(Long datasourceId, String path) {
        List<ZookeeperNodeInfo> nodes = support.provider().tree(support.target(datasourceId), path);
        List<ZkNodeVO> result = new ArrayList<>(nodes.size());
        for (ZookeeperNodeInfo node : nodes) {
            result.add(new ZkNodeVO(node.path(), node.name(), node.ephemeral(), node.childrenCount()));
        }
        return result;
    }

    @Override
    public ZkNodeDetailVO node(String path, Long datasourceId) {
        ZookeeperNodeDetail detail = support.provider().node(support.target(datasourceId), path);
        return new ZkNodeDetailVO(detail.path(), detail.ephemeral(), detail.createdAt(), detail.updatedAt(),
                detail.version(), detail.numChildren(), detail.dataLength(), detail.data(),
                detail.czxid(), detail.mzxid(), detail.pzxid(),
                detail.cversion(), detail.aversion(), detail.ephemeralOwner());
    }

    @Override
    public List<ZkChildNodeVO> children(String path, Long datasourceId) {
        List<ZookeeperChildNodeInfo> children = support.provider().children(support.target(datasourceId), path);
        List<ZkChildNodeVO> result = new ArrayList<>(children.size());
        for (ZookeeperChildNodeInfo child : children) {
            result.add(new ZkChildNodeVO(child.name(), child.path(), child.ephemeral(), child.data()));
        }
        return result;
    }

    @Override
    public boolean create(ZkNodeDTO request) {
        Long datasourceId = request == null ? null : request.datasourceId();
        return support.provider().create(support.target(datasourceId),
                request == null ? null : request.path(),
                request == null ? null : request.data(),
                request != null && Boolean.TRUE.equals(request.ephemeral()));
    }

    @Override
    public boolean update(ZkNodeDTO request) {
        Long datasourceId = request == null ? null : request.datasourceId();
        return support.provider().update(support.target(datasourceId),
                request == null ? null : request.path(),
                request == null ? null : request.data());
    }

    @Override
    public boolean delete(String path, Long datasourceId, boolean recursive) {
        return support.provider().delete(support.target(datasourceId), path, recursive);
    }

    @Override
    public List<ZkClusterNodeVO> cluster(Long datasourceId) {
        List<ZookeeperClusterNode> nodes = support.provider().cluster(support.target(datasourceId));
        List<ZkClusterNodeVO> result = new ArrayList<>(nodes.size());
        for (ZookeeperClusterNode node : nodes) {
            result.add(parseMntr(node));
        }
        return result;
    }

    @Override
    public List<ZkNodeAclVO> acl(String path, Long datasourceId) {
        List<ZookeeperAclInfo> acls = support.provider().acl(support.target(datasourceId), path);
        List<ZkNodeAclVO> result = new ArrayList<>(acls.size());
        for (ZookeeperAclInfo acl : acls) {
            result.add(new ZkNodeAclVO(acl.scheme(), acl.id(), acl.perms(), acl.permsText()));
        }
        return result;
    }

    /**
     * 解析 {@code mntr} 回包为单节点指标。
     *
     * <p>回包为 {@code key\tvalue} 每行一条；四字命令未放开时回包为空或报错串，
     * 此时所有指标留空并把原因放进 {@code error} 字段。</p>
     *
     * @param node 采集结果（含原始回包）
     * @return 节点指标
     */
    private static ZkClusterNodeVO parseMntr(ZookeeperClusterNode node) {
        String body = node.rawMetrics();
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
            String reason = node.error() != null ? node.error()
                    : (body == null || body.isBlank() ? "四字命令 mntr 未放开或不可达" : body.trim());
            return new ZkClusterNodeVO(node.host(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, reason);
        }
        return new ZkClusterNodeVO(node.host(),
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
}
