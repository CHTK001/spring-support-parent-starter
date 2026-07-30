package com.chua.starter.project.service.runtime.impl;

import com.chua.common.support.annotation.Spi;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandSupport;
import org.springframework.util.StringUtils;

@Spi("java")
public class JavaProjectRuntimeCommandStrategy implements ProjectRuntimeCommandStrategy {

    @Override
    public String buildStart(ProjectRuntimeCommandContext context) {
        ProjectRemoteProject project = context.project();
        String jar = StringUtils.hasText(project.getMainArtifact()) ? project.getMainArtifact().trim() : "*.jar";
        String jvm = StringUtils.hasText(project.getJvmArgs()) ? project.getJvmArgs() + " " : "";
        String appArgs = StringUtils.hasText(project.getSpringArgs()) ? (" " + project.getSpringArgs()) : "";
        String rootCommand = ProjectRuntimeCommandSupport.shellQuote(jar).equals("'*.jar'")
                ? jar
                : ProjectRuntimeCommandSupport.shellQuote(jar);
        String command = "java " + jvm + "-jar " + rootCommand + appArgs;
        return ProjectRuntimeCommandSupport.buildDetachedStartCommand(context, command);
    }

    @Override
    public String buildStop(ProjectRuntimeCommandContext context) {
        ProjectRemoteProject project = context.project();
        String artifact = StringUtils.hasText(project.getMainArtifact()) ? project.getMainArtifact().trim() : "";
        String cwd = context.rootDirectory();
        return "pid_file=" + ProjectRuntimeCommandSupport.shellQuote(context.pidFile()) + "; "
                + "if [ -f \"$pid_file\" ]; then pid=$(cat \"$pid_file\" | tr -d '\\r\\n '); "
                + "rm -f \"$pid_file\"; if [ -n \"$pid\" ] && kill -0 \"$pid\" 2>/dev/null; then "
                + "kill \"$pid\" 2>/dev/null || true; sleep 2; kill -9 \"$pid\" 2>/dev/null || true; fi; fi; "
                + (StringUtils.hasText(artifact)
                ? "pgrep -f " + ProjectRuntimeCommandSupport.shellQuote(artifact)
                + " | while read p; do "
                + "if [ -L /proc/\"$p\"/cwd ] && [ \"$(readlink /proc/\"$p\"/cwd)\" = " + ProjectRuntimeCommandSupport.shellQuote(cwd)
                + " ]; then kill \"$p\" 2>/dev/null || true; sleep 1; kill -9 \"$p\" 2>/dev/null || true; fi; done"
                : "true") + "; sleep 1";
    }
}
