package com.chua.starter.monitor.shell.resolver;

import com.chua.common.support.shell.BaseShell;
import com.chua.common.support.shell.Command;

/**
 * 分解器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
public interface Resolver {


    /**
     * 处理
     *
     * @param command 命令
     * @param shell   壳
     * @return {@link String}
     */
    String execute(Command command, BaseShell shell);
}
