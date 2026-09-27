package com.chua.report.client.starter.job.log;

import java.io.Serializable;
import java.util.Objects;

/**
 * 日志结果
 *
 * @param fromLineNum 起始行号
 * @param toLineNum 结束行号
 * @param logContent 日志内容
 * @param end 是否结束
 *
 * @since 2024/03/11
 * @author CH
 */
public record LogResult(
        int fromLineNum,
        int toLineNum,
        String logContent,
        boolean end
) implements Serializable {
    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 42L;

    /**
     * 规范构造器：日志内容做空值敌对化。
     *
     * <p>value class 前置条件——唯一的引用型组件必须非空。行号与结束标记为基本类型，
     * 无需空值校验。</p>
     *
     * @param logContent 日志内容
     */
    public LogResult {
        logContent = Objects.requireNonNull(logContent, "logContent 不能为 null");
    }
}
