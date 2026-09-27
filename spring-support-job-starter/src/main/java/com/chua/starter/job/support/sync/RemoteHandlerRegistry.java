package com.chua.starter.job.support.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行器节点注册表（调度中心侧）。
 *
 * <p>分布式拓扑下调度中心节点本地没有业务处理器，只有执行器知道任务能不能真跑。
 * 执行器通过 {@link JobSyncTopics#REGISTER} / {@link JobSyncTopics#HANDLERS} 上报
 * 节点身份（nodeId / role / 长链地址）与处理器清单，本表按 客户端标识 维护：</p>
 * <ul>
 *   <li>供治理层合并"未注册"判定与执行器列表（{@link #all()} / {@link #contains(String)}）</li>
 *   <li>供「远程作业」页展示在线节点与处理器（{@link #nodes()}）</li>
 *   <li>断开时由 {@link #remove(String)} 移除（同步服务端回调）</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class RemoteHandlerRegistry {

    /**
     * 客户端标识 → 节点信息
     */
    private final Map<String, NodeInfo> nodesByClient = new ConcurrentHashMap<>();

    /**
     * 登记/覆盖某执行器的节点信息。
     *
     * @param clientId    客户端标识（同步通道侧）
     * @param registerInfo 注册信息
     */
    public void register(String clientId, JobSyncTopics.RegisterInfo registerInfo) {
        if (clientId == null || clientId.isBlank() || registerInfo == null) {
            return;
        }
        NodeInfo previous = nodesByClient.get(clientId);
        nodesByClient.put(clientId, new NodeInfo(
                clientId,
                registerInfo.nodeId() != null ? registerInfo.nodeId() : clientId,
                registerInfo.role() != null ? registerInfo.role() : "EXECUTOR",
                registerInfo.serverUrl() != null ? registerInfo.serverUrl()
                        : previous == null ? null : previous.serverUrl(),
                new LinkedHashSet<>(registerInfo.handlers()),
                previous == null ? System.currentTimeMillis() : previous.registeredAtMs()));
    }

    /**
     * 仅更新某执行器的处理器清单（清单变化时的增量上报）。
     *
     * @param clientId 客户端标识
     * @param handlers 处理器名集合
     */
    public void updateHandlers(String clientId, Collection<String> handlers) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        NodeInfo previous = nodesByClient.get(clientId);
        if (previous == null) {
            register(clientId, new JobSyncTopics.RegisterInfo(null, null, null,
                    handlers == null ? List.of() : new ArrayList<>(handlers)));
            return;
        }
        nodesByClient.put(clientId, new NodeInfo(previous.clientId(), previous.nodeId(), previous.role(),
                previous.serverUrl(), handlers == null ? Set.of() : new LinkedHashSet<>(handlers),
                previous.registeredAtMs()));
    }

    /**
     * 移除某执行器的登记（连接关闭等场景由上层调用）。
     *
     * @param clientId 客户端标识
     */
    public void remove(String clientId) {
        if (clientId != null) {
            nodesByClient.remove(clientId);
        }
    }

    /**
     * 全部在线执行器上报的处理器名并集。
     *
     * @return 处理器名并集
     */
    public Set<String> all() {
        Set<String> merged = new HashSet<>();
        for (NodeInfo info : nodesByClient.values()) {
            merged.addAll(info.handlers());
        }
        return merged;
    }

    /**
     * 是否包含某处理器（本地或远端上报）。
     *
     * @param name 处理器名
     * @return 包含返回 true
     */
    public boolean contains(String name) {
        if (name == null) {
            return false;
        }
        for (NodeInfo info : nodesByClient.values()) {
            if (info.handlers().contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 已上报清单的执行器数量。
     *
     * @return 执行器数量
     */
    public int clientCount() {
        return nodesByClient.size();
    }

    /**
     * 在线执行器节点列表（按注册时间升序）。
     *
     * @return 节点信息列表
     */
    public List<NodeInfo> nodes() {
        List<NodeInfo> rows = new ArrayList<>(nodesByClient.values());
        rows.sort((a, b) -> Long.compare(a.registeredAtMs(), b.registeredAtMs()));
        return rows;
    }

    /**
     * 执行器节点信息。
     *
     * @param clientId       同步通道客户端标识
     * @param nodeId         执行器自述节点标识
     * @param role           角色
     * @param serverUrl      调度中心长链地址（执行器配置的）
     * @param handlers       处理器清单
     * @param registeredAtMs 首次登记时间（重连不刷新）
     */
    public record NodeInfo(String clientId, String nodeId, String role, String serverUrl,
                           Set<String> handlers, long registeredAtMs) {

        /**
         * 规范构造器：对处理器清单做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。清单按登记先后插入，
         * 「远程作业」页依此顺序展示，故沿用 {@link LinkedHashSet} 的顺序，
         * 不改用迭代顺序未定义的 {@link Set#copyOf}。</p>
         *
         * @param clientId       同步通道客户端标识
         * @param nodeId         执行器自述节点标识
         * @param role           角色
         * @param serverUrl      调度中心长链地址（执行器配置的）
         * @param handlers       处理器清单
         * @param registeredAtMs 首次登记时间（重连不刷新）
         */
        public NodeInfo {
            handlers = Collections.unmodifiableSet(new LinkedHashSet<>(
                    Objects.requireNonNull(handlers, "handlers 不能为 null")));
        }
    }
}
