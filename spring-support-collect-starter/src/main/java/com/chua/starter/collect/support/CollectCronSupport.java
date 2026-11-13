package com.chua.starter.collect.support;

import com.chua.common.support.task.scheduler.CronExpression;

import java.util.Date;

/**
 * Cron 表达式工具。
 *
 * <p>流程的 {@code collect_flow_cron} 使用 6 字段表达式（秒 分 时 日 月 周），
 * 与 job-starter、调度平台保持同一套解析器。真正决定何时触发的是 job-starter 的
 * {@code sys_job} 台账，本类只负责保存前校验和页面上的排期预览。
 * 委托 utils：解析经 {@code com.chua.common.support.task.scheduler.CronExpression} 计算。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectCronSupport {

    /**
     * 工具类禁止实例化。
     */
    private CollectCronSupport() {
    }

    /**
     * 校验表达式，空表达式视为不定时。
     *
     * @param cron 表达式
     * @throws IllegalArgumentException 表达式非法
     */
    public static void validate(String cron) {
        String text = trim(cron);
        if (text.isEmpty()) {
            return;
        }
        try {
            new CronExpression(text);
        } catch (Exception ex) {
            throw new IllegalArgumentException("定时表达式不合法: " + text + "（" + ex.getMessage() + "）");
        }
    }

    /**
     * 计算紧邻的下 N 次触发时间，用于设计器预览排期。
     *
     * @param cron  表达式
     * @param from  基准毫秒
     * @param times 次数，最多 10
     * @return 触发毫秒数组，长度不足表示没有更多触发点
     */
    public static long[] nextList(String cron, long from, int times) {
        String text = trim(cron);
        if (text.isEmpty()) {
            return new long[0];
        }
        // [P3C A20 豁免]
        int limit = Math.max(1, Math.min(times, 10));
        long[] result = new long[limit];
        int count = 0;
        long cursor = from;
        try {
            CronExpression expression = new CronExpression(text);
            for (int index = 0; index < limit; index++) {
                Date next = expression.getNextValidTimeAfter(new Date(cursor));
                if (next == null) {
                    break;
                }
                result[count++] = next.getTime();
                cursor = next.getTime();
            }
        } catch (Exception ex) {
            return new long[0];
        }
        if (count == limit) {
            return result;
        }
        long[] trimmed = new long[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }

    /**
     * 表达式是否为空。
     *
     * @param cron 表达式
     * @return 空返回 true
     */
    public static boolean isBlank(String cron) {
        return trim(cron).isEmpty();
    }

    /**
     * 去掉首尾空白与换行。
     *
     * @param cron 表达式
     * @return 规整后的表达式
     */
    private static String trim(String cron) {
        return cron == null ? "" : cron.trim().replaceAll("\\s+", " ");
    }
}
