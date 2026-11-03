package com.chua.starter.ai.support.workspace.git;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话工作区 Git 操作接口。
 *
 * <p>所有接口都以会话工作目录为作用域，路径参数使用仓库相对路径。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/sessions/{id}/git")
@RequiredArgsConstructor
public class AiGitController {

    /**
     * Git 工作区服务
     */
    private final AiGitService gitService;

    /**
     * 查询仓库状态。
     *
     * @param id 会话标识
     * @return 仓库状态
     */
    @GetMapping("/status")
    public ReturnResult<GitModels.Status> status(@PathVariable("id") String id) {
        return ReturnResult.ok(gitService.status(id));
    }

    /**
     * 初始化 Git 仓库。
     *
     * @param id 会话标识
     * @return 初始化后的仓库状态
     */
    @PostMapping("/init")
    public ReturnResult<GitModels.Status> init(@PathVariable("id") String id) {
        return ReturnResult.ok(gitService.init(id));
    }

    /**
     * 查询文件差异。
     *
     * @param id     会话标识
     * @param path   文件相对路径，为空时返回全部差异
     * @param staged 是否查询暂存区差异
     * @return 差异
     */
    @GetMapping("/diff")
    public ReturnResult<GitModels.Diff> diff(
            @PathVariable("id") String id,
            @RequestParam(name = "path", required = false) String path,
            @RequestParam(name = "staged", defaultValue = "false") boolean staged) {
        return ReturnResult.ok(gitService.diff(id, path, staged));
    }

    /**
     * 暂存文件。
     *
     * @param id      会话标识
     * @param request 文件路径列表
     * @return 操作后的仓库状态
     */
    @PostMapping("/stage")
    public ReturnResult<GitModels.Status> stage(
            @PathVariable("id") String id,
            @RequestBody GitPathsRequest request) {
        return ReturnResult.ok(gitService.stage(id, request.paths()));
    }

    /**
     * 取消暂存。
     *
     * @param id      会话标识
     * @param request 文件路径列表
     * @return 操作后的仓库状态
     */
    @PostMapping("/unstage")
    public ReturnResult<GitModels.Status> unstage(
            @PathVariable("id") String id,
            @RequestBody GitPathsRequest request) {
        return ReturnResult.ok(gitService.unstage(id, request.paths()));
    }

    /**
     * 丢弃工作区变更。
     *
     * @param id      会话标识
     * @param request 文件路径列表
     * @return 操作后的仓库状态
     */
    @PostMapping("/discard")
    public ReturnResult<GitModels.Status> discard(
            @PathVariable("id") String id,
            @RequestBody GitPathsRequest request) {
        return ReturnResult.ok(gitService.discard(id, request.paths()));
    }

    /**
     * 提交已暂存内容。
     *
     * @param id      会话标识
     * @param request 提交信息
     * @return 提交结果
     */
    @PostMapping("/commit")
    public ReturnResult<GitModels.CommitResult> commit(
            @PathVariable("id") String id,
            @RequestBody GitCommitRequest request) {
        return ReturnResult.ok(gitService.commit(id, request.message()));
    }

    /**
     * 查询分支列表。
     *
     * @param id 会话标识
     * @return 分支列表
     */
    @GetMapping("/branches")
    public ReturnResult<GitModels.Branches> branches(@PathVariable("id") String id) {
        return ReturnResult.ok(gitService.branches(id));
    }

    /**
     * 切换或创建分支。
     *
     * @param id      会话标识
     * @param request 分支请求
     * @return 操作后的仓库状态
     */
    @PostMapping("/checkout")
    public ReturnResult<GitModels.Status> checkout(
            @PathVariable("id") String id,
            @RequestBody GitCheckoutRequest request) {
        return ReturnResult.ok(gitService.checkout(id, request.branch(), request.create()));
    }

    /**
     * 查询提交日志。
     *
     * @param id    会话标识
     * @param limit 条数
     * @return 提交日志
     */
    @GetMapping("/log")
    public ReturnResult<List<GitModels.LogEntry>> log(
            @PathVariable("id") String id,
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        return ReturnResult.ok(gitService.log(id, limit));
    }

    /**
     * 文件路径请求。
     *
     * @param paths 文件相对路径列表
     * @author CH
     * @since 4.0.0.51
     */
    public record GitPathsRequest(
            List<String> paths
    ) {
    }

    /**
     * 提交请求。
     *
     * @param message 提交信息
     * @author CH
     * @since 4.0.0.51
     */
    public record GitCommitRequest(
            String message
    ) {
    }

    /**
     * 分支请求。
     *
     * @param branch 分支名
     * @param create 是否创建新分支
     * @author CH
     * @since 4.0.0.51
     */
    public record GitCheckoutRequest(
            String branch,
            boolean create
    ) {
    }
}
