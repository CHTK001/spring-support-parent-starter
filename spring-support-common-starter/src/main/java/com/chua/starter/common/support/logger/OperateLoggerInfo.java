package com.chua.starter.common.support.logger;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统日志
 *
 * @author CH
 */
@Getter
@Setter
public class OperateLoggerInfo extends SysLoggerInfo {

    public OperateLoggerInfo(Object source) {
        super(source);
    }
}
