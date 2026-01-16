package com.chua.report.client.starter.job.log;


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
    private int fromLineNum;
    private int toLineNum;
    private String logContent;
    private boolean isEnd;
}
