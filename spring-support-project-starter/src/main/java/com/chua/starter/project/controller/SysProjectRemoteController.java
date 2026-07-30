package com.chua.starter.project.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.project.model.remote.ProjectRemoteBackup;
import com.chua.starter.project.model.remote.ProjectRemoteFileContent;
import com.chua.starter.project.model.remote.ProjectRemoteFileEntry;
import com.chua.starter.project.model.remote.ProjectRemoteFileOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteFileRenameRequest;
import com.chua.starter.project.model.remote.ProjectRemoteFileWriteRequest;
import com.chua.starter.project.model.remote.ProjectRemoteOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.model.remote.ProjectRemoteUploadResult;
import com.chua.starter.project.service.SysProjectRemoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Tag(name = "项目远程部署接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/v2/project")
public class SysProjectRemoteController {

    private final SysProjectRemoteService remoteService;

    @Operation(summary = "按 endpoint 解析项目ID")
    @GetMapping("/remote/resolve")
    public ReturnResult<Integer> resolveProjectId(@RequestParam String endpoint) {
        try {
            Integer projectId = remoteService.resolveProjectIdByEndpoint(endpoint);
            if (projectId == null) {
                return ReturnResult.error("未匹配到项目: " + endpoint);
            }
            return ReturnResult.ok(projectId);
        } catch (Exception e) {
            log.error("remote resolve failed, endpoint={}", endpoint, e);
            return ReturnResult.error("解析远程项目失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取远程部署加密公钥")
    @GetMapping("/remote/public-key")
    public ReturnResult<String> publicKey() {
        return ReturnResult.ok("");
    }

    @Operation(summary = "列出远程文件")
    @GetMapping("/{sysProjectId}/remote/files")
    public ReturnResult<List<ProjectRemoteFileEntry>> listFiles(
            @PathVariable Integer sysProjectId,
            @RequestParam(required = false) String path) {
        try {
            return ReturnResult.ok(remoteService.listFiles(sysProjectId, path));
        } catch (Exception e) {
            return ReturnResult.error("读取文件列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "读取远程文件内容")
    @GetMapping("/{sysProjectId}/remote/files/content")
    public ReturnResult<ProjectRemoteFileContent> readFileContent(
            @PathVariable Integer sysProjectId,
            @RequestParam String path,
            @RequestParam(required = false) Integer maxBytes) {
        try {
            return ReturnResult.ok(remoteService.readFileContent(sysProjectId, path, maxBytes));
        } catch (Exception e) {
            return ReturnResult.error("读取文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "下载远程文件")
    @GetMapping("/{sysProjectId}/remote/files/download")
    public ResponseEntity<ByteArrayResource> downloadFile(
            @PathVariable Integer sysProjectId,
            @RequestParam String path) throws Exception {
        byte[] bytes = remoteService.downloadFile(sysProjectId, path);
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

    @Operation(summary = "上传远程文件")
    @PostMapping(value = "/{sysProjectId}/remote/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResult<ProjectRemoteUploadResult> uploadFile(
            @PathVariable Integer sysProjectId,
            @RequestParam(required = false) String directory,
            @RequestPart("file") MultipartFile file) {
        try {
            return ReturnResult.ok(remoteService.uploadFile(
                    sysProjectId,
                    directory,
                    file.getOriginalFilename(),
                    file.getInputStream()
            ));
        } catch (Exception e) {
            return ReturnResult.error("上传失败: " + e.getMessage());
        }
    }

    @Operation(summary = "写入远程文件")
    @PutMapping("/{sysProjectId}/remote/files/content")
    public ReturnResult<ProjectRemoteFileOperationResult> writeFileContent(
            @PathVariable Integer sysProjectId,
            @RequestBody ProjectRemoteFileWriteRequest request) {
        try {
            return ReturnResult.ok(remoteService.writeFileContent(sysProjectId, request.getPath(), request.getContent()));
        } catch (Exception e) {
            return ReturnResult.error("写入文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "创建远程目录")
    @PostMapping("/{sysProjectId}/remote/files/directories")
    public ReturnResult<ProjectRemoteFileOperationResult> createDirectory(
            @PathVariable Integer sysProjectId,
            @RequestParam String path) {
        try {
            return ReturnResult.ok(remoteService.createDirectory(sysProjectId, path));
        } catch (Exception e) {
            return ReturnResult.error("创建目录失败: " + e.getMessage());
        }
    }

    @Operation(summary = "重命名远程文件")
    @PutMapping("/{sysProjectId}/remote/files/rename")
    public ReturnResult<ProjectRemoteFileOperationResult> renameFile(
            @PathVariable Integer sysProjectId,
            @RequestBody ProjectRemoteFileRenameRequest request) {
        try {
            return ReturnResult.ok(remoteService.renameFile(sysProjectId, request.getPath(), request.getTargetPath()));
        } catch (Exception e) {
            return ReturnResult.error("重命名失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除远程文件")
    @DeleteMapping("/{sysProjectId}/remote/files")
    public ReturnResult<ProjectRemoteFileOperationResult> deleteFile(
            @PathVariable Integer sysProjectId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            return ReturnResult.ok(remoteService.deleteFile(sysProjectId, path, recursive));
        } catch (Exception e) {
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @Operation(summary = "远程项目列表")
    @GetMapping("/{sysProjectId}/remote/projects")
    public ReturnResult<List<ProjectRemoteProject>> listProjects(@PathVariable Integer sysProjectId) {
        try {
            return ReturnResult.ok(remoteService.listProjects(sysProjectId));
        } catch (Exception e) {
            return ReturnResult.error("获取项目列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "远程项目详情")
    @GetMapping("/{sysProjectId}/remote/projects/{projectId}")
    public ReturnResult<ProjectRemoteProject> getProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.getProject(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("获取项目失败: " + e.getMessage());
        }
    }

    @Operation(summary = "新建远程项目")
    @PostMapping("/{sysProjectId}/remote/projects")
    public ReturnResult<ProjectRemoteProject> createProject(
            @PathVariable Integer sysProjectId,
            @RequestBody ProjectRemoteProject project) {
        try {
            return ReturnResult.ok(remoteService.createProject(sysProjectId, project));
        } catch (Exception e) {
            return ReturnResult.error("创建项目失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新远程项目")
    @PutMapping("/{sysProjectId}/remote/projects/{projectId}")
    public ReturnResult<ProjectRemoteProject> updateProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestBody ProjectRemoteProject project) {
        try {
            return ReturnResult.ok(remoteService.updateProject(sysProjectId, projectId, project));
        } catch (Exception e) {
            return ReturnResult.error("更新项目失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除远程项目")
    @DeleteMapping("/{sysProjectId}/remote/projects/{projectId}")
    public ReturnResult<Void> deleteProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            remoteService.deleteProject(sysProjectId, projectId);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error("删除项目失败: " + e.getMessage());
        }
    }

    @Operation(summary = "启动远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/start")
    public ReturnResult<ProjectRemoteOperationResult> startProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.startProject(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("启动失败: " + e.getMessage());
        }
    }

    @Operation(summary = "停止远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/stop")
    public ReturnResult<ProjectRemoteOperationResult> stopProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.stopProject(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("停止失败: " + e.getMessage());
        }
    }

    @Operation(summary = "重启远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/restart")
    public ReturnResult<ProjectRemoteOperationResult> restartProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.restartProject(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("重启失败: " + e.getMessage());
        }
    }

    @Operation(summary = "查询远程项目状态")
    @GetMapping("/{sysProjectId}/remote/projects/{projectId}/status")
    public ReturnResult<ProjectRemoteOperationResult> getProjectStatus(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.getProjectStatus(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("获取状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "读取远程项目运行日志")
    @GetMapping("/{sysProjectId}/remote/projects/{projectId}/logs")
    public ReturnResult<String> getProjectLogs(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestParam(defaultValue = "200") int lines) {
        try {
            return ReturnResult.ok(remoteService.getProjectLogs(sysProjectId, projectId, lines));
        } catch (Exception e) {
            return ReturnResult.error("获取日志失败: " + e.getMessage());
        }
    }

    @Operation(summary = "读取远程项目部署日志")
    @GetMapping("/{sysProjectId}/remote/projects/{projectId}/deploy-logs")
    public ReturnResult<String> getProjectDeployLogs(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestParam(defaultValue = "200") int lines) {
        try {
            return ReturnResult.ok(remoteService.getProjectDeployLogs(sysProjectId, projectId, lines));
        } catch (Exception e) {
            return ReturnResult.error("获取升级日志失败: " + e.getMessage());
        }
    }

    @Operation(summary = "备份远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/backup")
    public ReturnResult<ProjectRemoteOperationResult> backupProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String note = body == null ? null : body.get("note");
            return ReturnResult.ok(remoteService.backupProject(sysProjectId, projectId, note));
        } catch (Exception e) {
            return ReturnResult.error("备份失败: " + e.getMessage());
        }
    }

    @Operation(summary = "远程项目备份列表")
    @GetMapping("/{sysProjectId}/remote/projects/{projectId}/backups")
    public ReturnResult<List<ProjectRemoteBackup>> listProjectBackups(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId) {
        try {
            return ReturnResult.ok(remoteService.listProjectBackups(sysProjectId, projectId));
        } catch (Exception e) {
            return ReturnResult.error("获取备份列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "回滚远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/rollback")
    public ReturnResult<ProjectRemoteOperationResult> rollbackProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestBody Map<String, String> body) {
        try {
            return ReturnResult.ok(remoteService.rollbackProject(sysProjectId, projectId, body.get("backupId")));
        } catch (Exception e) {
            return ReturnResult.error("回滚失败: " + e.getMessage());
        }
    }

    @Operation(summary = "部署远程项目")
    @PostMapping("/{sysProjectId}/remote/projects/{projectId}/deploy")
    public ReturnResult<ProjectRemoteOperationResult> deployProject(
            @PathVariable Integer sysProjectId,
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean stopBefore,
            @RequestParam(defaultValue = "true") boolean startAfter,
            @RequestParam(defaultValue = "true") boolean backupBefore,
            @RequestParam(defaultValue = "false") boolean clearBefore,
            @RequestParam(defaultValue = "true") boolean autoExtract,
            @RequestParam(defaultValue = "true") boolean deleteArchive,
            @RequestParam(defaultValue = "false") boolean configCheckBefore,
            @RequestParam(defaultValue = "false") boolean dependencyCheckBefore) {
        try {
            return ReturnResult.ok(remoteService.deployProject(
                    sysProjectId,
                    projectId,
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    stopBefore,
                    startAfter,
                    backupBefore,
                    clearBefore,
                    autoExtract,
                    deleteArchive,
                    configCheckBefore,
                    dependencyCheckBefore
            ));
        } catch (Exception e) {
            return ReturnResult.error("部署失败: " + e.getMessage());
        }
    }
}
