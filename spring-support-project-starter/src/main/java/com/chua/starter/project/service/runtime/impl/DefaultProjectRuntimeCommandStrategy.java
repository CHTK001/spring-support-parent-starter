package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandSupport;

@Spi("default")
public class DefaultProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        return ProjectRuntimeCommandSupport.buildDetachedStartCommand(
                context,
                "./start.sh"
        );
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        return "pid_file=" + ProjectRuntimeCommandSupport.shellQuote(context.pidFile()) + "; "
                + "if [ -f \"$pid_file\" ]; then pid=$(cat \"$pid_file\" | tr -d '\\r\\n '); "
                + "rm -f \"$pid_file\"; if [ -n \"$pid\" ] && kill -0 \"$pid\" 2>/dev/null; then "
                + "kill \"$pid\" 2>/dev/null || true; sleep 1; kill -9 \"$pid\" 2>/dev/null || true; fi; fi; "
                + "kw_pids=\"$(" + context.pidScript() + " | tr '\\n' ' ')\"; "
                + "if [ -n \"$kw_pids\" ]; then kill $kw_pids 2>/dev/null || true; sleep 1; kill -9 $kw_pids 2>/dev/null || true; fi; "
                + "echo \"$kw_pids\"";
    }
}
