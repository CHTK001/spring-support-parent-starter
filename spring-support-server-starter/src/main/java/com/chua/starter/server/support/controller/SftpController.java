package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.model.TempFile;
import com.chua.starter.server.support.service.SftpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
* SFTP 文件管理接口
* <p>
* 全部接口按主机维度组织：{@code /ms/server/sftp/{hostId}/...}。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Tag(name = "服务器资产-SFTP", description = "远程文件浏览、编辑与传输")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/sftp/{hostId}")
@RequiredArgsConstructor
public class SftpController {

    /** SFTP 服务 */
    private final SftpService sftpService;

    /**
    * 列出目录
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       目录路径，可为空取主目录
    * @return 条目列表
     */
    @Operation(summary = "列出目录")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> list(@PathVariable Long hostId,
                                                        @RequestParam(required = false) Long identityId,
                                                        @RequestParam(required = false) String path) {
        return sftpService.list(hostId, identityId, path);
    }

    /**
    * 读取文本内容
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       文件路径
    * @return 内容结果
     */
    @Operation(summary = "读取文本内容")
    @GetMapping("/content")
    public ReturnResult<Map<String, Object>> readContent(@PathVariable Long hostId,
                                                         @RequestParam(required = false) Long identityId,
                                                         @RequestParam String path) {
        return sftpService.readContent(hostId, identityId, path);
    }

    /**
    * 写入文本内容（整文件覆盖）
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       文件路径（查询 参数）
    * @param content    新内容
    * @return 结果
     */
    @Operation(summary = "写入文本内容")
    @PutMapping("/content")
    public ReturnResult<Void> writeContent(@PathVariable Long hostId,
                                           @RequestParam(required = false) Long identityId,
                                           @RequestParam String path,
                                           @RequestBody String content) {
        return sftpService.writeContent(hostId, identityId, path, content);
    }

    /**
    * 上传文件
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param directory  目标目录，可为空取根（兼容旧参数名）
    * @param path       目标目录，可为空取根（前端实际使用的参数名，优先于 目录）
    * @param file       文件
    * @return 远端路径
     */
    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public ReturnResult<String> upload(@PathVariable Long hostId,
                                       @RequestParam(required = false) Long identityId,
                                       @RequestParam(required = false) String directory,
                                       @RequestParam(required = false) String path,
                                       @RequestParam("file") MultipartFile file) {
        String targetDir = (path != null && !path.isBlank()) ? path : directory;
        return sftpService.upload(hostId, identityId, targetDir, file);
    }

    /**
    * 下载文件
    * <p>
    * 内容一次性读入内存后随响应输出，临时文件由 temp文件 关闭时删除；
    * 超大文件下载场景建议后续迭代改造为流式管道。
    * </p>
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       远端路径
    * @return 文件字节响应
     */
    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@PathVariable Long hostId,
                                           @RequestParam(required = false) Long identityId,
                                           @RequestParam String path) {
        ReturnResult<TempFile> result = sftpService.download(hostId, identityId, path);
        if (!result.isOk() || result.getData() == null) {
            // 业务失败以 404 表达，前端依据消息提示
            return ResponseEntity.notFound().build();
        }
        /**
        * 尝试。
        * @param result.getData( 结果.获取数据(
         */
        try (TempFile temp = result.getData()) {
            var headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + java.net.URLEncoder.encode(temp.fileName(),
                            java.nio.charset.StandardCharsets.UTF_8) + "\"");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(Files.readAllBytes(temp.path()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
    * 创建目录
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       新目录
    * @return 结果
     */
    @Operation(summary = "创建目录")
    @PostMapping("/mkdir")
    public ReturnResult<Void> mkdir(@PathVariable Long hostId,
                                    @RequestParam(required = false) Long identityId,
                                    @RequestParam String path) {
        return sftpService.mkdir(hostId, identityId, path);
    }

    /**
    * 删除文件或目录
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param path       目标路径
    * @param recursive  目录递归删除
    * @return 结果
     */
    @Operation(summary = "删除文件或目录")
    @DeleteMapping
    public ReturnResult<Void> delete(@PathVariable Long hostId,
                                     @RequestParam(required = false) Long identityId,
                                     @RequestParam String path,
                                     @RequestParam(defaultValue = "false") boolean recursive) {
        return sftpService.delete(hostId, identityId, path, recursive);
    }

    /**
    * 重命名/移动
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识，可为空
    * @param body       从路径 + 转为路径
    * @return 结果
     */
    @Operation(summary = "重命名或移动")
    @PutMapping("/rename")
    public ReturnResult<Void> rename(@PathVariable Long hostId,
                                     @RequestParam(required = false) Long identityId,
                                     @RequestBody Map<String, String> body) {
        return sftpService.rename(hostId, identityId, body.get("fromPath"), body.get("toPath"));
    }
}
