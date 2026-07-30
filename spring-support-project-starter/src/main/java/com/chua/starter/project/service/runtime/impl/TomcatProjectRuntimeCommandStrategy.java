package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandSupport;
import org.springframework.util.StringUtils;

@Spi("tomcat")
public class TomcatProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        String tomcatHome = resolveTomcatHome(context.project());
        return "cd " + ProjectRuntimeCommandSupport.shellQuote(tomcatHome + "/bin")
                + " && (./startup.sh > " + ProjectRuntimeCommandSupport.shellQuote(context.startupLogFile())
                + " 2>&1 < /dev/null & sleep 2; pgrep -f -- "
                + ProjectRuntimeCommandSupport.shellQuote(tomcatHome)
                + " | head -1 > " + ProjectRuntimeCommandSupport.shellQuote(context.pidFile()) + ")";
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        String tomcatHome = resolveTomcatHome(context.project());
        return "cd " + ProjectRuntimeCommandSupport.shellQuote(tomcatHome + "/bin")
                + " && ./shutdown.sh 2>&1; sleep 2; pkill -f -- "
                + ProjectRuntimeCommandSupport.shellQuote(tomcatHome)
                + " 2>/dev/null || true; rm -f "
                + ProjectRuntimeCommandSupport.shellQuote(context.pidFile());
    }

    private String resolveTomcatHome(ProjectRemoteProject project) {
        return StringUtils.hasText(project.getTomcatHome())
                ? project.getTomcatHome().trim()
                : String.valueOf(project.getRootDirectory());
    }
}
