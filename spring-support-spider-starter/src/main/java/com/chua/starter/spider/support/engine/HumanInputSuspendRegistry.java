package com.chua.starter.spider.support.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工介入节点挂起注册表。
 *
 * <p>以 {@code "taskId:nodeId"} 为键，存储等待用户输入的 {@link CompletableFuture}。
 * 执行线程通过 {@link #register} 注册并阻塞，前端提交输入后通过 {@link #complete} 恢复执行。</p>
 *
 * @author CH
 */
@Component
public class HumanInputSuspendRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /**
     * 注册一个新的挂起 Future，并返回它供调用方阻塞等待。
     *
     * @param taskId 任务 ID
     * @param nodeId 节点 ID
     * @return 新建的 CompletableFuture
     */
    public CompletableFuture<String> register(Long taskId, String nodeId) {
        String key = buildKey(taskId, nodeId);
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(key, future);
        return future;
    }

    /**
     * 完成指定节点的挂起 Future，恢复执行线程。
     *
     * @param taskId    任务 ID
     * @param nodeId    节点 ID
     * @param userInput 用户提交的输入值
     * @return {@code true} 表示成功找到并完成；{@code false} 表示未找到对应 Future
     */
    public boolean complete(Long taskId, String nodeId, String userInput) {
        String key = buildKey(taskId, nodeId);
        CompletableFuture<String> future = pending.remove(key);
        if (future == null) {
            return false;
        }
        future.complete(userInput);
        return true;
    }

    /**
     * 判断指定节点是否处于挂起等待状态。
     */
    public boolean isPending(Long taskId, String nodeId) {
        return pending.containsKey(buildKey(taskId, nodeId));
    }

    private String buildKey(Long taskId, String nodeId) {
        return taskId + ":" + nodeId;
    }
}
