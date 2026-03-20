package com.chua.starter.queue.transaction;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 事务消息模板
 * <p>
 * 支持在Spring事务提交后发送消息，确保消息发送与数据库操作的一致性
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalMessageTemplate {

    private final MessageTemplate messageTemplate;

    /**
     * 在事务提交后发送消息
     * <p>
     * 如果当前没有事务，则立即发送
     * </p>
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @return 发送结果（如果在事务中，返回null）
     */
    public SendResult sendAfterCommit(String destination, Object payload) {
        return sendAfterCommit(destination, payload, null);
    }

    /**
     * 在事务提交后发送消息（带消息头）
     *
     * @param destination 目标地址
     * @param payload     消息内容
     * @param headers     消息头
     * @return 发送结果（如果在事务中，返回null）
     */
    public SendResult sendAfterCommit(String destination, Object payload, Map<String, Object> headers) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // 没有事务，立即发送
            return messageTemplate.send(destination, payload, headers);
        }

        // 在事务中，注册同步器
        TransactionSynchronizationManager.registerSynchronization(
            new MessageSendSynchronization(destination, payload, headers));

        log.debug("[Queue] 消息已注册到事务: destination={}", destination);
        return null;
    }

    /**
     * 在事务提交后批量发送消息
     *
     * @param messages 消息列表
     */
    public void sendBatchAfterCommit(List<TransactionalMessage> messages) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // 没有事务，立即发送
            for (TransactionalMessage msg : messages) {
                messageTemplate.send(msg.getDestination(), msg.getPayload(), msg.getHeaders());
            }
            return;
        }

        // 在事务中，注册同步器
        TransactionSynchronizationManager.registerSynchronization(
            new BatchMessageSendSynchronization(messages));

        log.debug("[Queue] {} 条消息已注册到事务", messages.size());
    }

    /**
     * 消息发送同步器
     */
    private class MessageSendSynchronization implements TransactionSynchronization {
        private final String destination;
        private final Object payload;
        private final Map<String, Object> headers;

        public MessageSendSynchronization(String destination, Object payload, Map<String, Object> headers) {
            this.destination = destination;
            this.payload = payload;
            this.headers = headers;
        }

        @Override
        public void afterCommit() {
            try {
                SendResult result = messageTemplate.send(destination, payload, headers);
                if (result.isSuccess()) {
                    log.debug("[Queue] 事务提交后消息发送成功: destination={}", destination);
                } else {
                    log.error("[Queue] 事务提交后消息发送失败: destination={}, error={}",
                        destination, result.getError());
                }
            } catch (Exception e) {
                log.error("[Queue] 事务提交后消息发送异常: destination={}", destination, e);
            }
        }
    }

    /**
     * 批量消息发送同步器
     */
    private class BatchMessageSendSynchronization implements TransactionSynchronization {
        private final List<TransactionalMessage> messages;

        public BatchMessageSendSynchronization(List<TransactionalMessage> messages) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public void afterCommit() {
            int successCount = 0;
            int failureCount = 0;

            for (TransactionalMessage msg : messages) {
                try {
                    SendResult result = messageTemplate.send(
                        msg.getDestination(), msg.getPayload(), msg.getHeaders());
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        failureCount++;
                        log.error("[Queue] 批量消息发送失败: destination={}, error={}",
                            msg.getDestination(), result.getError());
                    }
                } catch (Exception e) {
                    failureCount++;
                    log.error("[Queue] 批量消息发送异常: destination={}", msg.getDestination(), e);
                }
            }

            log.info("[Queue] 事务提交后批量消息发送完成: 成功={}, 失败={}", successCount, failureCount);
        }
    }

    /**
     * 事务消息
     */
    public static class TransactionalMessage {
        private final String destination;
        private final Object payload;
        private final Map<String, Object> headers;

        public TransactionalMessage(String destination, Object payload) {
            this(destination, payload, null);
        }

        public TransactionalMessage(String destination, Object payload, Map<String, Object> headers) {
            this.destination = destination;
            this.payload = payload;
            this.headers = headers;
        }

        public String getDestination() {
            return destination;
        }

        public Object getPayload() {
            return payload;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }
    }
}
