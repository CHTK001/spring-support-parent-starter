package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.util.ProjectScripts;

import java.util.List;

/**
 * 发布模块「项目类型」提供者 SPI（按项目类型扩展）。
 *
 * <p>把 {@code ReleaseServiceImpl}、{@code ProjectScripts} 里所有与项目类型相关的分支
 * 收拢到本扩展点：四段内置脚本、升级包投放位置、是否需要主入口、是否 JVM 进程、
 * 是否有常驻进程、是否支持依赖包升级、包来源（上传 / Git 构建）、
 * 以及挂载系统服务时的前台常驻命令。</p>
 *
 * <p>新增项目类型只需实现本接口、标注 {@code @Spi} 并登记到
 * {@code META-INF/extensions/com.chua.starter.server.support.spi.ProjectTypeProvider}，
 * 业务层不再出现 {@code "springboot".equals(type)} 之类的判断。未登记的类型由
 * {@link ProjectTypeProviderRegistry#resolveOrGeneric(String)} 回落到通用实现，
 * 行为与改动前的 {@code default} 分支一致。</p>
 *
 * <p><b>实现约定：</b>{@link #placements(ProjectPlacementContext)} 只声明「投放到哪里、
 * 叫什么名字、是否需要先建目录、失败后是否要还原并重启」，真正的执行（SFTP 优先、
 * 命令回退）仍由业务层完成 —— 类型实现不应依赖 SFTP/SSH 服务，保持可独立测试。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectTypeProvider {

    /**
     * 包来源：上传安装包（拖拽 / 手动选择）
     */
    String SOURCE_UPLOAD = "upload";

    /**
     * 包来源：从 Git 仓库拉取源码并构建
     */
    String SOURCE_GIT = "git";

    /**
     * 项目类型标识（与前端项目类型下拉、{@code MsServerProject#projectType} 取值一致）。
     *
     * @return 类型标识（小写）
     */
    String type();

    /**
     * 展示名称（用于日志与前端类型列表）。
     *
     * @return 展示名称
     */
    default String label() {
        return type();
    }

    /**
     * 该实现额外覆盖的类型别名（例如静态站点与 nginx 共用同一套脚本）。
     *
     * @return 别名列表；无别名为空列表
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * 生成四段内置脚本（占位符未展开）。
     *
     * @param windows   目标主机是否 Windows
     * @param mainEntry 主入口名称
     * @return 启动 / 停止 / 重启 / 状态脚本
     */
    ProjectScripts.Scripts scripts(boolean windows, String mainEntry);

    /**
     * 升级包投放计划。
     *
     * <p>返回顺序即执行顺序；返回空列表表示该配置下不投放任何文件
     * （典型：分离式项目显式指定了非法的部署类型）。</p>
     *
     * @param context 投放上下文
     * @return 投放计划
     */
    List<ProjectPlacement> placements(ProjectPlacementContext context);

    /**
     * 是否必须配置主入口。
     *
     * @return 必须配置时为 {@code true}
     */
    default boolean requiresMainEntry() {
        return true;
    }

    /**
     * 是否为 JVM 进程（Arthas 等 Java 诊断能力的准入条件）。
     *
     * @return 是 JVM 进程时为 {@code true}
     */
    default boolean javaProcess() {
        return false;
    }

    /**
     * 是否有常驻进程。
     *
     * <p>为 {@code false} 时升级流程不执行重启与健康检查（典型：模型仓库只管理文件）。</p>
     *
     * @return 有常驻进程时为 {@code true}
     */
    default boolean runtimeProcess() {
        return true;
    }

    /**
     * 是否支持单独更新依赖包（分离式部署的 lib 目录）。
     *
     * @return 支持时为 {@code true}
     */
    default boolean supportsLibUpgrade() {
        return false;
    }

    /**
     * 默认部署类型（仅分离式等需要区分子包的场景使用）。
     *
     * @return 默认部署类型；不适用时为空串
     */
    default String defaultDeployType() {
        return "";
    }

    /**
     * 包来源。
     *
     * @return {@link #SOURCE_UPLOAD} 或 {@link #SOURCE_GIT}
     */
    default String sourceKind() {
        return SOURCE_UPLOAD;
    }

    /**
     * 挂载系统服务（systemd / launchd）时的前台常驻命令。
     *
     * <p>必须以前台方式运行：服务管理器跟踪到的进程即业务进程，退到后台会让
     * {@code Type=simple} 认为服务已结束。</p>
     *
     * @param mainEntry 主入口名称
     * @return POSIX 命令行
     */
    default String posixForeground(String mainEntry) {
        return "'./" + escape(mainEntry) + "' $APP_ARGS";
    }

    /**
     * POSIX 单引号转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    static String escape(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }
}
