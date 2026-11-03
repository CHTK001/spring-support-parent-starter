package com.chua.starter.ai.support.workspace.git;

import java.util.List;

/**
 * Git 面板接口数据模型。
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class GitModels {

    /**
     * 工具类禁止实例化
     */
    private GitModels() {
    }

    /**
     * 单个文件的 Git 状态。
     *
     * @param path        文件相对仓库根目录的路径
     * @param indexStatus 暂存区状态字符（A/M/D/U/空格）
     * @param workStatus  工作区状态字符（M/D/?/U/空格）
     * @param staged      是否存在已暂存变更
     * @param untracked   是否为未跟踪文件
     * @param conflicted  是否存在冲突
     * @author CH
     * @since 4.0.0.51
     */
    public record FileStatus(
            String path,
            String indexStatus,
            String workStatus,
            boolean staged,
            boolean untracked,
            boolean conflicted
    ) {
    }

    /**
     * 仓库状态快照。
     *
     * @param repository     当前工作区是否为 Git 仓库
     * @param repositoryRoot 仓库根目录
     * @param sessionRoot    会话执行目录
     * @param branch         当前分支
     * @param head           当前提交短 SHA
     * @param detached       是否处于游离 HEAD
     * @param clean          工作区是否干净
     * @param ahead          领先上游的提交数
     * @param behind         落后上游的提交数
     * @param files          变更文件列表
     * @author CH
     * @since 4.0.0.51
     */
    public record Status(
            boolean repository,
            String repositoryRoot,
            String sessionRoot,
            String branch,
            String head,
            boolean detached,
            boolean clean,
            int ahead,
            int behind,
            List<FileStatus> files
    ) {
    }

    /**
     * 文件差异。
     *
     * @param path       文件相对路径
     * @param staged     是否为暂存区差异
     * @param text       统一 diff 文本
     * @param additions  新增行数
     * @param deletions  删除行数
     * @param binary     是否为二进制差异
     * @author CH
     * @since 4.0.0.51
     */
    public record Diff(
            String path,
            boolean staged,
            String text,
            int additions,
            int deletions,
            boolean binary
    ) {
    }

    /**
     * 分支信息。
     *
     * @param name    分支名
     * @param current 是否为当前分支
     * @param remote  是否为远程分支
     * @author CH
     * @since 4.0.0.51
     */
    public record Branch(
            String name,
            boolean current,
            boolean remote
    ) {
    }

    /**
     * 分支列表。
     *
     * @param branches 本地分支
     * @param remotes  远程分支
     * @param current  当前分支名
     * @author CH
     * @since 4.0.0.51
     */
    public record Branches(
            List<Branch> branches,
            List<Branch> remotes,
            String current
    ) {
    }

    /**
     * 提交日志条目。
     *
     * @param sha      完整提交 SHA
     * @param shortSha 短提交 SHA
     * @param subject  提交标题
     * @param message  完整提交信息
     * @param author   作者
     * @param email    作者邮箱
     * @param time     提交时间（ISO-8601）
     * @author CH
     * @since 4.0.0.51
     */
    public record LogEntry(
            String sha,
            String shortSha,
            String subject,
            String message,
            String author,
            String email,
            String time
    ) {
    }

    /**
     * 提交结果。
     *
     * @param sha     提交 SHA
     * @param message 提交信息
     * @author CH
     * @since 4.0.0.51
     */
    public record CommitResult(
            String sha,
            String message
    ) {
    }
}
