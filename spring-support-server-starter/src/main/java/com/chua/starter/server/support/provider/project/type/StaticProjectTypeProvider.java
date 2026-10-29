package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * 静态站点项目：以项目目录为 nginx 前缀独立托管，无需主入口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "static", order = 100)
public class StaticProjectTypeProvider implements ProjectTypeProvider {

    @Override
    public String type() {
        return "static";
    }

    @Override
    public String label() {
        return "静态站点";
    }

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        return windows
                ? ProjectScripts.windowsStatic(mainEntry)
                : ProjectScripts.linuxStatic(mainEntry);
    }

    @Override
    public List<ProjectPlacement> placements(ProjectPlacementContext context) {
        if (context.autoExtract() && context.archive()) {
            return List.of(ProjectPlacement.extract("解压新版本包", context.projectPath()));
        }
        return List.of(ProjectPlacement.place("投放新版本文件", context.projectPath(),
                context.mainEntry(), false, true));
    }

    @Override
    public boolean requiresMainEntry() {
        return false;
    }

    @Override
    public String posixForeground(String mainEntry) {
        return "nginx -g 'daemon off;'";
    }
}
