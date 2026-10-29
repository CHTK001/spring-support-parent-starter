package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * 模型仓库项目：只管理模型文件，没有常驻进程，升级后不重启。
 *
 * <p>投放目标目录可由项目配置（{@code uploadTargetDir}）指定，为空时直接用项目地址。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "model", order = 100)
public class ModelProjectTypeProvider implements ProjectTypeProvider {

    @Override
    public String type() {
        return "model";
    }

    @Override
    public String label() {
        return "模型仓库";
    }

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        return windows
                ? ProjectScripts.windowsModel(mainEntry)
                : ProjectScripts.linuxModel(mainEntry);
    }

    @Override
    public List<ProjectPlacement> placements(ProjectPlacementContext context) {
        String targetDir = context.uploadTargetDir().isEmpty()
                ? context.projectPath()
                : context.resolve(context.uploadTargetDir());
        if (context.uploadExtract() && context.archive()) {
            return List.of(ProjectPlacement.extract("解压模型文件到目录", targetDir));
        }
        return List.of(ProjectPlacement.place("投放模型文件到目录", targetDir,
                context.packageName(), false, false));
    }

    @Override
    public boolean requiresMainEntry() {
        return false;
    }

    @Override
    public boolean runtimeProcess() {
        return false;
    }
}
