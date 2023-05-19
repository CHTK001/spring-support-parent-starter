package com.chua.shell.support.command;

import com.chua.shell.support.arthas.Attach;
import com.chua.shell.support.shell.ShellMapping;
import com.chua.shell.support.shell.ShellOriginal;
import com.chua.shell.support.shell.ShellParam;

/**
 * arthas
 *
 * @author CH
 */
public class ArthasCommand {

    private Attach attach;


    @ShellMapping(value = "arthas", needShort = false, describe = "arthas服务")
    public String open(
            @ShellParam(value = "port", describe = "端口", defaultValue = "18563") int port, @ShellOriginal String value) {
        if ("start".equals(value)) {
            if (null == attach) {
                synchronized (this) {
                    if (null == attach) {
                        attach = Attach.builder().httpPort(port).build();
                        attach.start();
                        return "开启成功";
                    }
                }
            }
            return "已开启";
        }

        if (null == attach) {
            return "未开启服务";
        }
        attach.stop();
        attach = null;
        return "停止成功";
    }
}
