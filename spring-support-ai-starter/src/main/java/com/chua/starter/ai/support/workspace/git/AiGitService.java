package com.chua.starter.ai.support.workspace.git;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.workspace.WorkspacePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Git 工作区服务。
 *
 * <p>基于 JGit 直接操作会话工作目录下的仓库，提供状态、差异、暂存、提交、分支与日志能力。
 * 所有路径参数都必须位于仓库根目录内，禁止操作 {@code .git} 目录。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGitService {

    /**
     * 未跟踪文件
     */
    private static final String STATUS_UNTRACKED = "?";

    /**
     * 冲突状态
     */
    private static final String STATUS_CONFLICTED = "U";

    /**
     * 空状态
     */
    private static final String STATUS_EMPTY = " ";

    /**
     * 短 SHA 长度
     */
    private static final int SHORT_SHA_LENGTH = 8;

    /**
     * 单次差异文本最大字节数
     */
    private static final int MAX_DIFF_BYTES = 512 * 1024;

    /**
     * 未跟踪文件最大读取字节数
     */
    private static final int MAX_UNTRACKED_BYTES = 256 * 1024;

    /**
     * 提交信息最大长度
     */
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 5000;

    /**
     * 日志默认条数
     */
    private static final int DEFAULT_LOG_LIMIT = 30;

    /**
     * 日志最大条数
     */
    private static final int MAX_LOG_LIMIT = 200;

    /**
     * Git 元数据目录名
     */
    private static final String GIT_DIRECTORY = ".git";

    /**
     * 本地分支引用前缀
     */
    private static final String LOCAL_BRANCH_PREFIX = "refs/heads/";

    /**
     * 远程分支引用前缀
     */
    private static final String REMOTE_BRANCH_PREFIX = "refs/remotes/";

    /**
     * 合法分支名匹配规则
     */
    private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,119}$");

    /**
     * 工作目录解析器
     */
    private final WorkspacePathResolver pathResolver;

    /**
     * 查询仓库状态。
     *
     * <p>工作区不是 Git 仓库时返回 {@code repository=false}，前端据此展示初始化入口。</p>
     *
     * @param sessionId 会话标识
     * @return 仓库状态
     */
    public GitModels.Status status(String sessionId) {
        WorkspacePaths paths = workspacePaths(sessionId);
        Path repositoryRoot = pathResolver.findRepositoryRoot(paths.sessionRoot(), paths.workspaceRoot());
        if (repositoryRoot == null) {
            return emptyStatus(paths.sessionRoot());
        }
        try (RepositoryHandle handle = openHandle(paths)) {
            return buildStatus(handle, paths.sessionRoot());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 查询状态失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "查询 Git 状态失败：" + messageOf(e));
        }
    }

    /**
     * 初始化当前会话工作目录为 Git 仓库。
     *
     * @param sessionId 会话标识
     * @return 初始化后的仓库状态
     */
    public GitModels.Status init(String sessionId) {
        WorkspacePaths paths = workspacePaths(sessionId);
        Path repositoryRoot = pathResolver.findRepositoryRoot(paths.sessionRoot(), paths.workspaceRoot());
        if (repositoryRoot != null) {
            return status(sessionId);
        }
        try {
            Files.createDirectories(paths.sessionRoot());
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "创建工作目录失败：" + messageOf(e));
        }
        try (Git ignored = Git.init().setDirectory(paths.sessionRoot().toFile()).call()) {
            log.info("[Git] 已初始化仓库 session={} root={}", sessionId, paths.sessionRoot());
        } catch (Exception e) {
            log.warn("[Git] 初始化仓库失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "初始化 Git 仓库失败：" + messageOf(e));
        }
        return status(sessionId);
    }

    /**
     * 查询文件差异。
     *
     * @param sessionId 会话标识
     * @param path      文件相对路径，为空时返回全部差异
     * @param staged    是否查询暂存区差异
     * @return 差异文本与行数统计
     */
    public GitModels.Diff diff(String sessionId, String path, boolean staged) {
        WorkspacePaths paths = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(paths)) {
            String relative = path == null || path.isBlank() ? null : relativePath(handle.repositoryRoot(), path);
            if (relative != null && !staged && isUntracked(handle, relative)) {
                return untrackedDiff(handle, relative);
            }
            String diffText = renderDiff(handle, relative, staged);
            boolean binary = diffText.contains("Binary files");
            int additions = 0;
            int deletions = 0;
            for (String line : diffText.split("\n", -1)) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    additions++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    deletions++;
                }
            }
            if (diffText.length() > MAX_DIFF_BYTES) {
                diffText = diffText.substring(0, MAX_DIFF_BYTES) + "\n... 差异内容过大，已截断 ...\n";
            }
            return new GitModels.Diff(relative == null ? "" : relative, staged, diffText, additions, deletions, binary);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 查询差异失败 session={} path={}: {}", sessionId, path, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "查询 Git 差异失败：" + messageOf(e));
        }
    }

    /**
     * 暂存文件。
     *
     * @param sessionId 会话标识
     * @param paths     文件相对路径列表，为空时暂存全部变更
     * @return 操作后的仓库状态
     */
    public GitModels.Status stage(String sessionId, List<String> paths) {
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            if (paths == null || paths.isEmpty()) {
                handle.git().add().addFilepattern(".").call();
                handle.git().add().setUpdate(true).addFilepattern(".").call();
            } else {
                for (String raw : paths) {
                    String relative = relativePath(handle.repositoryRoot(), raw);
                    Path file = handle.repositoryRoot().resolve(relative);
                    if (Files.exists(file)) {
                        handle.git().add().addFilepattern(relative).call();
                    } else {
                        handle.git().add().setUpdate(true).addFilepattern(relative).call();
                    }
                }
            }
            return buildStatus(handle, pathsInfo.sessionRoot());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 暂存失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "暂存文件失败：" + messageOf(e));
        }
    }

    /**
     * 取消暂存。
     *
     * @param sessionId 会话标识
     * @param paths     文件相对路径列表，为空时取消全部暂存
     * @return 操作后的仓库状态
     */
    public GitModels.Status unstage(String sessionId, List<String> paths) {
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            boolean hasHead = handle.repository().resolve(Constants.HEAD) != null;
            if (paths == null || paths.isEmpty()) {
                if (hasHead) {
                    handle.git().reset().call();
                } else {
                    handle.git().rm().setCached(true).addFilepattern(".").call();
                }
            } else {
                for (String raw : paths) {
                    String relative = relativePath(handle.repositoryRoot(), raw);
                    if (hasHead) {
                        handle.git().reset().addPath(relative).call();
                    } else {
                        handle.git().rm().setCached(true).addFilepattern(relative).call();
                    }
                }
            }
            return buildStatus(handle, pathsInfo.sessionRoot());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 取消暂存失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "取消暂存失败：" + messageOf(e));
        }
    }

    /**
     * 丢弃工作区变更。
     *
     * @param sessionId 会话标识
     * @param paths     文件相对路径列表
     * @return 操作后的仓库状态
     */
    public GitModels.Status discard(String sessionId, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请选择要丢弃的文件");
        }
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            Status status = handle.git().status().call();
            for (String raw : paths) {
                String relative = relativePath(handle.repositoryRoot(), raw);
                Path file = handle.repositoryRoot().resolve(relative).normalize();
                if (Files.isDirectory(file)) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, "暂不支持丢弃目录：" + relative);
                }
                if (status.getUntracked().contains(relative)) {
                    Files.deleteIfExists(file);
                } else if (status.getAdded().contains(relative)) {
                    handle.git().reset().addPath(relative).call();
                    Files.deleteIfExists(file);
                } else {
                    handle.git().checkout().addPath(relative).call();
                }
            }
            return buildStatus(handle, pathsInfo.sessionRoot());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 丢弃变更失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "丢弃变更失败：" + messageOf(e));
        }
    }

    /**
     * 提交已暂存内容。
     *
     * @param sessionId 会话标识
     * @param message   提交信息
     * @return 提交结果
     */
    public GitModels.CommitResult commit(String sessionId, String message) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "提交信息不能为空");
        }
        if (normalized.length() > MAX_COMMIT_MESSAGE_LENGTH) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "提交信息不能超过 5000 字符");
        }
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            Status status = handle.git().status().call();
            boolean staged = !status.getAdded().isEmpty()
                    || !status.getModified().isEmpty()
                    || !status.getRemoved().isEmpty()
                    || !status.getConflicting().isEmpty();
            if (!staged) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有已暂存的变更，请先暂存文件");
            }
            RevCommit commit = handle.git().commit().setMessage(normalized).call();
            if (commit == null) {
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "提交未生成记录");
            }
            return new GitModels.CommitResult(commit.getName(), normalized);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 提交失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "提交失败：" + messageOf(e));
        }
    }

    /**
     * 查询分支列表。
     *
     * @param sessionId 会话标识
     * @return 本地与远程分支
     */
    public GitModels.Branches branches(String sessionId) {
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            String current = handle.repository().getBranch();
            List<GitModels.Branch> local = collectBranches(handle, current, false);
            List<GitModels.Branch> remote = collectBranches(handle, current, true);
            return new GitModels.Branches(local, remote, current);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 查询分支失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "查询分支失败：" + messageOf(e));
        }
    }

    /**
     * 切换或创建分支。
     *
     * @param sessionId 会话标识
     * @param branch    分支名
     * @param create    是否创建新分支
     * @return 操作后的仓库状态
     */
    public GitModels.Status checkout(String sessionId, String branch, boolean create) {
        String normalized = branch == null ? "" : branch.trim();
        validateBranchName(normalized);
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            boolean exists = handle.repository().findRef(LOCAL_BRANCH_PREFIX + normalized) != null;
            if (create && exists) {
                throw new ApiException(ApiErrorCode.CONFLICT, "分支已存在：" + normalized);
            }
            if (!create && !exists) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "分支不存在：" + normalized);
            }
            handle.git().checkout().setName(normalized).setCreateBranch(create).call();
            return buildStatus(handle, pathsInfo.sessionRoot());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 切换分支失败 session={} branch={}: {}", sessionId, branch, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "切换分支失败：" + messageOf(e));
        }
    }

    /**
     * 查询提交日志。
     *
     * @param sessionId 会话标识
     * @param limit     条数
     * @return 提交日志
     */
    public List<GitModels.LogEntry> log(String sessionId, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LOG_LIMIT : Math.min(limit, MAX_LOG_LIMIT);
        WorkspacePaths pathsInfo = workspacePaths(sessionId);
        try (RepositoryHandle handle = openHandle(pathsInfo)) {
            List<GitModels.LogEntry> entries = new ArrayList<>();
            for (RevCommit commit : handle.git().log().setMaxCount(safeLimit).call()) {
                entries.add(new GitModels.LogEntry(
                        commit.getName(),
                        abbreviate(commit.getName()),
                        commit.getShortMessage(),
                        commit.getFullMessage() == null ? "" : commit.getFullMessage().trim(),
                        commit.getAuthorIdent().getName(),
                        commit.getAuthorIdent().getEmailAddress(),
                        java.time.Instant.ofEpochSecond(commit.getCommitTime()).toString()
                ));
            }
            return entries;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Git] 查询日志失败 session={}: {}", sessionId, e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "查询提交日志失败：" + messageOf(e));
        }
    }

    /**
     * 打开仓库句柄。
     *
     * @param paths 工作目录
     * @return 仓库句柄
     */
    private RepositoryHandle openHandle(WorkspacePaths paths) {
        Path repositoryRoot = pathResolver.findRepositoryRoot(paths.sessionRoot(), paths.workspaceRoot());
        if (repositoryRoot == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "当前工作区不是 Git 仓库，请先初始化仓库");
        }
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(paths.sessionRoot().toFile());
            if (builder.getGitDir() == null) {
                throw new ApiException(ApiErrorCode.NOT_FOUND, "当前工作区不是 Git 仓库，请先初始化仓库");
            }
            Repository repository = builder.build();
            Path workTree = repository.getWorkTree().toPath().toAbsolutePath().normalize();
            if (!workTree.startsWith(paths.workspaceRoot())) {
                repository.close();
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Git 仓库不在当前账号工作目录内");
            }
            return new RepositoryHandle(new Git(repository), workTree);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "打开 Git 仓库失败：" + messageOf(e));
        }
    }

    /**
     * 构造仓库状态。
     *
     * @param handle      仓库句柄
     * @param sessionRoot 会话执行目录
     * @return 仓库状态
     */
    private GitModels.Status buildStatus(RepositoryHandle handle, Path sessionRoot) {
        try {
            Repository repository = handle.repository();
            Status status = handle.git().status().call();
            Map<String, GitModels.FileStatus> files = new LinkedHashMap<>();
            appendStatus(files, status.getAdded(), "A", STATUS_EMPTY);
            appendStatus(files, status.getChanged(), STATUS_EMPTY, "M");
            appendStatus(files, status.getModified(), "M", STATUS_EMPTY);
            appendStatus(files, status.getRemoved(), "D", STATUS_EMPTY);
            appendStatus(files, status.getMissing(), STATUS_EMPTY, "D");
            appendStatus(files, status.getUntracked(), STATUS_UNTRACKED, STATUS_UNTRACKED);
            appendStatus(files, status.getConflicting(), STATUS_CONFLICTED, STATUS_CONFLICTED);
            List<GitModels.FileStatus> sorted = new ArrayList<>(files.values());
            sorted.sort((left, right) -> left.path().compareTo(right.path()));
            String branch = repository.getBranch();
            String fullBranch = repository.getFullBranch();
            boolean detached = fullBranch != null
                    && !fullBranch.startsWith(LOCAL_BRANCH_PREFIX)
                    && !fullBranch.startsWith(REMOTE_BRANCH_PREFIX);
            BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, branch);
            int ahead = tracking == null ? 0 : tracking.getAheadCount();
            int behind = tracking == null ? 0 : tracking.getBehindCount();
            ObjectId head = repository.resolve(Constants.HEAD);
            return new GitModels.Status(
                    true,
                    handle.repositoryRoot().toString(),
                    sessionRoot.toString(),
                    branch,
                    head == null ? "" : abbreviate(head.getName()),
                    detached,
                    sorted.isEmpty(),
                    ahead,
                    behind,
                    sorted
            );
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取 Git 状态失败：" + messageOf(e));
        }
    }

    /**
     * 合并同一文件在暂存区与工作区的状态。
     *
     * @param files       结果集合
     * @param paths       本次状态路径
     * @param indexStatus 暂存区状态
     * @param workStatus  工作区状态
     */
    private void appendStatus(Map<String, GitModels.FileStatus> files,
                              Set<String> paths,
                              String indexStatus,
                              String workStatus) {
        for (String path : paths) {
            GitModels.FileStatus existing = files.get(path);
            String mergedIndex = indexStatus;
            String mergedWork = workStatus;
            if (existing != null) {
                if (!STATUS_EMPTY.equals(existing.indexStatus())) {
                    mergedIndex = existing.indexStatus();
                }
                if (!STATUS_EMPTY.equals(existing.workStatus())) {
                    mergedWork = existing.workStatus();
                }
            }
            boolean staged = !STATUS_EMPTY.equals(mergedIndex) && !STATUS_UNTRACKED.equals(mergedIndex);
            boolean untracked = STATUS_UNTRACKED.equals(mergedIndex) && STATUS_UNTRACKED.equals(mergedWork);
            boolean conflicted = STATUS_CONFLICTED.equals(mergedIndex) || STATUS_CONFLICTED.equals(mergedWork);
            files.put(path, new GitModels.FileStatus(path, mergedIndex, mergedWork, staged, untracked, conflicted));
        }
    }

    /**
     * 生成统一 diff 文本。
     *
     * @param handle   仓库句柄
     * @param relative 文件相对路径，为空时全量
     * @param staged   是否对比暂存区
     * @return diff 文本
     */
    private String renderDiff(RepositoryHandle handle, String relative, boolean staged) {
        Repository repository = handle.repository();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(output)) {
            formatter.setRepository(repository);
            AbstractTreeIterator oldTree = staged
                    ? headTree(repository)
                    : new DirCacheIterator(repository.readDirCache());
            AbstractTreeIterator newTree = staged
                    ? new DirCacheIterator(repository.readDirCache())
                    : new FileTreeIterator(repository);
            if (relative != null) {
                formatter.setPathFilter(PathFilter.create(relative));
            }
            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            for (DiffEntry entry : entries) {
                formatter.format(entry);
            }
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "生成 Git 差异失败：" + messageOf(e));
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /**
     * 读取 HEAD 树。
     *
     * @param repository 仓库
     * @return HEAD 树迭代器，无提交时返回空树
     */
    private AbstractTreeIterator headTree(Repository repository) {
        try {
            ObjectId treeId = repository.resolve(Constants.HEAD + "^{tree}");
            if (treeId == null) {
                return new EmptyTreeIterator();
            }
            CanonicalTreeParser parser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                parser.reset(reader, treeId);
            }
            return parser;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取 HEAD 树失败：" + messageOf(e));
        }
    }

    /**
     * 生成未跟踪文件的新增 diff。
     *
     * @param handle   仓库句柄
     * @param relative 文件相对路径
     * @return 差异
     */
    private GitModels.Diff untrackedDiff(RepositoryHandle handle, String relative) {
        Path file = handle.repositoryRoot().resolve(relative).normalize();
        String content = readUntrackedText(file);
        if (content == null) {
            return new GitModels.Diff(relative, false, "Binary files a/" + relative + " and b/" + relative + " differ",
                    0, 0, true);
        }
        String[] lines = content.split("\n", -1);
        StringBuilder text = new StringBuilder();
        text.append("diff --git a/").append(relative).append(" b/").append(relative).append('\n');
        text.append("new file mode 100644\n");
        text.append("--- /dev/null\n");
        text.append("+++ b/").append(relative).append('\n');
        text.append("@@ -0,0 +1,").append(lines.length).append(" @@\n");
        for (String line : lines) {
            text.append('+').append(line).append('\n');
        }
        return new GitModels.Diff(relative, false, text.toString(), lines.length, 0, false);
    }

    /**
     * 读取未跟踪文本文件内容。
     *
     * @param file 文件
     * @return 文本内容；二进制或过大时返回 {@code null}
     */
    private String readUntrackedText(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_UNTRACKED_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            for (byte value : bytes) {
                if (value == 0) {
                    return null;
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断文件是否为未跟踪文件。
     *
     * @param handle   仓库句柄
     * @param relative 相对路径
     * @return 是否未跟踪
     */
    private boolean isUntracked(RepositoryHandle handle, String relative) {
        try {
            return handle.git().status().call().getUntracked().contains(relative);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 收集分支。
     *
     * @param handle  仓库句柄
     * @param current 当前分支
     * @param remote  是否远程分支
     * @return 分支列表
     */
    private List<GitModels.Branch> collectBranches(RepositoryHandle handle, String current, boolean remote) {
        try {
            List<GitModels.Branch> result = new ArrayList<>();
            handle.git().branchList()
                    .setListMode(remote ? ListBranchCommand.ListMode.REMOTE : ListBranchCommand.ListMode.ALL)
                    .call()
                    .forEach(ref -> {
                        String name = ref.getName();
                        if (name == null) {
                            return;
                        }
                        if (remote && !name.startsWith(REMOTE_BRANCH_PREFIX)) {
                            return;
                        }
                        if (!remote && !name.startsWith(LOCAL_BRANCH_PREFIX)) {
                            return;
                        }
                        String shortName = name.startsWith(REMOTE_BRANCH_PREFIX)
                                ? name.substring(REMOTE_BRANCH_PREFIX.length())
                                : name.substring(LOCAL_BRANCH_PREFIX.length());
                        result.add(new GitModels.Branch(shortName, !remote && shortName.equals(current), remote));
                    });
            result.sort((left, right) -> left.name().compareTo(right.name()));
            return result;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "读取分支失败：" + messageOf(e));
        }
    }

    /**
     * 校验分支名。
     *
     * @param branch 分支名
     */
    private void validateBranchName(String branch) {
        if (branch == null || branch.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "分支名不能为空");
        }
        if (!BRANCH_NAME_PATTERN.matcher(branch).matches()
                || branch.contains("..")
                || branch.contains("@{")
                || branch.endsWith("/")
                || branch.contains("//")) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法分支名：" + branch);
        }
    }

    /**
     * 将用户路径转换为仓库相对路径。
     *
     * @param repositoryRoot 仓库根目录
     * @param raw            用户路径
     * @return 仓库相对路径（使用正斜杠）
     */
    private String relativePath(Path repositoryRoot, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "文件路径不能为空");
        }
        String trimmed = raw.trim().replace('\\', '/');
        if (trimmed.contains("\u0000") || trimmed.startsWith("/") || trimmed.contains(":")) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法文件路径：" + raw);
        }
        Path candidate = repositoryRoot.resolve(trimmed).normalize();
        if (!candidate.startsWith(repositoryRoot)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "文件路径越界：" + raw);
        }
        if (GIT_DIRECTORY.equals(candidate.getFileName() == null ? "" : candidate.getFileName().toString())
                || trimmed.startsWith(GIT_DIRECTORY + "/")) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不允许操作 .git 目录");
        }
        String relative = repositoryRoot.relativize(candidate).toString().replace('\\', '/');
        if (relative.isBlank() || relative.startsWith("../")) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "非法文件路径：" + raw);
        }
        return relative;
    }

    /**
     * 解析工作目录。
     *
     * @param sessionId 会话标识
     * @return 工作目录
     */
    private WorkspacePaths workspacePaths(String sessionId) {
        Path workspaceRoot = pathResolver.agentWorkspace();
        Path sessionRoot = pathResolver.sessionWorkspace(sessionId);
        return new WorkspacePaths(workspaceRoot, sessionRoot);
    }

    /**
     * 构造空仓库状态。
     *
     * @param sessionRoot 会话目录
     * @return 空状态
     */
    private GitModels.Status emptyStatus(Path sessionRoot) {
        return new GitModels.Status(false, "", sessionRoot.toString(), "", "", false, true, 0, 0,
                Collections.emptyList());
    }

    /**
     * 缩短提交 SHA。
     *
     * @param sha 完整 SHA
     * @return 短 SHA
     */
    private String abbreviate(String sha) {
        if (sha == null) {
            return "";
        }
        return sha.length() <= SHORT_SHA_LENGTH ? sha : sha.substring(0, SHORT_SHA_LENGTH);
    }

    /**
     * 提取异常提示。
     *
     * @param e 异常
     * @return 提示文本
     */
    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 工作目录组合。
     *
     * @param workspaceRoot 账号工作目录
     * @param sessionRoot   会话执行目录
     * @author CH
     * @since 4.0.0.51
     */
    private record WorkspacePaths(
            Path workspaceRoot,
            Path sessionRoot
    ) {
    }

    /**
     * 仓库句柄，统一管理 JGit 资源。
     *
     * @author CH
     * @since 4.0.0.51
     */
    private static final class RepositoryHandle implements AutoCloseable {

        /**
         * Git 命令入口
         */
        private final Git git;

        /**
         * 仓库根目录
         */
        private final Path repositoryRoot;

        /**
         * 构造仓库句柄。
         *
         * @param git            Git 命令入口
         * @param repositoryRoot 仓库根目录
         */
        private RepositoryHandle(Git git, Path repositoryRoot) {
            this.git = git;
            this.repositoryRoot = repositoryRoot;
        }

        /**
         * 获取 Git 命令入口。
         *
         * @return Git 命令入口
         */
        private Git git() {
            return git;
        }

        /**
         * 获取底层仓库。
         *
         * @return 仓库
         */
        private Repository repository() {
            return git.getRepository();
        }

        /**
         * 获取仓库根目录。
         *
         * @return 仓库根目录
         */
        private Path repositoryRoot() {
            return repositoryRoot;
        }

        @Override
        public void close() {
            git.close();
        }
    }
}
