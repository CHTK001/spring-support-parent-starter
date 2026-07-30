package com.chua.starter.queue;

/**
 * 消息处理器接口
 *
 * @author CH
 * @since 2025-12-25
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 处理消息
     * <p>
     * 如果 autoAck=false，需要手动调用 ack.acknowledge() 确认消息。
     * 如果处理失败，可以调用 ack.nack(requeue) 拒绝消息。
     * </p>
     *
     * @param message 消息
     * @param ack    消息确认对象（如果 autoAck=false，需要手动确认）
     */
    void handle(Message message, Acknowledgment ack);

    /**
     * 处理消息（兼容旧版本，自动确认）
     * <p>
     * 此方法已废弃，请使用 handle(Message message, Acknowledgment ack)
     * </p>
     *
     * @param message 消息
     * @deprecated 使用 handle(Message message, Acknowledgment ack) 替代
     */
    @Deprecated
    default void handle(Message message) {
        // 为了向后兼容，自动确认
        handle(message, new AutoAcknowledgment());
    }

    /**
     * 自动确认实现（用于向后兼容）
     */
    class AutoAcknowledgment implements Acknowledgment {
        @Override
        public void acknowledge() {
            // 自动确认，无需操作
        }

        @Override
        public void nack(boolean requeue) {
            // 自动确认模式下，nack 等同于 ack
            acknowledge();
        }

        @Override
        public boolean isAcknowledged() {
            return true; // 自动确认模式下，始终认为已确认
        }
    }
}
