package com.chua.starter.filesystem.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.filesystem.support.log.FileLogStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件操作日志接口（{@code /v2/file/log}）。
 *
 * <p>读取 {@link FileLogStore} 内存环形缓冲中的文件操作流水，供前端
 * 「文件日志」页展示。默认返回最近 200 条，支持按操作类型与目标关键字
 * 过滤；也提供清空接口（仅清内存流水）。</p>
 *
 * <p>与上传任务接口同通道（应用登录态，Spring 侧 Controller），前端
 * 走 {@code /v2} 代理直达。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/file/log")
@Tag(name = "文件日志接口")
public class FileLogController {

    /**
     * 默认返回条数上限
     */
    private static final int DEFAULT_LIMIT = 200;

    /**
     * 最大允许条数上限
     */
    private static final int MAX_LIMIT = 2000;

    /**
     * 日志存储器
     */
    private final FileLogStore fileLogStore;

    /**
     * 创建控制器。
     *
     * @param fileLogStore 日志存储器（可传 {@link FileLogStore#getInstance()} 全局单例）
     */
    public FileLogController(FileLogStore fileLogStore) {
        this.fileLogStore = fileLogStore;
    }

    /**
     * 列出文件操作日志（新→旧）。
     *
     * @param operation 操作类型过滤（如 UPLOAD / DELETE / TASK_CREATE；空 = 不过滤）
     * @param keyword   目标关键字过滤（文件路径/文件名包含；空 = 不过滤）
     * @param limit     最多返回条数（默认 200，最大 2000）
     * @return 日志条目列表
     */
    @Operation(summary = "列出文件操作日志")
    @GetMapping("/list")
    public ReturnResult<List<FileLogStore.FileLogEntry>> list(
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return ReturnResult.ok(fileLogStore.list(operation, keyword, capped));
    }

    /**
     * 清空文件操作日志。
     *
     * @return 被清空的条数
     */
    @Operation(summary = "清空文件操作日志")
    @DeleteMapping("/clear")
    public ReturnResult<Integer> clear() {
        return ReturnResult.ok(fileLogStore.clear());
    }
}