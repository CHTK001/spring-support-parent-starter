// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
package com.chua.starter.collect.engine.executor;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.CollectRunResult;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectFlowProvider;
import com.chua.starter.collect.engine.spi.CollectFlowRef;
import com.chua.starter.collect.engine.spi.CollectParamMeta;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.support.CollectTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子流程节点：在当前任务里同步执行另一条流程。
 *
 * <p>子流程不单独占任务行，节点日志与采集记录都记进父任务，抓取与记录数并入父任务计量；
 * 它有自己的变量作用域与线程池，跑完按配置把变量回写到本令牌作用域。子流程未成功时本节点抛异常，
 * 之后走的是所有节点共用的规则：有出边承接该失败（总是或仅异常）时失败只记在本节点日志并把原因
 * 写进 {@code ex} 带给下游，没有任何出边承接时整个任务才落成失败。</p>
 *
 * <p>调用链成环会被拒绝执行：自身引用在保存时报错，A→B→A 这类跨流程的环在运行时按调用栈拦下。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect.process", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectProcessExecutor implements CollectShapeExecutor {

    /**
     * 节点类型标识。
     */
    public static final String SHAPE = "process";

    /**
     * 目标流程ID参数名。
     */
    public static final String FLOW_ID = "flowId";

    /**
     * 变量继承参数名。
     */
    public static final String INHERIT = "inherit";

    /**
     * 额外传参参数组名。
     */
    public static final String PASS = "pass";

    /**
     * 变量回写策略参数名。
     */
    public static final String WRITE_BACK = "writeBack";

    /**
     * 出参前缀参数名。
     */
    public static final String OUTPUT_PREFIX = "outputPrefix";

    /**
     * 子流程超时毫秒参数名。
     */
    public static final String TIMEOUT = "timeout";

    /**
     * 继承全部上游变量。
     */
    public static final String INHERIT_ALL = "all";

    /**
     * 不继承上游变量，只认显式传参。
     */
    public static final String INHERIT_NONE = "none";

    /**
     * 只回写父作用域已有的变量。
     */
    public static final String WRITE_BACK_CHANGED = "changed";

    /**
     * 回写子流程全部变量。
     */
    public static final String WRITE_BACK_ALL = "all";

    /**
     * 不回写变量，只保留统计出参。
     */
    public static final String WRITE_BACK_NONE = "none";

    /**
     * 出参前缀默认值。
     */
    public static final String DEFAULT_PREFIX = "sub";

    /**
     * 超时默认值，非正数表示沿用单任务超时。
     */
    public static final long DEFAULT_TIMEOUT = 0L;

    /**
     * 执行引擎，延迟取用：引擎构造期要收全部执行器，反向直注会成环。
     */
    private final ObjectProvider<CollectEngine> engine;

    /**
     * 子流程来源，延迟取用：默认实现是流程服务，它本身依赖引擎。
     */
    private final ObjectProvider<CollectFlowProvider> flowProviders;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    /**
     * 来源列表缓存，进程内装配完成后不变。
     */
    private volatile List<CollectFlowProvider> providers;

    @Override
    public String supportShape() {
        return SHAPE;
    }

    @Override
    public CollectShapeMeta meta() {
        CollectShapeMeta meta = CollectShapeMetas.of(supportShape(), "子流程", "控制", "cluster",
                "同步执行另一条流程，记录与抓取并入本任务，失败可按异常流转容错");
        CollectParamMeta flow = CollectShapeMetas.required(FLOW_ID, "目标子流程", "flow",
                "从流程下拉里选一条已存在的流程；不能选自己，跨流程成环会被拒绝执行");
        CollectParamMeta pass = CollectShapeMetas.text(PASS + ":变量名", "传入变量（变量名 = 表达式）", "pairs",
                "覆盖继承过去的同名变量；不继承上游时这里就是全部入参");
        return CollectShapeMetas.with(meta, flow,
                CollectShapeMetas.option(INHERIT, "继承上游变量", INHERIT_ALL,
                        "选 none 时子流程只看\"传入变量\"，避免被上游同名变量干扰", INHERIT_ALL, INHERIT_NONE),
                pass,
                CollectShapeMetas.option(WRITE_BACK, "变量回写", WRITE_BACK_CHANGED,
                        "changed 只回写本作用域已有的变量；all 连子流程新建的变量一并写回；none 只留统计出参",
                        WRITE_BACK_CHANGED, WRITE_BACK_ALL, WRITE_BACK_NONE),
                CollectShapeMetas.text(OUTPUT_PREFIX, "出参前缀", "text",
                        "产出 subStatus/subNodes/subRequests/subRecords/subCost/subError"),
                CollectShapeMetas.text(TIMEOUT, "超时毫秒", "number", "留空按单任务超时"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) throws Exception {
        Integer target = targetFlowId(node);
        checkCallable(node, context, target);
        CollectFlowRef ref = load(target);
        if (ref == null) {
            throw new IllegalStateException("子流程不存在或已删除: 流程 " + target);
        }
        if (ref.dsl() == null) {
            throw new IllegalStateException("子流程[" + displayName(ref, target) + "]还没有设计流程图");
        }
        Map<String, Object> seed = seed(node, variables);
        CollectRunResult result = engine.getObject().executeNested(context, target, ref.dsl(), seed,
                node.longParam(TIMEOUT, DEFAULT_TIMEOUT));
        String prefix = prefix(node);
        writeOutputs(prefix, variables, result);
        writeBackVariables(node, prefix, variables, result);
        if (!CollectTaskStatus.SUCCESS.equals(result.getStatus())) {
            throw new IllegalStateException("子流程[" + displayName(ref, target) + "]未成功: " + describe(result));
        }
    }

    @Override
    public String summary(CollectNode node, Map<String, Object> variables) {
        String prefix = prefix(node);
        Object records = variables.get(prefix + "Records");
        Object nodes = variables.get(prefix + "Nodes");
        return records == null ? null : "节点 " + nodes + " 次，产出 " + records + " 条，耗时 "
                + variables.get(prefix + "Cost") + "ms";
    }

    /**
     * 取目标流程ID。
     *
     * @param node 当前节点
     * @return 流程ID
     * @throws IllegalStateException 未填写或不是数字
     */
    private Integer targetFlowId(CollectNode node) {
        String raw = node.param(FLOW_ID).trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("子流程节点没有选择目标流程");
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("子流程节点的目标流程ID不是数字: " + raw);
        }
    }

    /**
     * 运行前拦下自引用、超层数与跨流程成环。
     *
     * @param node    当前节点
     * @param context 执行上下文
     * @param target  目标流程ID
     * @throws IllegalStateException 不允许调用
     */
    private void checkCallable(CollectNode node, CollectContext context, Integer target) {
        if (target.equals(context.getFlowId())) {
            throw new IllegalStateException("子流程不能调用自身: " + target);
        }
        if (context.referencesFlow(target)) {
            throw new IllegalStateException("子流程引用成环，调用链上已有流程 " + target + "，已拒绝执行");
        }
        int maxDepth = Math.max(1, properties.getProcess().getMaxDepth());
        if (context.getDepth() + 1 > maxDepth) {
            throw new IllegalStateException("子流程嵌套超过上限 " + maxDepth + " 层（plugin.collect.process.max-depth）");
        }
    }

    /**
     * 构造传给子流程的变量：按配置继承上游，再用显式传参覆盖同名项。
     *
     * @param node      当前节点
     * @param variables 本令牌作用域
     * @return 入参表
     */
    private Map<String, Object> seed(CollectNode node, Map<String, Object> variables) {
        Map<String, Object> seed = new LinkedHashMap<>();
        if (!INHERIT_NONE.equalsIgnoreCase(node.param(INHERIT).trim())) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (callable(entry.getKey())) {
                    seed.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (CollectParamDTO pair : node.pairsOf(PASS)) {
            String name = pair.name() == null ? "" : pair.name().trim();
            if (callable(name)) {
                seed.put(name, CollectExpression.eval(pair.value(), variables));
            }
        }
        return seed;
    }

    /**
     * 变量是否允许跨子流程边界传递。
     *
     * @param name 变量名
     * @return 可传递返回 true
     */
    private boolean callable(String name) {
        return name != null && !name.isBlank() && !name.startsWith("_")
                && !CollectEngine.VAR_EXCEPTION.equals(name) && !CollectEngine.IDENTITY_VARIABLES.contains(name);
    }

    /**
     * 把子流程统计写成带前缀的出参。
     *
     * @param prefix    出参前缀
     * @param variables 本令牌作用域
     * @param result    子流程执行结果
     */
    private void writeOutputs(String prefix, Map<String, Object> variables, CollectRunResult result) {
        variables.put(prefix + "Status", result.getStatus());
        variables.put(prefix + "Nodes", (long) result.getNodeCount());
        variables.put(prefix + "Requests", (long) result.getRequestCount());
        variables.put(prefix + "Records", (long) result.getRecordCount());
        variables.put(prefix + "Cost", result.getCostMs());
        variables.put(prefix + "Error", result.getErrorMessage() == null ? "" : result.getErrorMessage());
    }

    /**
     * 按回写策略把子流程变量并入本作用域。
     *
     * <p>只写本令牌作用域，不写父流程全局变量：并行分支同时回写同名全局变量会互相覆盖，
     * 需要跨分支共享时在子流程后用变量节点转一次。</p>
     *
     * @param node      当前节点
     * @param prefix    出参前缀
     * @param variables 本令牌作用域
     * @param result    子流程执行结果
     */
    private void writeBackVariables(CollectNode node, String prefix,
                                    Map<String, Object> variables, CollectRunResult result) {
        String policy = node.param(WRITE_BACK).trim();
        if (WRITE_BACK_NONE.equalsIgnoreCase(policy)) {
            return;
        }
        boolean all = WRITE_BACK_ALL.equalsIgnoreCase(policy);
        for (Map.Entry<String, Object> entry : result.getVariables().entrySet()) {
            String name = entry.getKey();
            if (!callable(name) || (!all && !variables.containsKey(name)) || name.startsWith(prefix)) {
                continue;
            }
            variables.put(name, entry.getValue() == null ? "" : entry.getValue());
        }
    }

    /**
     * 取出参前缀。
     *
     * @param node 当前节点
     * @return 归一化后的前缀
     */
    private String prefix(CollectNode node) {
        String prefix = node.param(OUTPUT_PREFIX).trim();
        return prefix.isEmpty() ? DEFAULT_PREFIX : prefix;
    }

    /**
     * 子流程显示名。
     *
     * @param ref    子流程定义
     * @param target 流程ID
     * @return 名称，缺失时退回ID
     */
    private String displayName(CollectFlowRef ref, Integer target) {
        return ref.flowName() == null || ref.flowName().isBlank() ? "流程 " + target : ref.flowName();
    }

    /**
     * 拼一句可读的失败说明。
     *
     * @param result 子流程执行结果
     * @return 状态与原因
     */
    private String describe(CollectRunResult result) {
        String error = result.getErrorMessage();
        return error == null || error.isBlank() ? result.getStatus() : result.getStatus() + " " + error;
    }

    /**
     * 按流程ID取子流程定义，多个来源时先注册的生效。
     *
     * @param flowId 目标流程ID
     * @return 子流程定义，全部来源都没有返回 null
     */
    private CollectFlowRef load(Integer flowId) {
        for (CollectFlowProvider provider : providers()) {
            CollectFlowRef ref = provider.load(flowId);
            if (ref != null) {
                return ref;
            }
        }
        return null;
    }

    /**
     * 收集 Spring 与 {@code @Spi} 两侧的子流程来源，按实现类去重。
     *
     * @return 来源列表
     */
    private List<CollectFlowProvider> providers() {
        List<CollectFlowProvider> local = providers;
        if (local != null) {
            return local;
        }
        Map<Class<?>, CollectFlowProvider> byClass = new LinkedHashMap<>();
        flowProviders.orderedStream().forEach(item -> byClass.putIfAbsent(item.getClass(), item));
        try {
            ServiceProvider.of(CollectFlowProvider.class).collect(item -> {
                if (item != null) {
                    byClass.putIfAbsent(item.getClass(), item);
                }
            });
        } catch (Exception ex) {
            log.debug("[collect] SPI 子流程来源收集跳过: {}", ex.getMessage());
        }
        local = List.copyOf(byClass.values());
        providers = local;
        return local;
    }
}
