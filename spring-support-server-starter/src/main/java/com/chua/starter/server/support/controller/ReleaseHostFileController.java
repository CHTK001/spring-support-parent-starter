package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.storage.result.GetObjectResult;
import com.chua.common.support.storage.result.ObjectResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.model.vo.HostFileConfigVO;
import com.chua.starter.server.support.model.vo.HostFileDeleteVO;
import com.chua.starter.server.support.model.vo.HostFileDigestVO;
import com.chua.starter.server.support.model.vo.HostFileEntryVO;
import com.chua.starter.server.support.model.vo.HostFilePageVO;
import com.chua.starter.server.support.service.HostFileDigestService;
import com.chua.starter.server.support.service.HostFileService;
import com.chua.starter.server.support.util.HostFilePaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 主机文件（运行本服务的这台机器）的浏览、目录管理与下载。
 *
 * <p>本控制器只做 HTTP 映射，业务与存储访问都在 {@link HostFileService}：</p>
 * <ul>
 *   <li>浏览 / 下载 / 删除文件对所有存储后端可用；</li>
 *   <li>建目录、重命名、算 MD5 只对本地存储可用，其它后端会拿到明确的「不支持」，
 *       不会出现界面点了没反应；</li>
 *   <li>上传不走这里：分片、断点与全局进度 SSE 由 filesystem-starter 的
 *       {@code /v2/file/upload-task/*} 提供，本页只传目标目录。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Tag(name = "主机文件", description = "运行本服务的这台机器的文件浏览、目录管理与下载")
@Slf4j
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host-file")
public class ReleaseHostFileController {

    /**
     * 主机文件服务
     */
    private final HostFileService hostFileService;

    /**
     * MD5 指纹台账（上传完成后复核并入库）
     */
    private final HostFileDigestService digestService;

    /**
     * 构造。
     *
     * @param hostFileService 主机文件服务
     * @param digestService   MD5 指纹台账
     */
    public ReleaseHostFileController(HostFileService hostFileService, HostFileDigestService digestService) {
        this.hostFileService = hostFileService;
        this.digestService = digestService;
    }

    /**
     * 存储信息：默认提供者、本机根目录、可切换的提供者。
     *
     * @param bucket 存储提供者名，留空用默认提供者
     * @return 存储信息
     */
    @Operation(summary = "主机文件存储信息")
    @GetMapping("/config")
    public ReturnResult<HostFileConfigVO> config(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket) {
        return hostFileService.config(bucket);
    }

    /**
     * 列目录。
     *
     * @param bucket  存储提供者名，留空用默认提供者
     * @param path    目录 key，留空为根
     * @param keyword 名称关键字
     * @param sort    排序字段：name / size / time
     * @param order   排序方向：asc / desc
     * @param limit   每页条数
     * @param offset  跳过条数
     * @return 目录页
     */
    @Operation(summary = "列出主机文件")
    @GetMapping("/list")
    public ReturnResult<HostFilePageVO> list(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "目录 key") @RequestParam(name = "path", required = false) String path,
            @Parameter(description = "名称关键字") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "排序字段 name/size/time")
            @RequestParam(name = "sort", required = false, defaultValue = "name") String sort,
            @Parameter(description = "排序方向 asc/desc")
            @RequestParam(name = "order", required = false, defaultValue = "asc") String order,
            @Parameter(description = "每页条数") @RequestParam(name = "limit", required = false, defaultValue = "200") int limit,
            @Parameter(description = "跳过条数") @RequestParam(name = "offset", required = false, defaultValue = "0") int offset) {
        return hostFileService.list(bucket, path, keyword, sort, order, limit, offset);
    }

    /**
     * 新建目录。
     *
     * @param bucket 存储提供者名
     * @param path   父目录 key
     * @param name   目录名
     * @return 新目录条目
     */
    @Operation(summary = "新建目录")
    @PostMapping("/dir")
    public ReturnResult<HostFileEntryVO> mkdir(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "父目录 key") @RequestParam(name = "path", required = false) String path,
            @Parameter(description = "目录名") @RequestParam(name = "name") String name) {
        return hostFileService.mkdir(bucket, path, name);
    }

    /**
     * 重命名。
     *
     * @param bucket 存储提供者名
     * @param key    目标 key
     * @param name   新名称
     * @return 重命名后的条目
     */
    @Operation(summary = "重命名主机文件")
    @PutMapping("/rename")
    public ReturnResult<HostFileEntryVO> rename(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "目标 key") @RequestParam(name = "key") String key,
            @Parameter(description = "新名称") @RequestParam(name = "name") String name) {
        return hostFileService.rename(bucket, key, name);
    }

    /**
     * 移动到另一个目录。
     *
     * @param bucket    存储提供者名
     * @param key       目标 key
     * @param targetDir 目标目录 key
     * @return 移动后的条目
     */
    @Operation(summary = "移动主机文件")
    @PutMapping("/move")
    public ReturnResult<HostFileEntryVO> move(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "目标 key") @RequestParam(name = "key") String key,
            @Parameter(description = "目标目录 key") @RequestParam(name = "targetDir", required = false) String targetDir) {
        return hostFileService.move(bucket, key, targetDir);
    }

    /**
     * 删除文件或目录（目录递归删除）。
     *
     * <p>路径本身是实现细节：{@code key} 里带不带分隔符都会被归一化。</p>
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 是否删除成功
     */
    @Operation(summary = "删除主机文件")
    @DeleteMapping
    public ReturnResult<Boolean> delete(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "文件 key") @RequestParam(name = "key") String key) {
        return hostFileService.delete(bucket, key);
    }

    /**
     * 批量删除。
     *
     * @param bucket 存储提供者名
     * @param keys   文件 key 列表
     * @return 成功 / 失败汇总
     */
    @Operation(summary = "批量删除主机文件")
    @PostMapping("/delete")
    public ReturnResult<HostFileDeleteVO> deleteBatch(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @RequestBody List<String> keys) {
        return hostFileService.deleteBatch(bucket, keys);
    }

    /**
     * 计算文件 MD5（仅本地存储）。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 指纹
     */
    @Operation(summary = "计算主机文件 MD5")
    @GetMapping("/md5")
    public ReturnResult<HostFileDigestVO> md5(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "文件 key") @RequestParam(name = "key") String key) {
        return hostFileService.md5(bucket, key);
    }

    /**
     * 复核并登记 MD5：上传完成后由前端带声明的指纹调用，服务端按文件真实字节重算，
     * 一致才写入台账；之后的列表直接显示台账值，不再重算。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @param md5    前端声明的 MD5（小写 hex）
     * @return 复核后的实际 MD5
     */
    @Operation(summary = "复核并登记主机文件 MD5（上传后用）")
    @PostMapping("/md5/confirm")
    public ReturnResult<String> confirmMd5(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "文件 key") @RequestParam(name = "key") String key,
            @Parameter(description = "声明的 MD5（小写 hex）") @RequestParam(name = "md5") String md5) {
        return digestService.confirm(bucket, key, md5);
    }

    /**
     * 读取文本类文件开头，用于在线预览。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @return 文本内容
     */
    @Operation(summary = "预览文本文件")
    @GetMapping("/text")
    public ReturnResult<String> text(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "文件 key") @RequestParam(name = "key") String key) {
        return hostFileService.readText(bucket, key);
    }

    /**
     * 下载 / 内联预览文件。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @param inline 为真时按内容类型内联展示（图片 / PDF / 文本预览用），否则作为附件下载
     * @return 文件流
     */
    @Operation(summary = "下载主机文件")
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @Parameter(description = "存储提供者名") @RequestParam(name = "bucket", required = false) String bucket,
            @Parameter(description = "文件 key") @RequestParam(name = "key") String key,
            @Parameter(description = "是否内联预览") @RequestParam(name = "inline", required = false,
                    defaultValue = "false") boolean inline) {
        GetObjectResult result = hostFileService.open(bucket, key);
        if (result == null || result.getInputStream() == null
                || ObjectResult.ResultCode.FAILURE == result.getResultCode()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String fileName = result.getMetadata() == null || result.getMetadata().getName() == null
                ? HostFilePaths.nameOf(key) : result.getMetadata().getName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        InputStream stream = result.getInputStream();
        String contentType = inline
                ? HostFilePaths.contentTypeOf(fileName) : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String disposition = (inline ? "inline" : "attachment")
                + "; filename=\"file\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(stream));
    }
}
