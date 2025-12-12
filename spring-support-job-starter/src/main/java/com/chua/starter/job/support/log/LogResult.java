package com.chua.starter.job.support.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 日志结果
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
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
