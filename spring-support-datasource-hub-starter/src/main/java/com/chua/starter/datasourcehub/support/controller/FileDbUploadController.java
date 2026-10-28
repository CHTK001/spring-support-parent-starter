package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.FileDbUploadVO;
import com.chua.starter.datasourcehub.support.service.FileDbUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件库（SQLite / DuckDB / H2）库文件上传。
 *
 * <p>新建这类数据源时要填「服务器上的绝对路径」。让人自己把文件传到服务器、
 * 再回来手打路径，是最容易出错的一步（路径打错、权限不对、目录不在白名单内）。
 * 这里给一个窄口子：传上来、落到受管目录、把绝对路径回给前端填进表单。</p>
 *
 * <p>不做成分片上传：单文件库是「一次传一个文件」的场景，走对象存储那套
 * 分片/任务/合并反而要再搬一次文件。大小上限由
 * {@code plugin.datasource-hub.file-db-max-bytes} 控制（默认 64MB）。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
@Tag(name = "数据源中心 - 文件库上传", description = "SQLite / DuckDB / H2 库文件上传并返回绝对路径")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ms/datasource/file-db")
public class FileDbUploadController {

    /**
     * 库文件上传服务
     */
    private final FileDbUploadService fileDbUploadService;

    /**
     * 上传库文件。
     *
     * @param file 库文件（.db / .sqlite / .duckdb / .mv.db …）
     * @return 落盘后的绝对路径
     */
    @Operation(summary = "上传文件库库文件")
    @PostMapping("/upload")
    public ReturnResult<FileDbUploadVO> upload(@RequestParam("file") MultipartFile file) {
        return fileDbUploadService.upload(file);
    }
}