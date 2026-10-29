package com.chua.starter.server.support.provider.project.type;

import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * 通用项目类型兜底实现：未登记（或历史遗留、空值）的项目类型走这里。
 *
 * <p>行为与改造前 {@code ProjectScripts} / {@code runUpgrade} 的 {@code default} 分支一致：
 * 脚本交给用户自带的 {@code start.sh} / {@code start.ps1}，升级包按主入口投放。
 * 刻意<b>不</b>登记到 SPI 扩展点文件：它没有类型标识，只作为
 * {@link com.chua.starter.server.support.spi.ProjectTypeProviderRegistry#resolveOrGeneric(String)}
 * 的兜底对象存在。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class GenericProjectTypeProvider implements ProjectTypeProvider {

    /**
     * 共享实例（无状态，可直接复用）
     */
    public static final GenericProjectTypeProvider INSTANCE = new GenericProjectTypeProvider();

    /**
     * 无状态实现，禁止额外实例化
     */
    private GenericProjectTypeProvider() {
    }

    @Override
    public String type() {
        return "generic";
    }

    @Override
    public String label() {
        return "通用";
    }

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        return windows
                ? ProjectScripts.windowsGeneric(mainEntry)
                : ProjectScripts.linuxGeneric(mainEntry);
    }

    @Override
    public List<ProjectPlacement> placements(ProjectPlacementContext context) {
        if (context.autoExtract() && context.archive()) {
            return List.of(ProjectPlacement.extract("解压新版本包", context.projectPath()));
        }
        return List.of(ProjectPlacement.place("投放新版本文件", context.projectPath(),
                context.mainEntry(), false, true));
    }
}
