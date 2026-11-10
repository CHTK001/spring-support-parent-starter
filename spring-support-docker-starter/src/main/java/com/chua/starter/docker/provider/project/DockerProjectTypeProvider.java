package com.chua.starter.docker.provider.project;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlacement;
import com.chua.starter.server.support.spi.ProjectPlacementContext;
import com.chua.starter.server.support.spi.ProjectTypeProvider;
import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * Docker 项目类型：以项目地址为构建上下文，按 Dockerfile 构建镜像并运行容器。
 *
 * <p>实现放在 Docker 模块（{@code spring-support-docker-starter}）而不是发布模块：
 * 容器相关的命令、参数与后续可复用的镜像/网络/卷能力都属于 Docker 域，
 * 发布模块只通过 {@link ProjectTypeProvider} 扩展点消费，不再出现
 * {@code "docker".equals(type)} 之类的分支。</p>
 *
 * <p>发布语义：</p>
 * <ul>
 *   <li>主入口 = <b>容器名</b>（停止 / 状态 / systemd 前台命令都以它为准）</li>
 *   <li>升级包为压缩包时解压到项目地址（新的源码 + Dockerfile）；单文件时投放到项目地址</li>
 *   <li>启动脚本先 {@code docker build}，成功后才重建容器，构建失败不影响旧容器</li>
 *   <li>容器 PID 写入 {@code app.pid}，与 Java / Nginx 项目共用同一套状态判定</li>
 * </ul>
 *
 * <p>其余参数（Dockerfile 路径、镜像名、端口、环境变量、数据卷、网络、重启策略、
 * 构建参数、启动命令）存放在项目 {@code appConfig} 的 JSON 对象里，由
 * {@code DockerProjectSettings} 在执行期展开到脚本占位符。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Spi(value = "docker", order = 100)
public class DockerProjectTypeProvider implements ProjectTypeProvider {

    @Override
    public String type() {
        return "docker";
    }

    @Override
    public String label() {
        return "Docker";
    }

    @Override
    public ProjectScripts.Scripts scripts(boolean windows, String mainEntry) {
        return windows
                ? ProjectScripts.windowsDocker(mainEntry)
                : ProjectScripts.linuxDocker(mainEntry);
    }

    @Override
    public List<ProjectPlacement> placements(ProjectPlacementContext context) {
        if (context.autoExtract() && context.archive()) {
            return List.of(ProjectPlacement.extract("解压构建上下文到项目目录", context.projectPath()));
        }
        // 单个产物（例如新 jar）直接投放到构建上下文根目录，由 Dockerfile 的 COPY 取用
        return List.of(ProjectPlacement.place("投放构建产物到项目目录", context.projectPath(),
                context.packageName(), false, false));
    }

    @Override
    public String pidFile() {
        return "app.pid";
    }

    @Override
    public String posixForeground(String mainEntry) {
        String name = mainEntry == null ? "" : mainEntry.trim();
        return name.isEmpty() ? "" : "docker start -a '" + ProjectTypeProvider.escape(name) + "'";
    }
}
