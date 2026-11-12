package com.chua.starter.monitor.support.operlog;

import com.chua.starter.monitor.support.entity.MsServerNode;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 删除前的节点快照 —— 给 {@code ms_server_oper_log} 里那条 DELETE 留痕补主语。
 *
 * <p><strong>为什么需要它</strong>：{@code @SysLog} 事件是在业务方法返回之后才发出的，
 * 而删除已经先一步把 {@code ms_server_node} 的行删掉了。监听器此时再查主表，
 * 只能拿到「查不到」，DELETE 日志的 nodeName / target 两列就永远是空的 ——
 * 留痕留了一条谁也认不出删了谁的记录。</p>
 *
 * <p><strong>为什么按 nodeId 存 Map 而不是 ThreadLocal</strong>：事件默认同步投递，
 * 但一旦有异步 {@code ApplicationEventMulticaster}，ThreadLocal 在请求线程上登记、
 * 在任务线程上取走，必然取不到，快照会静默失效。按 nodeId 存一个短命条目则与线程无关，
 * 取走即清理。</p>
 *
 * <p>节点 id 由数据库自增生成、不复用，所以残留条目不会被别的节点误认领；
 * 只有「事件根本没投递」这种异常情况才可能留下一条无用条目，不影响任何业务结果。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
public final class ServerOperLogNodeSnapshot {

    /**
     * 待删除节点的快照，key 为节点 id，取走即移除。
     */
    private static final Map<Long, ServerOperLogNodeSnapshot> PENDING = new ConcurrentHashMap<>();

    /**
     * 节点 id
     */
    @Getter
    private final Long nodeId;

    /**
     * 节点名称（删除前的值）
     */
    @Getter
    private final String nodeName;

    /**
     * 访问摘要：协议 + 监听地址（删除前的值）
     */
    @Getter
    private final String target;

    private ServerOperLogNodeSnapshot(Long nodeId, String nodeName, String target) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.target = target;
    }

    /**
     * 删除前登记快照。
     *
     * <p>由服务层在真正删行之前调用；方法本身不抛异常，登记失败只影响留痕质量。</p>
     *
     * @param node 即将删除的节点
     */
    public static void capture(MsServerNode node) {
        if (node == null || node.getId() == null) {
            return;
        }
        String name = node.getName() == null ? "" : node.getName();
        String protocol = node.getProtocol() == null ? "" : node.getProtocol();
        String host = node.getHost() == null ? "" : node.getHost();
        String port = node.getPort() == null ? "" : String.valueOf(node.getPort());
        String target = (protocol + " " + host + ":" + port).trim();
        PENDING.put(node.getId(), new ServerOperLogNodeSnapshot(
                node.getId(), name, target.isBlank() ? null : target));
    }

    /**
     * 取走快照并清理。
     *
     * @param expectedNodeId 本次日志事件对应的节点 id
     * @return 命中时返回快照；没有登记过返回 null
     */
    public static ServerOperLogNodeSnapshot take(Long expectedNodeId) {
        return expectedNodeId == null ? null : PENDING.remove(expectedNodeId);
    }
}
