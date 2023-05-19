package com.chua.shell.support.shell.mapping;

import com.chua.shell.support.LineUtils;
import com.chua.shell.support.shell.CommandAttribute;
import com.chua.shell.support.shell.Shell;
import com.chua.shell.support.shell.ShellMapping;
import com.google.common.base.Strings;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

import java.util.Collection;

import static com.chua.shell.support.shell.mapping.DelegateCommand.COMMAND_LIMIT;

/**
 * wget命令
 *
 * @author CH
 */
public class HelpCommand {
    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"help"}, describe = "帮助")
    public String help(Shell shell) {
        StringBuilder stringBuilder = new StringBuilder("\r\n");
        Collection<CommandAttribute> command = shell.getCommand();
        for (CommandAttribute commandAttribute : command) {
            stringBuilder
                    .append(Strings.repeat("\t", 1))
                    .append(AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, LineUtils.ifx(commandAttribute.getName(), COMMAND_LIMIT)))
                    .append(" :  ")
                    .append(AnsiOutput.toString(AnsiColor.BRIGHT_WHITE, commandAttribute.getDescribe()))
                    .append("\r\n");
        }
        stringBuilder.append("\r\n");
        return stringBuilder.toString();
    }

    /**
     * clear
     */
    @ShellMapping(value = {"clear"}, describe = "清除终端信息")
    public void clear() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }


}
