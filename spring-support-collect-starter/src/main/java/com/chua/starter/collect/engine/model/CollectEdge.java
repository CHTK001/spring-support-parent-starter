package com.chua.starter.collect.engine.model;

import com.chua.starter.collect.dto.CollectEdgeDTO;
import lombok.Getter;

/**
 * 可执行连线。
 *
 * <p>连线携带三个语义：流转条件、异常流向与是否传递上游变量。异常流向沿用以异常分支
 * 为语义的取值，与 spider-flow 的连线取值保持一致：0 总是流转、1 仅上游异常时流转、
 * 2 仅上游正常时流转。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class CollectEdge {

    /**
     * 无论上游是否异常都流转。
     */
    public static final String FLOW_ALWAYS = "0";

    /**
     * 仅上游异常时流转，用于异常分支。
     */
    public static final String FLOW_EXCEPTION = "1";

    /**
     * 仅上游正常时流转。
     */
    public static final String FLOW_NORMAL = "2";

    /**
     * 原始 DSL 连线。
     */
    private final CollectEdgeDTO source;

    /**
     * 目标节点。
     */
    private final CollectNode target;

    /**
     * 构造可执行连线。
     *
     * @param source DSL 连线
     * @param target 目标节点
     */
    public CollectEdge(CollectEdgeDTO source, CollectNode target) {
        this.source = source;
        this.target = target;
    }

    /**
     * 连线ID。
     *
     * @return 连线ID
     */
    public String id() {
        return source.id();
    }

    /**
     * 流转条件表达式，未配置返回空串。
     *
     * @return 条件表达式
     */
    public String condition() {
        String condition = source.condition();
        return condition == null ? "" : condition.trim();
    }

    /**
     * 异常流向。
     *
     * @return 流向编码，未配置返回总是流转
     */
    public String exceptionFlow() {
        String flow = source.exceptionFlow();
        return flow == null || flow.isBlank() ? FLOW_ALWAYS : flow.trim();
    }

    /**
     * 是否传递上游变量。
     *
     * @return 传递返回 true
     */
    public boolean transmitVariable() {
        return source.transmitVariable() == null || source.transmitVariable();
    }

    /**
     * 上游异常时本边是否放行。
     *
     * @return 放行返回 true
     */
    public boolean allowWhenException() {
        return FLOW_ALWAYS.equals(exceptionFlow()) || FLOW_EXCEPTION.equals(exceptionFlow());
    }

    /**
     * 上游正常时本边是否放行。
     *
     * @return 放行返回 true
     */
    public boolean allowWhenNormal() {
        return FLOW_ALWAYS.equals(exceptionFlow()) || FLOW_NORMAL.equals(exceptionFlow());
    }

    /**
     * 本边是否是显式声明的异常处理分支。
     *
     * <p>只有 {@link #FLOW_EXCEPTION}（仅上游异常时流转）代表「这条边就是用来接异常的」，
     * 失败交到这里才算被处理；{@link #FLOW_ALWAYS} 只是「无论成败都往下走」的路由声明，
     * 不代表下游有能力兜住异常，把它算作处理会让节点失败被静默吞掉、任务终态永远成功。
     * {@link #FLOW_NORMAL} 上游异常时本就不放行，不参与判定。</p>
     *
     * @return 是异常处理分支返回 true
     */
    public boolean isExceptionBranch() {
        return FLOW_EXCEPTION.equals(exceptionFlow());
    }
}
