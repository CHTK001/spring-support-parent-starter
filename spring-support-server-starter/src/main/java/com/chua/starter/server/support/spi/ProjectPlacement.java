package com.chua.starter.server.support.spi;

/**
 * 升级包投放计划中的单条指令。
 *
 * <p>由 {@link ProjectTypeProvider#placements(ProjectPlacementContext)} 声明，
 * 业务层负责执行：{@link Kind#EXTRACT} 交给平台实现解压，
 * {@link Kind#PLACE} 走「SFTP 重命名优先、平台命令回退」的投放链路。</p>
 *
 * @param title            步骤标题（任务面板展示）
 * @param kind             投放方式
 * @param targetDir        目标目录（绝对路径）
 * @param fileName         目标文件名（仅 {@link Kind#PLACE} 使用）
 * @param createTargetDir  投放前是否先创建目标目录
 * @param restoreOnFailure 投放失败时是否用最新备份还原并重新拉起服务
 * @author CH
 * @since 4.0.0.42
 */
public record ProjectPlacement(String title, Kind kind, String targetDir, String fileName,
                               boolean createTargetDir, boolean restoreOnFailure) {

    /**
     * 投放方式。
     */
    public enum Kind {
        /**
         * 解压压缩包到目标目录
         */
        EXTRACT,
        /**
         * 把单个文件投放到目标目录
         */
        PLACE
    }

    /**
     * 构造「解压」指令。
     *
     * @param title     步骤标题
     * @param targetDir 解压目标目录
     * @return 投放指令
     */
    public static ProjectPlacement extract(String title, String targetDir) {
        return new ProjectPlacement(title, Kind.EXTRACT, targetDir, null, false, false);
    }

    /**
     * 构造「投放文件」指令。
     *
     * @param title            步骤标题
     * @param targetDir        目标目录
     * @param fileName         目标文件名
     * @param createTargetDir  是否先创建目标目录
     * @param restoreOnFailure 失败时是否还原并重启
     * @return 投放指令
     */
    public static ProjectPlacement place(String title, String targetDir, String fileName,
                                         boolean createTargetDir, boolean restoreOnFailure) {
        return new ProjectPlacement(title, Kind.PLACE, targetDir, fileName, createTargetDir, restoreOnFailure);
    }
}
