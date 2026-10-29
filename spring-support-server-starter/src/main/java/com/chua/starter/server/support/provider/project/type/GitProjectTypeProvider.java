package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Git 源码部署项目：从仓库拉取源码、Maven 构建后再按 JVM 应用发布。
 *
 * <p>它是<b>正式的项目类型</b>，不是给某个类型附加的开关：类型决定「包从哪来」，
 * 由 {@link #sourceKind()} 声明为 {@code git}，升级链路据此先构建再走本类型的投放与重启。
 * 构建产物按 FatJar 处理（不附加 profile，避免把非 SpringBoot 应用强行塞入 prod 配置）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "git", order = 100)
public class GitProjectTypeProvider extends AbstractJavaProjectTypeProvider {

    @Override
    public String type() {
        return "git";
    }

    @Override
    public String label() {
        return "Git 源码部署";
    }

    @Override
    protected boolean springProfile() {
        return false;
    }

    @Override
    public String sourceKind() {
        return SOURCE_GIT;
    }
}
