package com.chua.starter.server.support.spi;

/**
 * 升级包投放上下文（{@link ProjectTypeProvider#placements(ProjectPlacementContext)} 的唯一输入）。
 *
 * <p>刻意不直接传 {@code MsServerProject} 实体：类型扩展点不应被 MyBatis 实体契约绑定，
 * 也便于单测直接构造。</p>
 *
 * @param projectPath     项目地址（绝对路径）
 * @param mainEntry       主入口名称（jar / 可执行文件 / 启动脚本）
 * @param libDir          依赖库目录（相对项目地址，仅分离式使用；为空表示 lib）
 * @param packagePath     安装包在目标主机上的绝对路径
 * @param archive         安装包是否为压缩包（按扩展名判定）
 * @param autoExtract     升级配置：压缩包是否自动解压
 * @param uploadExtract   项目配置：上传时是否解压（模型类型使用）
 * @param uploadTargetDir 项目配置：上传目标目录（相对项目地址，模型类型使用）
 * @param deployType      部署类型（main / lib / both，分离式使用）
 * @author CH
 * @since 4.0.0.42
 */
public record ProjectPlacementContext(
        String projectPath,
        String mainEntry,
        String libDir,
        String packagePath,
        boolean archive,
        boolean autoExtract,
        boolean uploadExtract,
        String uploadTargetDir,
        String deployType) {

    /**
     * 紧凑构造器：字符串组件归一为去空白后的值，避免实现方各自判空。
     */
    public ProjectPlacementContext {
        projectPath = normalize(projectPath);
        mainEntry = normalize(mainEntry);
        libDir = normalize(libDir);
        packagePath = normalize(packagePath);
        uploadTargetDir = normalize(uploadTargetDir);
        deployType = normalize(deployType);
    }

    /**
     * 把相对路径拼到项目地址之后。
     *
     * @param relative 相对路径；为空时直接返回项目地址
     * @return 拼接后的绝对路径
     */
    public String resolve(String relative) {
        String rel = normalize(relative);
        if (rel.isEmpty()) {
            return projectPath;
        }
        if (projectPath.isEmpty()) {
            return rel;
        }
        boolean tail = projectPath.endsWith("/") || projectPath.endsWith("\\");
        return (tail ? projectPath : projectPath + "/") + rel;
    }

    /**
     * 安装包文件名（去掉目录部分）。
     *
     * @return 文件名；包路径为空时返回空串
     */
    public String packageName() {
        int slash = Math.max(packagePath.lastIndexOf('/'), packagePath.lastIndexOf('\\'));
        return slash >= 0 ? packagePath.substring(slash + 1) : packagePath;
    }

    /**
     * 依赖库目录（为空时取 {@code lib}）。
     * @param defaultDir 字符串
     * @return 依赖库目录
    */
    public String libDirOr(String defaultDir) {
        return libDir.isEmpty() ? defaultDir : libDir;
    }

    /**
     * 字符串归一。
     *
     * @param value 原始值
     * @return 去空白后的值；{@code null} 归一为空串
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
