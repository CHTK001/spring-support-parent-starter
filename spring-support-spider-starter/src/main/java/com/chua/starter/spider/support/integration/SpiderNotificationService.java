package com.chua.starter.spider.support.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * lay-message 消息通知集成（可选依赖）。
 *
 * <p>若 lay-message 模块可用，则在任务完成/失败/人工介入时发送通知；
 * 否则静默跳过，不影响爬虫执行。</p>
 *
 * @author CH
 */
@Slf4j
@Component
public class SpiderNotificationService {

    @Autowired(required = false)
    private Object layMessageService;

    /**
     * 发送任务完成通知。
     *
     * @param taskId   任务 ID
     * @param taskName 任务名称
     * @param success  是否成功
     * @param summary  摘要信息
     */
    public void notifyTaskFinished(Long taskId, String taskName, boolean success, String summary) {
        if (layMessageService == null) {
            log.debug("[Spider][Notify] lay-message 未集成，跳过通知 taskId={} success={}", taskId, success);
            return;
        }
        String title = success ? "爬虫任务完成" : "爬虫任务失败";
        String content = String.format("任务【%s】(ID:%d) %s。%s", taskName, taskId,
                success ? "执行完成" : "执行失败", summary != null ? summary : "");
        sendMessage(title, content);
    }

    /**
     * 发送人工介入通知。
     *
     * @param taskId   任务 ID
     * @param taskName 任务名称
     * @param nodeId   等待输入的节点 ID
     * @param prompt   提示文字
     */
    public void notifyHumanInputRequired(Long taskId, String taskName, String nodeId, String prompt) {
        if (layMessageService == null) {
            log.debug("[Spider][Notify] lay-message 未集成，跳过人工介入通知 taskId={} nodeId={}", taskId, nodeId);
            return;
        }
        String title = "爬虫任务需要人工介入";
        String content = String.format("任务【%s】(ID:%d) 节点[%s] 等待输入：%s", taskName, taskId, nodeId, prompt);
        sendMessage(title, content);
    }

    /** 是否已集成 lay-message */
    public boolean isAvailable() {
        return layMessageService != null;
    }

    private void sendMessage(String title, String content) {
        try {
            layMessageService.getClass()
                    .getMethod("send", String.class, String.class)
                    .invoke(layMessageService, title, content);
        } catch (Exception e) {
            log.debug("[Spider][Notify] lay-message 发送失败（可忽略）: {}", e.getMessage());
        }
    }
}
