package com.chua.starter.spider.flow;

import com.chua.starter.spider.entity.SpiderFlowNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程执行上下文（贯穿整个流程的"变量作用域"）。
 *
 * <p>对应 spider-flow 的"流程变量"概念：每个节点可以把结果写入 ctx.var，
 * 后续节点通过 {@code ctx.getVar("name")} 读取。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpiderFlowContext {

    /**
     * 流程编码
     */
    private String flowCode;

    /**
     * 执行批次号
     */
    private String executionNo;

    /**
     * 流程变量作用域（节点间共享）
     */
    @Builder.Default
    private Map<String, Object> vars = new LinkedHashMap<>();

    /**
     * 当前正在执行的节点 key（用于调试面板高亮）
     */
    private String currentNodeKey;

    /**
     * 是否已被中断（用户停止 / 异常终止）
     */
    @Builder.Default
    private boolean interrupted = false;

    /**
     * 写入变量。
     *
     * @param key   变量名
     * @param value 变量值
     */
    public void putVar(String key, Object value) {
        if (key == null) {
            return;
        }
        vars.put(key, value);
    }

    /**
     * 读取变量（不存在返回 null）。
     *
     * @param key 变量名
     * @param <T> 期望类型
     * @return 变量值
     */
    @SuppressWarnings("unchecked")
    public <T> T getVar(String key) {
        if (key == null) {
            return null;
        }
        return (T) vars.get(key);
    }

    /**
     * 读取变量（带默认值）。
     *
     * @param key          变量名
     * @param defaultValue 默认值
     * @param <T>          期望类型
     * @return 变量值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getVar(String key, T defaultValue) {
        if (key == null || !vars.containsKey(key)) {
            return defaultValue;
        }
        T val = (T) vars.get(key);
        return val == null ? defaultValue : val;
    }

    /**
     * 中断流程。
     */
    public void interrupt() {
        this.interrupted = true;
    }
}
