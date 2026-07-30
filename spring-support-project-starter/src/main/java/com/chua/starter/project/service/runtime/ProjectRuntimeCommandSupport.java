package com.chua.starter.project.service.runtime;

public final class ProjectRuntimeCommandSupport {

    private ProjectRuntimeCommandSupport() {
    }

    public static String shellQuote(String value) {
        return "'" + String.valueOf(value == null ? "" : value).replace("'", "'\"'\"'") + "'";
    }

    public static String buildDetachedStartCommand(ProjectRuntimeCommandContext context, String command) {
        return "mkdir -p " + shellQuote(context.rootDirectory())
                + " " + shellQuote(context.logDirectory())
                + " && cd " + shellQuote(context.rootDirectory())
                + " && (nohup " + command
                + " > " + shellQuote(context.startupLogFile())
                + " 2>&1 < /dev/null & echo $! > " + shellQuote(context.pidFile()) + ")";
    }
}
