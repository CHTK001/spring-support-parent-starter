package com.chua.starter.monitor.support.operlog;

import com.chua.starter.common.support.logger.SysLoggerInfo;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.chua.starter.monitor.support.entity.MsServerOperLog;
import com.chua.starter.monitor.support.mapper.MsServerNodeMapper;
import com.chua.starter.monitor.support.service.ServerOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @SysLog} 事件 → {@code ms_server_oper_log} 的落库监听器。
 *
 * <p>切面（{@code SysLoggerPointcutAdvisor}）把 {@link SysLoggerInfo} 用
 * {@code ApplicationContext} 发出去，全仓默认没有消费者：事件被发布即被丢弃。
 * 本监听器补上服务管理这一段的写的一端。</p>
 *
 * <p><strong>为什么按 URL 收窄</strong>：切面对整个应用生效，系统模块（用户、租户、
 * 登录…）的日志不该出现在「某个服务节点的操作历史」里。这里只收
 * {@code /ms/monitor/server} 前缀下的写操作。</p>
 *
 * <p><strong>为什么只记能定位到节点的操作</strong>：{@code node_id} 是非空列。
 * 新建节点（{@code POST /ms/monitor/server}）的 id 在响应体里、事件里拿不到，
 * 这类请求一律丢弃并打 debug 便于核对口径 —— 新建之后紧接着的启动/停止/编辑
 * 都会留下来，时间线上不会缺主语。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerOperLogListener {

    /**
     * 服务管理自己的 URL 前缀，其余模块的 {@code @SysLog} 一律不收。
     */
    private static final String SCOPED_PREFIX = "/ms/monitor/server";

    /**
     * 路径里的节点 id：{@code /ms/monitor/server/5}、{@code /ms/monitor/server/5/start}。
     */
    private static final Pattern PATH_ID = Pattern.compile("^/ms/monitor/server/(\\d+)(?:/|$)");

    /**
     * 方法名 → 操作码。
     *
     * <p>不直接用方法名大写当操作码：{@code hotToggleFilter} 这类内部符号
     * 读起来没有意义，前端按码过滤、按文案展示都不该跟着 Java 方法名走。</p>
     */
    private static final Map<String, String> ACTION_CODES = Map.ofEntries(
            Map.entry("create", "CREATE"),
            Map.entry("update", "UPDATE"),
            Map.entry("hotUpdate", "HOT_UPDATE"),
            Map.entry("hotToggleFilter", "FILTER_TOGGLE"),
            Map.entry("hotUpdateFilterParams", "FILTER_PARAMS_UPDATE"),
            Map.entry("delete", "DELETE"),
            Map.entry("start", "START"),
            Map.entry("stop", "STOP"));

    private final ServerOperLogService operLogService;

    private final MsServerNodeMapper nodeMapper;

    /**
     * 接收切面发出的操作日志事件。
     *
     * @param info 事件载荷
     */
    @EventListener
    public void onSysLog(SysLoggerInfo info) {
        try {
            Long nodeId = resolveNodeId(info == null ? null : info.getLogMapping());
            if (nodeId == null) {
                return;
            }
            // 每次事件都取走快照（即便本次不是删除），避免残留跨请求认领
            ServerOperLogNodeSnapshot snapshot = ServerOperLogNodeSnapshot.take(nodeId);
            MsServerNode node = findNode(nodeId);
            if (isUntraceable(info, node, snapshot)) {
                // 失败 + 主表已查不到 + 没有删除快照：说明这次操作根本没落到任何节点上
                // （典型是删除/启停一个不存在的 id）。这种请求没有「该节点的操作历史」可言，
                // 落库只会得到一条 nodeName / target 全空、任何节点面板都查不到的孤儿行。
                log.debug("[server-oper-log] 跳过无主操作 nodeId={} action={} path={}",
                        nodeId, actionOf(info.getMethodName()), pathOf(info.getLogMapping()));
                return;
            }
            operLogService.record(toRow(info, nodeId, node, snapshot));
        } catch (RuntimeException e) {
            // 监听器抛异常会冒泡回切面、进而让业务请求报错，
            // 所以这里必须自己吞掉：留痕失败不影响用户操作。
            log.warn("[server-oper-log] 处理操作日志事件失败 reason={}", e.getMessage());
        }
    }

    /**
     * 从请求 URL 里解析节点 id。
     *
     * @param url 完整请求 URL（含查询串）
     * @return 节点 id；不在范围内或解析不出时返回 null
     */
    private Long resolveNodeId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // 只保留 path 部分做前缀判定，避免 host 名里恰好含 /ms 之类的误命中
        String path = pathOf(url);
        if (!path.startsWith(SCOPED_PREFIX)) {
            return null;
        }
        Matcher m = PATH_ID.matcher(path);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    /**
     * 判断这次操作是否「没有可留痕的主体」。
     *
     * <p>成功但查不到节点（删除刚走完）由快照兜住，不属于这一类；
     * 只有失败、且节点在库里根本不存在、也没有删除快照时才成立。</p>
     *
     * @param info     事件
     * @param node     当前查到的节点，可能为 null
     * @param snapshot 删除前快照，可能为 null
     * @return true 表示不落库
     */
    private boolean isUntraceable(SysLoggerInfo info, MsServerNode node, ServerOperLogNodeSnapshot snapshot) {
        if (node != null || snapshot != null) {
            return false;
        }
        Integer status = info == null ? null : info.getLogStatus();
        // status 为空按成功处理：宁可留一条空主语的行，也不要吞掉一次真实操作
        return status != null && status == 0;
    }

    /**
     * 事件转日志行。
     *
     * @param info     事件
     * @param nodeId   节点 id
     * @param node     当前节点；删除后为 null
     * @param snapshot 删除前快照；非删除操作为 null
     * @return 日志行
     */
    private MsServerOperLog toRow(SysLoggerInfo info, Long nodeId,
                                  MsServerNode node, ServerOperLogNodeSnapshot snapshot) {
        String path = pathOf(info.getLogMapping());
        MsServerOperLog row = new MsServerOperLog();
        row.setNodeId(nodeId);
        row.setNodeName(nameOf(node, snapshot));
        row.setAction(actionOf(info.getMethodName()));
        row.setActionName(info.getLogName() == null || info.getLogName().isBlank()
                ? info.getLogModule() : info.getLogName());
        row.setTarget(targetOf(node, snapshot));
        row.setDetail(buildDetail(info));
        row.setSuccess(info.getLogStatus());
        row.setCostMs(info.getLogCost());
        row.setOperator(resolveOperator(info));
        row.setClientIp(info.getClientIp());
        row.setRequestUrl(info.getLogMapping());
        row.setCreateTime(info.getCreateTime() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(info.getCreateTime().toInstant(), ZoneId.systemDefault()));
        log.debug("[server-oper-log] 记录节点 {} 的操作 {} path={}", nodeId, row.getAction(), path);
        return row;
    }

    /**
     * 操作码：方法名映射，未登记的用大写方法名兜底。
     *
     * <p>未登记不该等于「记下来没人看得懂」，大写的方法名本身是可检索的线索。</p>
     *
     * @param methodName 控制器方法名
     * @return 操作码
     */
    private String actionOf(String methodName) {
        String method = methodName == null ? "" : methodName;
        String mapped = ACTION_CODES.get(method);
        return mapped != null ? mapped : upper(method);
    }

    /**
     * 取操作人。
     *
     * <p>空串不是 null：上游切面拿不到用户名时会给出 {@code createName = ""}，
     * 若直接返回它会挡掉本来有值的 {@code createBy}，操作日志的「谁做的」整列长期为空。</p>
     *
     * @param info 事件
     * @return 操作人；两边都取不到时返回 null（留空比写「未知」诚实）
     */
    private String resolveOperator(SysLoggerInfo info) {
        String name = info.getCreateName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String by = info.getCreateBy();
        if (by != null && !by.isBlank()) {
            return by;
        }
        return null;
    }

    /**
     * 取 URL 的 path 部分（含查询串）。
     *
     * @param url 完整请求 URL
     * @return path
     */
    private String pathOf(String url) {
        if (url == null) {
            return "";
        }
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(slash) : "/";
    }

    /**
     * 明细：优先用注解上的 content，其次带请求参数（改了什么配置的主要线索）。
     *
     * @param info 事件
     * @return 明细；都取不到返回 null
     */
    private String buildDetail(SysLoggerInfo info) {
        if (info.getLogContent() != null && !info.getLogContent().isBlank()) {
            return info.getLogContent();
        }
        if (info.getLogParam() != null && !info.getLogParam().isBlank()) {
            return info.getLogParam();
        }
        return null;
    }

    /**
     * 方法名大写（空名返回 {@code UNKNOWN}）。
     *
     * @param method 方法名
     * @return 大写结果
     */
    private String upper(String method) {
        return method.isBlank() ? "UNKNOWN" : method.toUpperCase(Locale.ROOT);
    }

    /**
     * 冗余一份节点名称。
     *
     * <p>删除操作触发时主表里已经没有这行了，此时用删除前快照顶上；
     * 两边都拿不到才留空串（{@code node_name} 是非空列）。</p>
     *
     * @param node     当前节点，可能为 null
     * @param snapshot 删除前快照，可能为 null
     * @return 节点名称
     */
    private String nameOf(MsServerNode node, ServerOperLogNodeSnapshot snapshot) {
        if (node != null) {
            return node.getName() == null ? "" : node.getName();
        }
        if (snapshot != null && snapshot.getNodeName() != null) {
            return snapshot.getNodeName();
        }
        return "";
    }

    /**
     * 冗余一份节点访问摘要（协议 + 监听地址）。
     *
     * @param node     当前节点，可能为 null
     * @param snapshot 删除前快照，可能为 null
     * @return 摘要；两边都拿不到返回 null
     */
    private String targetOf(MsServerNode node, ServerOperLogNodeSnapshot snapshot) {
        if (node != null) {
            String protocol = node.getProtocol() == null ? "" : node.getProtocol();
            String host = node.getHost() == null ? "" : node.getHost();
            String port = node.getPort() == null ? "" : String.valueOf(node.getPort());
            String target = (protocol + " " + host + ":" + port).trim();
            return target.isBlank() ? null : target;
        }
        return snapshot == null ? null : snapshot.getTarget();
    }

    /**
     * 查节点（失败不外抛：日志不能因为主表查询异常而丢）。
     *
     * @param nodeId 节点 id
     * @return 节点；查不到或异常返回 null
     */
    private MsServerNode findNode(Long nodeId) {
        try {
            return nodeMapper.selectById(nodeId);
        } catch (RuntimeException e) {
            log.debug("[server-oper-log] 查节点失败 nodeId={}", nodeId);
            return null;
        }
    }
}
