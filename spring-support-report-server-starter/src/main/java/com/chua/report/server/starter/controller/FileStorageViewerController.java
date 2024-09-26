package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.oss.result.ListObjectResult;
import com.chua.report.server.starter.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * OSS预览
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/file/storage")
@Tag(name = "文件存储")
@RequiredArgsConstructor
public class FileStorageViewerController {

    private final FileStorageService fileStorageService;
    /**
     *
     *
     * 分页查询数据
     *
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("viewer")
    public ReturnResult<ListObjectResult> viewer(
                                                 Integer fileStorageId,
                                                 @RequestParam(required = false, defaultValue = "/")String path,
                                                 @RequestParam(required = false, defaultValue = "10")Integer limit,
                                                 @RequestParam(required = false)String marker) {
        return ReturnResult.ok(fileStorageService.viewer(fileStorageId, path, limit, marker));
    }

}
