package com.chua.starter.job.support.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 日志读取结果
 * <p>
 * 封装日志文件读取操作的返回结果，支持增量读取。
 * 通过{@code fromLineNum}和{@code toLineNum}实现分页读取。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 从第1行开始读取
 * LogResult result = JobFileAppender.readLog(logFileName, 1);
 *
 * // 读取后续内容
 * if (!result.isEnd()) {
 *     result = JobFileAppender.readLog(logFileName, result.getToLineNum() + 1);
 * }
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobFileAppender#readLog(String, int)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogResult implements Serializable {

    private static final long serialVersionUID = 42L;

    /**
     * 起始行号
     */
    private int fromLineNum;

    /**
     * 结束行号
     */
    private int toLineNum;

    /**
     * 日志内容
     */
    private String logContent;

    /**
     * 是否结束
     */
    private boolean isEnd;
}
