package com.chua.starter.queue.priority;

import com.chua.starter.queue.Message;

import java.util.Comparator;

/**
 * 消息优先级比较器
 * <p>
 * 根据消息优先级进行排序
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
public class MessagePriorityComparator implements Comparator<Message> {

    public static final String PRIORITY_HEADER = "X-Priority";

    /**
     * 默认优先级
     */
    public static final int DEFAULT_PRIORITY = 5;

    /**
     * 最高优先级
     */
    public static final int HIGHEST_PRIORITY = 0;

    /**
     * 最低优先级
     */
    public static final int LOWEST_PRIORITY = 10;

    @Override
    public int compare(Message m1, Message m2) {
        int priority1 = getPriority(m1);
        int priority2 = getPriority(m2);

        // 优先级数字越小，优先级越高
        int result = Integer.compare(priority1, priority2);

        // 如果优先级相同，按时间戳排序（先进先出）
        if (result == 0) {
            return Long.compare(m1.getTimestamp(), m2.getTimestamp());
        }

        return result;
    }

    /**
     * 获取消息优先级
     */
    public static int getPriority(Message message) {
        Object priority = message.getHeader(PRIORITY_HEADER);
        if (priority instanceof Integer) {
            return (Integer) priority;
        }
        if (priority instanceof String) {
            try {
                return Integer.parseInt((String) priority);
            } catch (NumberFormatException e) {
                return DEFAULT_PRIORITY;
            }
        }
        return DEFAULT_PRIORITY;
    }

    /**
     * 设置消息优先级
     */
    public static void setPriority(Message message, int priority) {
        if (priority < HIGHEST_PRIORITY) {
            priority = HIGHEST_PRIORITY;
        }
        if (priority > LOWEST_PRIORITY) {
            priority = LOWEST_PRIORITY;
        }
        message.getHeaders().put(PRIORITY_HEADER, priority);
    }

    /**
     * 设置为高优先级
     */
    public static void setHighPriority(Message message) {
        setPriority(message, 1);
    }

    /**
     * 设置为低优先级
     */
    public static void setLowPriority(Message message) {
        setPriority(message, 9);
    }

    /**
     * 是否为高优先级消息
     */
    public static boolean isHighPriority(Message message) {
        return getPriority(message) <= 2;
    }

    /**
     * 是否为低优先级消息
     */
    public static boolean isLowPriority(Message message) {
        return getPriority(message) >= 8;
    }
}
