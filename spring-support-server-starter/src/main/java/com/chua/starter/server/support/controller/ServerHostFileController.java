package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.ServerFileContent;
import com.chua.starter.server.support.model.ServerFileEntry;
import com.chua.starter.server.support.model.ServerFileOperationResult;
import com.chua.starter.server.support.model.ServerFileRenameRequest;
import com.chua.starter.server.support.model.ServerFileWriteRequest;
import com.chua.starter.server.support.model.ServerFileWatchRequest;
import com.chua.starter.server.support.model.ServerFileWatchTicket;
import com.chua.starter.server.support.service.ServerFileService;
import com.chua.starter.server.support.service.ServerFileWatchService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/files")
public class ServerHostFileController {

    private final ServerFileService serverFileService;
    private final ServerFileWatchService serverFileWatchService;

    @GetMapping
    public ReturnResult<List<ServerFileEntry>> list(
            @PathVariable Integer id,
            @RequestParam(required = false) String path) throws Exception {
        return ReturnResult.ok(serverFileService.listFiles(id, path));
    }

    @GetMapping("/content")
    public ReturnResult<ServerFileContent> content(
            @PathVariable Integer id,
            @RequestParam String path,
            @RequestParam(required = false) Integer maxBytes) throws Exception {
        return ReturnResult.ok(serverFileService.readContent(id, path, maxBytes));
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> download(
            @PathVariable Integer id,
            @RequestParam String path) throws Exception {
        byte[] bytes = serverFileService.download(id, path);
        String fileName = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : (path.contains("\\") ? path.substring(path.lastIndexOf('\\') + 1) : path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<ServerFileOperationResult> upload(
            @PathVariable Integer id,
            @RequestParam(required = false) String directory,
            @RequestPart("file") MultipartFile file) throws Exception {
        return ReturnResult.ok(serverFileService.upload(
                id,
                directory,
                file.getOriginalFilename(),
                file.getInputStream()));
    }

    @PutMapping("/content")
    public ReturnResult<ServerFileOperationResult> writeContent(
            @PathVariable Integer id,
            @RequestBody ServerFileWriteRequest request) throws Exception {
        return ReturnResult.ok(serverFileService.writeContent(
                id,
                request.getPath(),
                request.getContent()));
    }

    @PostMapping("/directories")
    public ReturnResult<ServerFileOperationResult> mkdir(
            @PathVariable Integer id,
            @RequestParam String path) throws Exception {
        return ReturnResult.ok(serverFileService.createDirectory(id, path));
    }

    @PutMapping("/rename")
    public ReturnResult<ServerFileOperationResult> rename(
            @PathVariable Integer id,
            @RequestBody ServerFileRenameRequest request) throws Exception {
        return ReturnResult.ok(serverFileService.rename(id, request.getPath(), request.getTargetPath()));
    }

    @DeleteMapping
    public ReturnResult<ServerFileOperationResult> delete(
            @PathVariable Integer id,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) throws Exception {
        return ReturnResult.ok(serverFileService.delete(id, path, recursive));
    }

    @PostMapping("/watch")
    public ReturnResult<ServerFileWatchTicket> watch(
            @PathVariable Integer id,
            @RequestBody ServerFileWatchRequest request) throws Exception {
        return ReturnResult.ok(serverFileWatchService.createWatch(id, request.getPath()));
    }

    @DeleteMapping("/watch/{watchId}")
    public ReturnResult<Boolean> stopWatch(
            @PathVariable Integer id,
            @PathVariable Long watchId) {
        return ReturnResult.ok(serverFileWatchService.stopWatch(id, watchId));
    }
}
