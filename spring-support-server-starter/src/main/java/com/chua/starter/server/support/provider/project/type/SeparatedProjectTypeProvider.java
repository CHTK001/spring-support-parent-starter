package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 分离式部署项目：主入口为可执行启动脚本，依赖 jar 放在独立的 lib 目录。
 *
 * <p>升级时可只更新主包、只更新依赖包，或两者都更新，由部署类型决定：
 * 为空表示两者都更新；{@code main} / {@code lib} / {@code both} 精确匹配；
 * 其它取值（历史脏数据）两者都不更新 —— 与改造前保持一致，不做「猜一个」的兜底，
 * 避免把主包误投进 lib 目录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "separated", order = 100)
public class SeparatedProjectTypeProvider implements ProjectTypeProvider {

    /**
     * 部署类型：只更新主包
     */
    private static final String DEPLOY_MAIN = "main";

    /**
     * 部署类型：只更新依赖包
     */
    private static final String DEPLOY_LIB = "lib";

    /**
     * 部署类型：主包与依赖包都更新
     */
    private static final String DEPLOY_BOTH = "both";

    /**
     * 依赖库默认目录名
     */
    private static final String DEFAULT_LIB_DIR = "lib";

    @Override
    public String type() {
        return "separated";
    }

    @Override
    public String label() {
        return "分离式部署";
    }

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        return windows
                ? ProjectScripts.windowsSeparated(mainEntry)
                : ProjectScripts.linuxSeparated(mainEntry);
    }

    @Override
    public List<ProjectPlacement> placements(ProjectPlacementContext context) {
        String deployType = context.deployType().toLowerCase(Locale.ROOT);
        boolean doMain = DEPLOY_MAIN.equals(deployType) || DEPLOY_BOTH.equals(deployType);
        boolean doLib = DEPLOY_LIB.equals(deployType) || DEPLOY_BOTH.equals(deployType);
        if (deployType.isEmpty()) {
            doMain = true;
            doLib = true;
        }
        List<ProjectPlacement> out = new ArrayList<>(2);
        if (doMain) {
            out.add(mainPlacement(context));
        }
        if (doLib) {
            out.add(libPlacement(context));
        }
        return List.copyOf(out);
    }

    /**
     * 主包投放：解压到项目目录，或把单个文件投放到主入口位置。
     *
     * @param context 投放上下文
     * @return 投放指令
     */
    private ProjectPlacement mainPlacement(ProjectPlacementContext context) {
        if (context.autoExtract() && context.archive()) {
            return ProjectPlacement.extract("解压主包", context.projectPath());
        }
        return ProjectPlacement.place("投放主包", context.projectPath(),
                context.mainEntry(), false, true);
    }

    /**
     * 依赖包投放：解压到 lib 目录，或把单个 jar 投放到 lib 目录。
     *
     * <p>投放前先建目录：首次升级时 lib 目录往往还不存在，不建目录 SFTP 重命名会失败。</p>
     *
     * @param context 投放上下文
     * @return 投放指令
     */
    private ProjectPlacement libPlacement(ProjectPlacementContext context) {
        String libPath = context.resolve(context.libDirOr(DEFAULT_LIB_DIR));
        if (context.autoExtract() && context.archive()) {
            return ProjectPlacement.extract("解压依赖到 lib 目录", libPath);
        }
        return ProjectPlacement.place("投放依赖到 lib 目录", libPath,
                context.packageName(), true, false);
    }

    @Override
    public boolean supportsLibUpgrade() {
        return true;
    }

    @Override
    public String defaultDeployType() {
        return DEPLOY_BOTH;
    }

    @Override
    public String posixForeground(String mainEntry) {
        return "'./" + ProjectTypeProvider.escape(mainEntry) + "' $JVM_ARGS $APP_ARGS";
    }
}
