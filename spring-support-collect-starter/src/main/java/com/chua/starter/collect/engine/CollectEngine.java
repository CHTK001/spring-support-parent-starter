// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.engine;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectNodeDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.engine.model.CollectEdge;
import com.chua.starter.collect.engine.model.CollectGraph;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 采集流程执行引擎。
 *
 * <p>采用令牌语义：一次节点执行是一个令牌，令牌跑完后按出边派发下游令牌，因此同一个节点
 * 可以被不同令牌多次执行，汇聚不需要到达计数。</p>
 * <ul>
 *   <li>入口为 start 节点，没有 start 时取入度为 0 的节点。</li>
 *   <li>节点的 {@code loopCount} 参数决定令牌展开几个迭代，可以是次数也可以是集合；每个迭代
 *       把自己的作用域沿下游继续传播，这就是"列表页抓 N 条、每条再抓详情"的天然表达。
 *       集合为空时本节点及其下游都不执行。</li>
 *   <li>{@code loopParallel} 打开后各迭代各自入池，由线程池大小限流；关闭时按顺序执行。</li>
 *   <li>连线按异常流向与 {@code condition} 放行；环路在建模期被拒绝，令牌不会无限增殖。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectEngine {

    /**
     * 循环次数参数名。
     */
    public static final String PARAM_LOOP_COUNT = "loopCount";

    /**
     * 循环下标变量名参数。
     */
    public static final String PARAM_LOOP_INDEX = "loopVariableName";

    /**
     * 循环元素变量名参数。
     */
    public static final String PARAM_LOOP_ITEM_VARIABLE = "loopItemVariableName";

    /**
     * 循环并行开关参数名。
     */
    public static final String PARAM_LOOP_PARALLEL = "loopParallel";

    /**
     * 节点输出名参数名（设计器「输出名」，默认 {@code data}）。
     *
     * <p>各执行器把自己的产出写进这个变量；引擎再按它派生 {@code data} 别名、
     * {@code dataJson} 与节点产出留存，因此这里与执行器的 {@code DEFAULT_OUTPUT}
     * 必须同名同义。改默认值时三处一起改。</p>
     */
    public static final String OUTPUT_VARIABLE = "outputVariable";

    /**
     * 节点内部详情（入参 / 轮次）在出参列的硬上限。
     *
     * <p>{@code collect_task_log_output} 是 varchar(2000)，出参预览自身最多
     * previewLength*4（默认 500*4=2000），拼上入参后必须整体截到 1990 以内，
     * 否则 MySQL 严格模式下写日志直接报错、把节点执行拖成任务失败。</p>
     */
    private static final int OUTPUT_DETAIL_LIMIT = 1990;

    /**
     * 单节点入参详情预算：超出直接截断，保证出参列头部「这节点拿什么配置去执行」可读。
     */
    private static final int INPUT_DETAIL_LIMIT = 600;

    /**
     * 入参详情里单个参数值的截断长度（求值结果可能是整页 HTML）。
     */
    private static final int INPUT_VALUE_LIMIT = 120;

    /**
     * 上游异常文本的变量名。
     */
    public static final String VAR_EXCEPTION = "ex";

    /**
     * 令牌终止变量名，执行器写入 true 后本令牌不再向下游派发。
     */
    public static final String VAR_STOP = "_stop";

    /**
     * 节点级超时毫秒参数名。
     */
    public static final String PARAM_NODE_TIMEOUT = "timeout";

    /**
     * 节点超时策略参数名。
     */
    public static final String PARAM_NODE_TIMEOUT_POLICY = "timeoutPolicy";

    /**
     * 节点超时策略：自动延长一次。
     */
    public static final String TIMEOUT_POLICY_EXTEND = "extend";

    /**
     * 节点超时策略：直接结束。
     */
    public static final String TIMEOUT_POLICY_END = "end";

    /**
     * 引擎按流程身份注入的变量，跨子流程边界时不继承。
     */
    public static final Set<String> IDENTITY_VARIABLES = Set.of("flowId", "taskId", "taskNo");

    /**
     * 执行器注册表。
     */
    private final CollectExecutorRegistry registry;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    /**
     * 运行中的任务上下文。
     */
    private final Map<Long, CollectContext> running = new ConcurrentHashMap<>();

    /**
     * 执行流程图直到结束、停止或超时。
     *
     * @param context 执行上下文
     * @param dsl     流程图 DSL
     * @return 执行结果
     */
    public CollectRunResult execute(CollectContext context, CollectDslDTO dsl) {
        long begin = System.currentTimeMillis();
        CollectGraph graph;
        try {
            graph = CollectGraph.build(dsl, registry.shapes());
        } catch (CollectGraph.CollectGraphException ex) {
            return finish(context, begin, CollectTaskStatus.FAILURE, ex.getMessage());
        }
        ExecutorService pool = createPool(context, dsl);
        context.attachPool(pool);
        running.put(context.getTaskId(), context);
        try {
            // 流程显式声明了「采集平台(output)」节点时关闭默认落记录，避免双写；
            // 否则引擎默认把行集合产出落成采集记录（历史与界面数据不依赖编排加节点）。
            context.setAutoRecord(!hasOutputNode(dsl));
            seedVariables(context, dsl);
            for (CollectNode node : graph.entryNodes()) {
                schedule(context, node, new LinkedHashMap<>(context.getRootVariables()));
            }
            // 流程级运行超时：DSL 显式配置优先，0/未配置退回平台全局默认。
            // 分片型大采集（如 GitHub 全量分片 1140 个请求）默认 30 分钟会在收尾被杀，
            // 设计器「流程属性 → 运行超时」里可单独调大，不影响其他流程。
            long limit = effectiveTimeoutMs(dsl);
            return settle(context, begin, context.awaitFinish(limit), limit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.cancel();
            return finish(context, begin, CollectTaskStatus.FAILURE, "执行被中断: " + ex.getMessage());
        } finally {
            running.remove(context.getTaskId());
            pool.shutdownNow();
        }
    }

    /**
     * 判断流程 DSL 中是否声明了「采集平台(output)」节点。
     *
     * @param dsl 流程 DSL
     * @return 含 output 节点返回 true
     */
    private static boolean hasOutputNode(CollectDslDTO dsl) {
        if (dsl == null || dsl.nodes() == null) {
            return false;
        }
        for (CollectNodeDTO node : dsl.nodes()) {
            if ("output".equals(node.shape())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取本次任务实际生效的超时毫秒：流程 DSL 显式配置优先，否则用平台全局默认。
     *
     * @param dsl 流程 DSL
     * @return 本次任务超时毫秒（恒大于 0）
     */
    private long effectiveTimeoutMs(CollectDslDTO dsl) {
        if (dsl != null && dsl.timeoutMs() != null && dsl.timeoutMs() > 0L) {
            return dsl.timeoutMs();
        }
        return properties.getTaskTimeoutMs();
    }

    /**
     * 在父任务里同步执行一段子流程。
     *
     * <p>子流程不单独占任务行，因此不写 {@code running}：停止、并发与调度计数都归根任务。
     * 线程池必须单独建——父令牌正阻塞等子流程，若复用父池，池里线程全在等孩子、
     * 孩子的令牌却排在自己后面，直接锁死。</p>
     *
     * @param parent    父上下文
     * @param flowId    子流程ID
     * @param dsl       子流程图
     * @param seed      调用方传入的变量，覆盖子流程自带的同名变量
     * @param timeoutMs 子流程最长执行毫秒，非正数取单任务超时
     * @return 子流程执行结果，计量为本层小计
     * @throws InterruptedException 等待被中断
     */
    public CollectRunResult executeNested(CollectContext parent, Integer flowId, CollectDslDTO dsl,
                                          Map<String, Object> seed, long timeoutMs) throws InterruptedException {
        long begin = System.currentTimeMillis();
        CollectContext context = CollectContext.nested(parent, flowId);
        CollectGraph graph;
        try {
            graph = CollectGraph.build(dsl, registry.shapes());
        } catch (CollectGraph.CollectGraphException ex) {
            return finish(context, begin, CollectTaskStatus.FAILURE, "子流程图不合法: " + ex.getMessage());
        }
        ExecutorService pool = createPool(context, dsl);
        context.attachPool(pool);
        try {
            seedVariables(context, dsl);
            applySeed(context, seed);
            for (CollectNode node : graph.entryNodes()) {
                schedule(context, node, new LinkedHashMap<>(context.getRootVariables()));
            }
            long limit = timeoutMs > 0 ? timeoutMs : properties.getTaskTimeoutMs();
            return settle(context, begin, context.awaitFinish(limit), limit);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 等本次执行落定并汇总状态。
     *
     * @param context  执行上下文
     * @param begin    开始毫秒
     * @param settled  是否在超时前跑完
     * @param limit    本次实际生效的超时毫秒，用于超时说明
     * @return 执行结果
     * @throws InterruptedException 等待被中断
     */
    private CollectRunResult settle(CollectContext context, long begin, boolean settled, long limit)
            throws InterruptedException {
        String status = CollectTaskStatus.SUCCESS;
        String error = null;
        if (!settled) {
            status = CollectTaskStatus.FAILURE;
            error = "执行超时 " + limit + "ms，已强制结束";
            context.cancel();
        } else if (!context.isRunning()) {
            status = CollectTaskStatus.STOPPED;
            error = "任务被手动停止";
        } else if (context.getFailureMessage() != null) {
            status = CollectTaskStatus.FAILURE;
            error = context.getFailureMessage();
        }
        return finish(context, begin, status, error);
    }

    /**
     * 把调用方的实参盖在子流程自带变量之上。
     *
     * <p>引擎内部键与 {@code flowId}/{@code taskId}/{@code taskNo} 不覆盖：前两个是引擎记账用的，
     * 后三个必须指向子流程自己，否则子流程里的脚本会拿到父流程的身份。</p>
     *
     * @param context 子流程上下文
     * @param seed    调用方传入的变量
     */
    private void applySeed(CollectContext context, Map<String, Object> seed) {
        if (seed == null || seed.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : seed.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank() || name.startsWith("_")
                    || VAR_EXCEPTION.equals(name) || IDENTITY_VARIABLES.contains(name)) {
                continue;
            }
            context.putRootVariable(name.trim(), entry.getValue());
        }
    }

    /**
     * 创建本次执行独占的线程池。
     *
     * @param context 执行上下文
     * @param dsl     流程图 DSL
     * @return 线程池
     */
    private ExecutorService createPool(CollectContext context, CollectDslDTO dsl) {
        int threads = dsl.threadCount() == null || dsl.threadCount() <= 0
                ? properties.getDefaultThreadCount() : dsl.threadCount();
        int size = Math.max(1, Math.min(threads, 64));
// [P3C C1 豁免] 场景化创建线程，需文档化
        return Executors.newFixedThreadPool(size, runnable -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
            Thread thread = new Thread(runnable);
            thread.setName("collect-" + context.getTaskNo() + "-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 注入全局变量与内置变量。
     *
     * @param context 执行上下文
     * @param dsl     流程图 DSL
     */
    private void seedVariables(CollectContext context, CollectDslDTO dsl) {
        Map<String, Object> root = context.getRootVariables();
        // 节点产出留存上限：超过后不再登记新的节点产出，已登记的照常可引用，
        // 防止百万级采集把堆打满（古诗 52212 行 × 多支路时不设上限会 OOM）
        context.setNodeOutputLimit(properties.getNodeOutputLimit());
        context.putRootVariable("taskNo", context.getTaskNo());
        context.putRootVariable("taskId", context.getTaskId());
        context.putRootVariable("flowId", context.getFlowId());
        if (dsl.variables() != null) {
            for (CollectParamDTO param : dsl.variables()) {
                Object value = CollectExpression.eval(param.value(), root);
                // 流程变量双写：root 保留 ${xxx} 直引（存量兼容），env 供 ${env.xxx}。
                // 两处指向同一值，改任一处另一处同步生效。
                context.putRootVariable(param.name(), value);
                context.putEnvVariable(param.name(), value);
            }
        }
        // 内置命名空间（放在用户变量之后，env / nodes / data 成为保留字，不被同名流程变量顶掉）：
        //   ${env.xxx}  → 流程变量表（${env.用户名} 取用户自己声明的变量）
        //   ${nodes.n3.word} → 跨节点 / 跨分支取 n3 节点的产出
        // 两张表本身是引用，注入作用域后表达式走既有的「成员访问 Map」路径，解析器零改动。
        context.putRootVariable("env", context.getEnvVariables());
        context.putRootVariable("nodes", context.getNodeOutputs());
        context.getGlobalHeaders().clear();
        context.getGlobalCookies().clear();
        if (dsl.headers() != null) {
            context.getGlobalHeaders().addAll(dsl.headers());
        }
        if (dsl.cookies() != null) {
            context.getGlobalCookies().addAll(dsl.cookies());
        }
    }

    /**
     * 把一个令牌派发为线程池上的一次异步执行。
     *
     * @param context 执行上下文
     * @param node    节点
     * @param vars    令牌作用域
     */
    private void schedule(CollectContext context, CollectNode node, Map<String, Object> vars) {
        if (!context.isRunning()) {
            return;
        }
        context.beginWork();
        context.getPool().submit(() -> {
            try {
                runToken(context, node, vars);
            } catch (Exception ex) {
                log.warn("[collect] 令牌执行异常 task={} node={}", context.getTaskNo(), node.id(), ex);
            } finally {
                context.endWork();
            }
        });
    }

    /**
     * 展开一个节点的迭代，每个迭代是一个独立令牌。
     *
     * @param context 执行上下文
     * @param node    节点
     * @param vars    令牌入参作用域
     */
    private void runToken(CollectContext context, CollectNode node, Map<String, Object> vars) {
        CollectShapeExecutor executor = registry.get(node.shape());
        if (executor == null) {
            String message = "节点类型未注册: " + node.shape();
            Map<String, Object> scope = new LinkedHashMap<>(vars);
            scope.put(VAR_EXCEPTION, message);
            context.getSink().nodeLog(node, 0, CollectTaskStatus.FAILURE, 0L, message, null);
            dispatch(context, node, scope, true);
            return;
        }
        List<Object> items = resolveLoopItems(node, vars);
        if (items.isEmpty()) {
            // 空在「哪」要能一眼看出：把循环参数的求值结果带上（循环集合=… → 0 项），
            // 否则日志里只有一句「集合为空」，用户还得回设计器翻参数
            String detail = inputDetail(node, vars, true);
            context.getSink().nodeLog(node, 0, CollectTaskStatus.SKIPPED, 0L,
                    "循环集合为空，本节点与下游不执行", detail.isEmpty() ? null : detail);
            return;
        }
        if (items.size() > 1 && node.boolParam(PARAM_LOOP_PARALLEL, false)) {
            for (int iteration = 0; iteration < items.size(); iteration++) {
                int round = iteration;
                scheduleIteration(context, executor, node, vars, items, round);
            }
            return;
        }
        for (int iteration = 0; iteration < items.size(); iteration++) {
            if (!context.isRunning()) {
                return;
            }
            runIteration(context, executor, node, vars, items, iteration);
        }
    }

    /**
     * 并行模式下把一次迭代作为独立令牌入池。
     *
     * @param context  执行上下文
     * @param executor 节点执行器
     * @param node     节点
     * @param vars     令牌入参作用域
     * @param items    迭代元素
     * @param iteration 轮次
     */
    private void scheduleIteration(CollectContext context, CollectShapeExecutor executor, CollectNode node,
                                   Map<String, Object> vars, List<Object> items, int iteration) {
        if (!context.isRunning()) {
            return;
        }
        context.beginWork();
        context.getPool().submit(() -> {
            try {
                runIteration(context, executor, node, vars, items, iteration);
            } catch (Exception ex) {
                log.warn("[collect] 迭代执行异常 task={} node={}", context.getTaskNo(), node.id(), ex);
            } finally {
                context.endWork();
            }
        });
    }

    /**
     * 执行一次迭代并向后派发。
     *
     * @param context   执行上下文
     * @param executor  节点执行器
     * @param node      节点
     * @param vars      令牌入参作用域
     * @param items     迭代元素
     * @param iteration 轮次
     */
    private void runIteration(CollectContext context, CollectShapeExecutor executor, CollectNode node,
                              Map<String, Object> vars, List<Object> items, int iteration) {
        Map<String, Object> scope = newScope(node, vars, items, iteration);
        long start = System.currentTimeMillis();
        boolean failed;
        try {
            executeNode(executor, node, context, scope);
            failed = false;
            scope.remove(VAR_EXCEPTION);
            // 节点产出落地：${data} 别名、dataJson 派生、跨节点留存
            bindOutput(context, executor, node, scope);
        } catch (Exception ex) {
            failed = true;
            String message = describe(ex);
            scope.put(VAR_EXCEPTION, message);
            log.warn("[collect] 节点执行失败 task={} node={} shape={} err={}",
                    context.getTaskNo(), node.id(), node.shape(), message);
        }
        context.countNode();
        long cost = System.currentTimeMillis() - start;
        // 节点内部详情：轮次（多轮时）+ 入参求值 —— 用户反馈「每个节点内部的也需要输出到日志」，
        // 出参列原先只有本节点改写/新增的变量，「拿什么配置执行、循环跑到第几轮」不可见
        String internal = internalDetail(node, scope, items.size() > 1, iteration, items.size());
        if (failed) {
            context.getSink().nodeLog(node, iteration, CollectTaskStatus.FAILURE, cost,
                    String.valueOf(scope.get(VAR_EXCEPTION)), internal.isEmpty() ? null : internal);
            dispatch(context, node, scope, failed);
            return;
        }
        context.getSink().nodeLog(node, iteration, CollectTaskStatus.SUCCESS, cost,
                safeSummary(executor, node, scope), joinDetail(internal, preview(node, context, vars, scope)));
        if (CollectExpression.truthy(scope.get(VAR_STOP))) {
            context.getSink().nodeLog(node, iteration, CollectTaskStatus.SKIPPED, 0L,
                    "本节点已终止该分支，下游未执行", null);
            return;
        }
        dispatch(context, node, scope, false);
    }

    /**
     * 执行单个节点，支持节点级超时与超时策略。
     *
     * <p>节点配了 {@code timeout} 时放入独立线程等待；超时后：
     * {@code timeoutPolicy=extend} 自动延长一次相同时长，{@code timeoutPolicy=end}
     * 直接抛错结束该分支。执行器自身有 timeout 参数时二者共用同一个值。</p>
     *
     * @param executor 节点执行器
     * @param node     节点
     * @param context  执行上下文
     * @param scope    本次迭代作用域
     * @throws Exception 节点执行失败或超时
     */
    private void executeNode(CollectShapeExecutor executor, CollectNode node, CollectContext context,
                             Map<String, Object> scope) throws Exception {
        long timeout = node.longParam(PARAM_NODE_TIMEOUT, 0L);
        if (timeout <= 0L) {
            executor.execute(node, context, scope);
            return;
        }
        String policy = node.param(PARAM_NODE_TIMEOUT_POLICY);
// [P3C C1 豁免] 场景化创建线程，需文档化
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = single.submit(() -> {
                try {
                    executor.execute(node, context, scope);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            try {
                future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutEx) {
                future.cancel(true);
                if (TIMEOUT_POLICY_EXTEND.equalsIgnoreCase(policy)) {
                    try {
                        future.get(timeout, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException second) {
                        throw new IllegalStateException("节点执行超时（已自动延长一次）: " + node.id());
                    }
                } else {
                    throw new IllegalStateException("节点执行超时，已结束该分支: " + node.id());
                }
            }
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception e) {
                throw e;
            }
            throw new IllegalStateException(cause == null ? "节点执行失败" : cause.getMessage(), cause);
        } finally {
            single.shutdownNow();
        }
    }

    /**
     * 构造一次迭代的作用域，写入循环下标与循环元素。
     *
     * @param node      节点
     * @param vars      令牌入参作用域
     * @param items     迭代元素
     * @param iteration 轮次
     * @return 本次迭代独立作用域
     */
    private Map<String, Object> newScope(CollectNode node, Map<String, Object> vars,
                                         List<Object> items, int iteration) {
        Map<String, Object> scope = new LinkedHashMap<>(vars);
        if (node.param(PARAM_LOOP_COUNT).isEmpty()) {
            return scope;
        }
        scope.put(nonDefault(node.param(PARAM_LOOP_INDEX), "index"), (long) iteration);
        scope.put(nonDefault(node.param(PARAM_LOOP_ITEM_VARIABLE), "item"), items.get(iteration));
        return scope;
    }

    /**
     * 节点产出落地：写 {@code data} 别名、派生 {@code dataJson}、登记跨节点留存。
     *
     * <p>变量模型的核心。统一约定「{@code ${data}} 恒指向上一个节点的产出」，
     * 与节点自己把产出命名成什么无关：</p>
     * <ul>
     *   <li>节点按「输出名」（{@code outputVariable}，默认 {@code data}）写产出；
     *       引擎再把它别名到 {@code data}，所以下游不管上游输出名叫什么，
     *       一律 {@code ${data}} 就能拿到。多源分叉下各令牌作用域是复制关系，
     *       不会互相覆盖。</li>
     *   <li>{@code dataJson} 是 {@code data} 的 JSON 形态：{@code data} 已是
     *       Map/List 时就是它本身，是 JSON 字符串时解析成结构，解析不了落成原串。
     *       由引擎派生、不占变量位，用户不用先写一句「转 JSON」。</li>
     *   <li>产出同时登记进 {@link CollectContext#getNodeOutputs() 节点产出表}，
     *       供 {@code ${nodes.节点ID.字段}} 跨节点、跨分支引用。</li>
     * </ul>
     *
     * @param context 执行上下文
     * @param node    节点
     * @param scope   本次迭代作用域
     */
    private void bindOutput(CollectContext context, CollectShapeExecutor executor,
                            CollectNode node, Map<String, Object> scope) {
        // 输出名：优先用户配置（设计器「输出名」），为空时用执行器的兜底名
        //（rows / resp / linkList…），引擎再统一别名成 data
        String configured = node.param(OUTPUT_VARIABLE);
        String outName = (configured == null || configured.isBlank())
                ? executor.defaultOutput(node) : configured.trim();
        Object value = scope.get(outName);
        if (value == null) {
            return;
        }
        scope.putIfAbsent("data", value);
        scope.putIfAbsent("dataJson", toJsonValue(value));
        context.putNodeOutput(node.id(), value);
        // 默认落采集记录：流程没有「采集平台(output)」节点时，
        // 把行集合产出（List<Map>，如 extract/sql 的 rows）逐条写入采集结果表，
        // 使运行记录「产出 N 条」与采集结果页不依赖编排期加节点。非行集合产出不落。
        if (context.isAutoRecord() && value instanceof List<?> rows && !rows.isEmpty()
                && rows.get(0) instanceof Map<?, ?>) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> rowMap) || rowMap.isEmpty()) {
                    continue;
                }
                CollectRecordEntry entry = new CollectRecordEntry();
                entry.setNodeId(node.id());
                entry.setUrl(text(scope.get("url")));
                for (Map.Entry<?, ?> cell : rowMap.entrySet()) {
                    entry.getCells().add(new CollectParamDTO(String.valueOf(cell.getKey()),
                            CollectFunctions.stringify(cell.getValue()), null));
                }
                if (!context.offerRecord(entry)) {
                    // 达到单任务记录上限：不再继续落，但不中断流程主体
                    break;
                }
            }
        }
    }

    /**
     * 转字符串，空值返回空串。
     *
     * @param value 值
     * @return 字符串
     */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 派生 {@code dataJson}：{@code data} 已是结构就原样，是字符串就尝试解析成结构。
     *
     * <p>解析失败落成原串而不是 null —— 下游 {@code ${dataJson.xxx}} 取不到时
     * 求值给 null，与「变量未定义」同语义，不会把整条链拖崩。</p>
     *
     * @param value 节点产出
     * @return JSON 形态值
     */
    private static Object toJsonValue(Object value) {
        if (value instanceof Map || value instanceof List || value == null) {
            return value;
        }
        if (value instanceof String text) {
            Object parsed = CollectJson.parseAny(text);
            return parsed == null ? value : parsed;
        }
        return value;
    }

    /**
     * 按边的异常流向与条件派发下游令牌。
     *
     * <p>失败是否算「被接住」只看有没有显式的异常处理分支（{@code exceptionFlow=1}）：
     * 默认的 {@code exceptionFlow=0} 只是「无论成败都往下走」的路由声明，下游并没有承诺兜住异常，
     * 若把它也算作接住，节点失败会被静默吞掉——任务终态恒为 SUCCESS，
     * 以 FAILURE 为条件的执行通知也永远不会投递。</p>
     *
     * @param context 执行上下文
     * @param node    当前节点
     * @param vars    当前令牌作用域
     * @param failed  当前令牌是否失败
     */
    private void dispatch(CollectContext context, CollectNode node, Map<String, Object> vars, boolean failed) {
        boolean rescued = false;
        for (CollectEdge edge : node.getOutEdges()) {
            boolean traversed = failed ? edge.allowWhenException()
                    : edge.allowWhenNormal() && CollectExpression.evalCondition(edge.condition(), vars);
            if (!traversed) {
                context.getSink().nodeLog(edge.getTarget(), 0, CollectTaskStatus.SKIPPED, 0L,
                        node.name() + " → " + edge.getTarget().name()
                                + (failed ? " 上游异常且本边不允许异常流转" : " 连线条件不成立: " + edge.condition()),
                        null);
                rescued = rescued || failed && edge.isExceptionBranch();
                continue;
            }
            Map<String, Object> child = new LinkedHashMap<>(context.getRootVariables());
            if (edge.transmitVariable()) {
                child.putAll(vars);
            }
            schedule(context, edge.getTarget(), child);
            rescued = rescued || failed && edge.isExceptionBranch();
        }
        if (failed && !rescued) {
            context.markFailure(String.valueOf(vars.get(VAR_EXCEPTION)));
        }
    }

    /**
     * 解析循环集合：次数、集合、元素集、数组、多行文本或单次执行。
     *
     * @param node 节点
     * @param vars 令牌作用域
     * @return 迭代元素列表，未配置循环参数时返回单元素列表
     */
    private List<Object> resolveLoopItems(CollectNode node, Map<String, Object> vars) {
        String raw = node.param(PARAM_LOOP_COUNT);
        if (raw.isEmpty()) {
            return List.of("");
        }
        Object value = CollectExpression.eval(raw, vars);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Number number) {
            return rounds(number.intValue());
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof org.jsoup.select.Elements elements) {
            List<Object> items = new ArrayList<>();
            for (org.jsoup.nodes.Element element : elements) {
                items.add(element);
            }
            return items;
        }
        if (value.getClass().isArray()) {
            int length = ReflectUtils.arrayLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(ReflectUtils.arrayGet(value, i));
            }
            return items;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        try {
            return rounds(Integer.parseInt(text));
        } catch (NumberFormatException ex) {
            // 多行文本当循环集合：同样先归一「字面换行」，否则整段文本只会得到一个循环项
            return new ArrayList<>(CollectText.lines(text));
        }
    }

    /**
     * 生成次数循环的下标列表。
     *
     * @param count 次数
     * @return 次数不超过 0 时返回空列表，否则返回 0..count-1
     */
    private List<Object> rounds(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<Object> rounds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rounds.add((long) i);
        }
        return rounds;
    }

    /**
     * 生成出参预览，只保留本令牌新增或改变的变量，避免整页 HTML 落库。
     *
     * <p>数据库节点是特例：它的产出（目标表 / 写入行数 / 清空行数）只存在
     * {@code _written} / {@code _cleared} 这类下划线暂存变量里，按上面的"只收新增变量"
     * 通用规则会被整条跳过，出参预览恒为空（实测 GitHub 例子 n4）。所以这里对 shape=database
     * 单独拼一段结构化出参，列名与行数都进预览 —— 采集日志"写了哪张表、写了多少行"才有处可查。
     * 其他形状一行不动。</p>
     *
     * @param context  执行上下文
     * @param incoming 令牌入参作用域
     * @param scope    本次迭代作用域
     * @return 预览文本
     */
    private String preview(CollectNode node, CollectContext context, Map<String, Object> incoming,
                             Map<String, Object> scope) {
        // 数据库节点特判：把目标表 / 写入方式 / 行数拼进预览，见方法 javadoc。
        String database = databasePreview(node, scope);
        int limit = Math.max(32, context.getProperties().getPreviewLength());
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : scope.entrySet()) {
            if (entry.getKey().startsWith("_") || entry.getValue() == null) {
                continue;
            }
            Object before = incoming.get(entry.getKey());
            if (before != null && before.equals(entry.getValue())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            Object value = entry.getValue();
            builder.append(entry.getKey()).append('=');
            if (value instanceof Collection || value instanceof org.jsoup.select.Elements) {
                builder.append('[').append(CollectFunctions.size(value)).append(" 项]");
            } else {
                String text = CollectFunctions.stringify(value).replaceAll("\\s+", " ");
                builder.append(text.length() > limit ? text.substring(0, limit) + "…" : text);
            }
            if (builder.length() > limit * 3) {
                break;
            }
        }
        // 数据库节点的结构化出参拼在最前面：表名与行数是排查"数据去哪了"的第一信息，
        // 通用变量预览（集合只记条数）反而在后。
        String result = database == null || database.isEmpty()
                ? builder.toString()
                : (builder.length() == 0 ? database : database + "; " + builder);
        return result.length() > limit * 4 ? result.substring(0, limit * 4) : result;
    }

    /**
     * 数据库节点的结构化出参：目标表 / 写入方式 / 写入行数 / 清空行数 / 更新行数。
     *
     * <p>执行器只把行数写进 {@code _written} / {@code _cleared} / {@code _updated} /
     * {@code _inserted} 下划线暂存变量，通用预览规则会整条跳过，这里直接读出来拼。
     * 非 database 形状返回空串；表名取不到时也不编造，只给行数。</p>
     *
     * @param node  当前节点
     * @param scope 本次迭代作用域
     * @return 结构化出参，非 database 形状返回空串
     */
    private String databasePreview(CollectNode node, Map<String, Object> scope) {
        if (node == null || !"database".equals(node.shape())) {
            return "";
        }
        String table = "";
        try {
            table = CollectExpression.evalText(node.param("table"), scope).trim();
        } catch (Exception ex) {
            // 表名表达式求值失败不影响行数信息
        }
        StringBuilder preview = new StringBuilder("目标表=").append(table.isEmpty() ? "未知" : table);
        // 写入方式读节点参数（执行器不往 scope 写这个值）：append / replace / update。
        try {
            String mode = CollectExpression.evalText(node.param("mode"), scope).trim();
            if (!mode.isEmpty()) {
                preview.append(" 方式=").append(mode);
            }
        } catch (Exception ex) {
            // 方式读不到不影响行数信息
        }
        appendCount(preview, "清空", scope.get("_cleared"));
        appendCount(preview, "写入", scope.get("_written"));
        appendCount(preview, "更新", scope.get("_updated"));
        appendCount(preview, "新增", scope.get("_inserted"));
        return preview.toString();
    }

    /**
     * 出参行数片段：只收整数，非数字一律跳过不编造。
     *
     * @param preview 出参拼接器
     * @param label   行数含义
     * @param value   暂存变量值
     */
    private void appendCount(StringBuilder preview, String label, Object value) {
        if (value instanceof Number number) {
            preview.append(' ').append(label).append('=').append(number.longValue()).append("行");
        }
    }

    /**
     * 汇总节点内部详情：轮次（多轮时）+ 入参求值。
     *
     * <p>用户反馈「不详细，每个节点内部的也需要输出到日志」。出参列原先只展示节点
     * 改写/新增的变量 —— 执行器内部（重试、分批）走不到这里，但引擎侧的上下文
     * （这轮拿什么配置执行、循环跑到第几轮）是通用的、每个节点都该有：失败行
     * 尤其依赖它（失败时没有出参预览，入参就是唯一的现场）。</p>
     *
     * @param node       节点
     * @param scope      本次迭代作用域（已含循环下标与元素变量）
     * @param multiRound 本次是否处于多轮循环
     * @param iteration  当前轮次（0 起）
     * @param total      轮次总数
     * @return 详情文本；无内容时返回空串
     */
    private String internalDetail(CollectNode node, Map<String, Object> scope,
                                  boolean multiRound, int iteration, int total) {
        List<String> parts = new ArrayList<>();
        if (multiRound) {
            parts.add("round=" + (iteration + 1) + "/" + total);
        }
        String input = inputDetail(node, scope, false);
        if (!input.isEmpty()) {
            parts.add(input);
        }
        return String.join(" | ", parts);
    }

    /**
     * 节点入参详情：本节点声明参数的求值结果压成 {@code in: a=1; b=2} 单行。
     *
     * <p>值求值后展示（而不是原样表达式）——「source=${projects}」看不出东西，
     * 「source=[100 项]」才说明这节点实际拿到什么。循环四参数常规行省略
     * （轮次由 round 提供），空集合跳过行传 includeLoop=true 把「空在哪」摊开。</p>
     *
     * @param node        节点
     * @param scope       求值作用域
     * @param includeLoop 是否连循环四参数一并列出
     * @return 入参详情；没有任何可展示参数时返回空串
     */
    private String inputDetail(CollectNode node, Map<String, Object> scope, boolean includeLoop) {
        StringBuilder builder = new StringBuilder("in: ");
        for (CollectParamDTO param : node.params()) {
            String name = param.name();
            String raw = param.value();
            if (name == null || name.isBlank() || raw == null || raw.isBlank()) {
                continue;
            }
            if (!includeLoop && isLoopParam(name)) {
                continue;
            }
            if (builder.length() > INPUT_DETAIL_LIMIT) {
                break;
            }
            if (builder.length() > 4) {
                builder.append("; ");
            }
            builder.append(name).append('=');
            Object value;
            try {
                value = CollectExpression.eval(raw, scope);
            } catch (Exception ex) {
                // 求值不了就展示原文：日志是排查现场，不能因为表达式解析失败整行消失
                value = raw;
            }
            builder.append(describeValue(value));
        }
        if (builder.length() <= 4) {
            return "";
        }
        return builder.length() > INPUT_DETAIL_LIMIT
                ? builder.substring(0, INPUT_DETAIL_LIMIT) + "…" : builder.toString();
    }

    /**
     * 判断是否循环四参数。
     *
     * @param name 参数名
     * @return 是循环参数返回 true
     */
    private static boolean isLoopParam(String name) {
        return PARAM_LOOP_COUNT.equals(name) || PARAM_LOOP_INDEX.equals(name)
                || PARAM_LOOP_ITEM_VARIABLE.equals(name) || PARAM_LOOP_PARALLEL.equals(name);
    }

    /**
     * 参数值压成单行可读文本：集合报项数、标量去空白并按 {@link #INPUT_VALUE_LIMIT} 截断。
     *
     * @param value 求值结果
     * @return 可读文本
     */
    private String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Collection<?> collection) {
            return "[" + collection.size() + " 项]";
        }
        if (value instanceof org.jsoup.select.Elements elements) {
            return "[" + elements.size() + " 项]";
        }
        if (value.getClass().isArray()) {
            return "[" + java.lang.reflect.Array.getLength(value) + " 项]";
        }
        String text = CollectFunctions.stringify(value).replaceAll("\\s+", " ");
        return text.length() > INPUT_VALUE_LIMIT ? text.substring(0, INPUT_VALUE_LIMIT) + "…" : text;
    }

    /**
     * 拼接内部详情与出参预览并按 {@link #OUTPUT_DETAIL_LIMIT} 截断。
     *
     * <p>入参在前：预览可能顶满 2000 字符（整页 HTML），放前面才能保证「拿什么配置
     * 执行」始终可读；预览被截掉的尾部只是响应体残段，语义无损。</p>
     *
     * @param internal 内部详情（轮次 + 入参）
     * @param produced 出参预览
     * @return 拼接结果；两者都为空时返回空串
     */
    private String joinDetail(String internal, String produced) {
        String joined;
        if (internal.isEmpty()) {
            joined = produced;
        } else if (produced.isEmpty()) {
            joined = internal;
        } else {
            joined = internal + " | " + produced;
        }
        if (joined.length() > OUTPUT_DETAIL_LIMIT) {
            return joined.substring(0, OUTPUT_DETAIL_LIMIT) + "…";
        }
        return joined;
    }

    /**
     * 取执行器摘要，异常降级为空。
     *
     * @param executor 执行器
     * @param node     节点
     * @param scope    作用域
     * @return 摘要文本
     */
    private String safeSummary(CollectShapeExecutor executor, CollectNode node, Map<String, Object> scope) {
        try {
            return executor.summary(node, scope);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 异常说明压成单行。
     *
     * @param ex 异常
     * @return 单行说明
     */
    private String describe(Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.replaceAll("\\s+", " ");
    }

    /**
     * 参数值兜底。
     *
     * @param value    参数值
     * @param fallback 兜底值
     * @return 非空参数值
     */
    private String nonDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 汇总执行结果。
     *
     * @param context 执行上下文
     * @param begin   开始毫秒
     * @param status  状态
     * @param error   失败说明
     * @return 执行结果
     */
    private CollectRunResult finish(CollectContext context, long begin, String status, String error) {
        CollectRunResult result = new CollectRunResult();
        result.setTaskId(context.getTaskId());
        result.setTaskNo(context.getTaskNo());
        result.setStatus(status);
        result.setCostMs(System.currentTimeMillis() - begin);
        result.setNodeCount(context.getNodeCount().get());
        result.setRequestCount(context.getRequestCount().get());
        result.setRecordCount(context.getRecordCount().get());
        result.setErrorMessage(error);
        result.getPreview().addAll(context.getRecords());
        result.getVariables().putAll(context.getRootVariables());
        return result;
    }

    /**
     * 停止任务。
     *
     * @param taskId 任务ID
     * @return 命中运行中任务返回 true
     */
    public boolean cancel(Long taskId) {
        CollectContext context = running.get(taskId);
        if (context == null) {
            return false;
        }
        context.cancel();
        return true;
    }

    /**
     * 任务是否仍在执行。
     *
     * @param taskId 任务ID
     * @return 执行中返回 true
     */
    public boolean isRunning(Long taskId) {
        return running.containsKey(taskId);
    }

    /**
     * 当前执行中的任务数。
     *
     * @return 执行中任务数
     */
    public int runningCount() {
        return running.size();
    }

    /**
     * 本机某流程正在执行的任务数。
     *
     * @param flowId 流程ID
     * @return 执行中任务数
     */
    public int inflightOfFlow(Integer flowId) {
        if (flowId == null) {
            return 0;
        }
        int total = 0;
        for (CollectContext context : running.values()) {
            if (flowId.equals(context.getFlowId())) {
                total++;
            }
        }
        return total;
    }

    /**
     * 某流程是否正在执行。
     *
     * @param flowId 流程ID
     * @return 执行中返回 true
     */
    public boolean isFlowRunning(Integer flowId) {
        return inflightOfFlow(flowId) > 0;    }}