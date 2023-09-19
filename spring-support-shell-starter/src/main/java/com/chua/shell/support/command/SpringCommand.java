package com.chua.shell.support.command;

import com.chua.common.support.shell.ShellMapping;
import com.chua.common.support.shell.ShellParam;
import com.chua.common.support.shell.ShellResult;
import com.chua.shell.support.command.spring.EnvironmentSpring;
import com.chua.shell.support.command.spring.InvokeSpring;
import com.chua.shell.support.command.spring.LoggerSpring;
import com.chua.shell.support.command.spring.ShowSpring;

import java.util.List;

/**
 * spring命令
 *
 * @author CH
 */
public class SpringCommand {

    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"spring"}, describe = "spring的操作命令")
    public ShellResult spring(
            @ShellParam(value = "invoke", numberOfArgs = 2, describe = "执行spring对象", example = {"spring -invoke bean method: 执行spring无参方法"}) List<String> invoke,
            @ShellParam(value = "show", numberOfArgs = 2, describe = "获取Bean/Mapping变量", example = {"spring -show bean: 获取Bean等级"}) List<String> show,
            @ShellParam(value = "env", numberOfArgs = 3, describe = "设置/获取环境变量", example = {"spring -e get server.port: 获取spring参数", "spring -e set server.port 9090: 设置/更新spring参数"}) List<String> env,
            @ShellParam(value = "logger", numberOfArgs = 3, describe = "设置/获取日志等级",
                    example = {"spring -e get root: 获取root等级", "spring -e set root debug: 设置/更新root参数"}) List<String> logger
    ) {
        if (null != invoke && !invoke.isEmpty()) {
            return InvokeSpring.execute(invoke);
        }

        if (null != show && !show.isEmpty()) {
            return ShowSpring.execute(show);
        }

        if (null != env && !env.isEmpty()) {
            return EnvironmentSpring.execute(env);
        }


        if (null != logger && !logger.isEmpty()) {
            return LoggerSpring.execute(logger);
        }

        return null;

    }


}
