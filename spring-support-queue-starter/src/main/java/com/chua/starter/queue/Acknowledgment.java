package com.chua.starter.queue;

/**
 * 消息确认接口
 * <p>
 * 用于手动确认或拒绝消息，支持消息队列的可靠性保证。
 * </p>
 *
 * @author CH
 * @since 2025-01-02
 */
public interface Acknowledgment {

    /**
     * 确认消息
     * <p>
     * 表示消息已成功处理，可以从队列中移除。
     * </p>
     */
    void acknowledge();

    /**
     * 拒绝消息
     * <p>
     * 表示消息处理失败，根据 requeue 参数决定是否重新入队。
     * </p>
     *
     * @param requeue 是否重新入队
     *                 - true: 消息重新入队，可以被其他消费者处理
     *                 - false: 消息被丢弃或发送到死信队列
     */
    void nack(boolean requeue);

    /**
     * 拒绝消息（不重新入队）
     * <p>
     * 等同于 nack(false)
     * </p>
     */
    default void nack() {
        nack(false);
    }

    /**
     * 拒绝消息并发送到指定的死信队列
     * <p>
     * 当消息处理失败时，可以将消息发送到指定的死信队列，而不是使用默认的死信队列。
     * </p>
     *
     * @param deadLetterQueue 死信队列名称（如果为 null 或空，则使用默认死信队列）
     * @param reason          失败原因（可选）
     */
    default void nackToDeadLetter(String deadLetterQueue, String reason) {
        // 默认实现：调用 nack(false)，由具体实现类决定如何处理死信队列
        nack(false);
    }

    /**
     * 拒绝消息并发送到指定的死信队列（无原因）
     * <p>
     * 等同于 nackToDeadLetter(deadLetterQueue, null)
     * </p>
     *
     * @param deadLetterQueue 死信队列名称
     */
    default void nackToDeadLetter(String deadLetterQueue) {
        nackToDeadLetter(deadLetterQueue, null);
    }

    /**
     * 检查消息是否已确认
     *
     * @return 是否已确认
     */
    default boolean isAcknowledged() {
        return false;
    }
}

