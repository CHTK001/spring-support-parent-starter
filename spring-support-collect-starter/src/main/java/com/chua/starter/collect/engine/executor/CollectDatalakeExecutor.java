package com.chua.starter.collect.engine.executor;

import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.spi.pipeline.PipelineConfig;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectExpression;
import com.chua.starter.collect.engine.model.CollectNode;
import com.chua.starter.collect.engine.spi.CollectShapeExecutor;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.engine.spi.CollectShapeMetas;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采集到数据中台的输出适配器：只调用底层管理与执行契约，不持有管线配置与表。
 *
 * <p>以同步方式逐条交给宿主已有引擎，避免一次复制整个集合或另建无界缓冲队列。
 * 任一目标投递失败即抛错，由采集的异常边处理；已成功的外部写入不回滚，不自动重试。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Component
@ConditionalOnProperty(name = "datalake.enabled", havingValue = "true")
public class CollectDatalakeExecutor implements CollectShapeExecutor {

    /** 宿主数据中台延迟引用，避免节点注册阶段提前启动数据面。 */
    private final ObjectProvider<DatalakeServer> servers;

    /**
     * 构造输出适配器。
     * @param servers 宿主数据中台
     */
    public CollectDatalakeExecutor(ObjectProvider<DatalakeServer> servers) {
        this.servers = servers;
    }

    @Override
    public String supportShape() {
        return "datalake";
    }

    @Override
    public CollectShapeMeta meta() {
        return CollectShapeMetas.with(
                CollectShapeMetas.of(supportShape(), "数据中台投递", "输出", "connection",
                        "把对象或对象集合投递到已保存的数据管线；逐条确认，失败走异常路径。试运行也会真实投递"),
                CollectShapeMetas.required("pipelineId", "数据管线", "pipeline", "在数据中台中维护管线与投递目标"),
                CollectShapeMetas.required("source", "来源数据", "textarea", "对象或对象集合表达式，例如 ${data}"),
                CollectShapeMetas.outputVariable("投递结果，含 pipelineId 与 delivered"));
    }

    @Override
    public void execute(CollectNode node, CollectContext context, Map<String, Object> variables) {
        DatalakeServer server = servers.getIfAvailable();
        if (server == null) {
            throw new IllegalStateException("宿主未装配数据中台");
        }
        String pipelineId = CollectExpression.evalText(node.param("pipelineId"), variables).trim();
        PipelineConfig config = configOf(server, pipelineId);
        PipelineConfig.PipelineStageConfig stage = config.getStages() == null ? null : config.getStages().get("default");
        if (stage == null || stage.getSink() == null || stage.getSink().isEmpty()) {
            throw new IllegalArgumentException("数据管线未配置投递目标: " + pipelineId);
        }
        for (Map<String, Object> sink : stage.getSink()) {
            if (sink == null || !(sink.get("type") instanceof String type)
                    || server.getSinkManager().allSinks().stream().noneMatch(target -> type.equals(target.type()))) {
                throw new IllegalArgumentException("数据管线含未注册的投递目标: " + pipelineId);
            }
        }
        Object input = CollectExpression.eval(node.param("source"), variables);
        Iterable<?> rows = input instanceof Map<?, ?> ? List.of(input)
                : input instanceof Iterable<?> iterable ? iterable : null;
        if (rows == null) {
            throw new IllegalArgumentException("来源数据必须是对象或对象集合");
        }
        long delivered = 0;
        for (Object item : rows) {
            if (!context.isRunning() || Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("数据投递已停止，已确认 " + delivered + " 条");
            }
            if (!(item instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException("数据集合含非对象元素，已确认 " + delivered + " 条");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            row.forEach((key, value) -> values.put(String.valueOf(key), value));
            DataEnvelope envelope = new DataEnvelope(values);
            envelope.setPipelineId(pipelineId);
            envelope.setTimestamp(System.currentTimeMillis());
            envelope.setTopics(Set.of("collect"));
            envelope.setTraceId(context.getTaskNo() + ":" + node.id() + ":" + delivered);
            server.getPipelineEngine().execute(pipelineId, envelope);
            // 引擎会继续其余 Sink，最终 state 可能被后续成功覆盖，必须检查整条执行轨迹。
            long successes = envelope.getTrace().stream().filter(trace -> trace.startsWith("[Sink] written to type=")).count();
            if (successes != stage.getSink().size()
                    || envelope.getTrace().stream().anyMatch(trace -> trace.startsWith("[Sink][ERROR]"))) {
                throw new IllegalStateException("管线投递失败: " + pipelineId + "，已确认 " + delivered
                        + " 条；当前条可能部分成功，请核对目标后重试");
            }
            delivered++;
        }
        String output = node.param("outputVariable").trim();
        variables.put(output.isEmpty() ? "data" : output, Map.of("pipelineId", pipelineId, "delivered", delivered));
    }

    /**
     * 从底层管理器取编译配置；不直接读取数据中台的持久层。
     * @param server 宿主服务
     * @param id 管线标识
     * @return 已存在的配置
     */
    private PipelineConfig configOf(DatalakeServer server, String id) {
        if (id.isEmpty() || !(server.getPipelineManager() instanceof DefaultPipelineManager manager)) {
            throw new IllegalArgumentException("数据管线不可用: " + id);
        }
        PipelineConfig config = manager.getCompiledPipeline(id);
        if (config == null) {
            throw new IllegalArgumentException("数据管线不存在: " + id);
        }
        return config;
    }
}
