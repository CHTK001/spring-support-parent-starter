package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandSupport;

@Spi("nodejs")
public class NodeProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        return ProjectRuntimeCommandSupport.buildDetachedStartCommand(
                context,
                "node app.js"
        );
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        String cwd = context.rootDirectory();
        return "pgrep -f -- " + ProjectRuntimeCommandSupport.shellQuote("app.js")
                + " | while read p; do "
                + "if [ -L /proc/\"$p\"/cwd ] && [ \"$(readlink /proc/\"$p\"/cwd)\" = " + ProjectRuntimeCommandSupport.shellQuote(cwd)
                + " ]; then kill \"$p\" 2>/dev/null || true; sleep 1; kill -9 \"$p\" 2>/dev/null || true; fi; done 2>/dev/null;"
                + " rm -f " + ProjectRuntimeCommandSupport.shellQuote(context.pidFile()) + "; true";
    }
}
