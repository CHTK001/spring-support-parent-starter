package com.chua.starter.monitor.shell.resolver;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.json.Json;
import com.chua.common.support.shell.BaseShell;
import com.chua.common.support.shell.Command;

/**
 * 分解器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/06
 */
@SpiDefault
public class DefaultResolver implements Resolver{
    @Override
    public String execute(Command command, BaseShell shell) {
        return Json.toJSONString(shell.handlerAnalysis(command.getCommand(), shell));
    }
}
