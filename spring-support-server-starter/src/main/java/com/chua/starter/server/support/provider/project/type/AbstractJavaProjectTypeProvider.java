package com.chua.starter.server.support.provider.project.type;

import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * JVM 应用类项目（{@code java -jar} 运行）的公共实现。
 *
 * <p>SpringBoot 单体、FatJar、Git 源码部署三者的升级投放与进程特征完全一致，
 * 差异只在启动脚本是否附带生产 profile，因此把公共部分收敛到本类，子类只声明差异。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public abstract class AbstractJavaProjectTypeProvider implements ProjectTypeProvider {

    /**
     * 启动脚本是否附带 {@code --spring.profiles.active=prod}。
     *
     * @return 附带时为 {@code true}
     */
    protected abstract boolean springProfile();

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        if (windows) {
            return ProjectScripts.windowsJava(mainEntry);
        }
        return springProfile()
                ? ProjectScripts.linuxSpringboot(mainEntry)
                : ProjectScripts.linuxFatjar(mainEntry);
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
    public boolean javaProcess() {
        return true;
    }

    @Override
    public String posixForeground(String mainEntry) {
        return "java $JVM_ARGS -jar '" + ProjectTypeProvider.escape(mainEntry) + "' $APP_ARGS";
    }
}
