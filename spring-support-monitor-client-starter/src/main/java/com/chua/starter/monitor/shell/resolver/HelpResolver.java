package com.chua.starter.monitor.shell.resolver;

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
public class HelpResolver implements Resolver{
    @Override
    public String execute(Command command, BaseShell shell) {
        return Json.toJSONString(shell.usageCommand());
    }
}
