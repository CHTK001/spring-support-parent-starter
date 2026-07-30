package com.chua.starter.project.service;

import com.chua.starter.project.model.remote.ProjectRemoteBackup;
import com.chua.starter.project.model.remote.ProjectRemoteFileContent;
import com.chua.starter.project.model.remote.ProjectRemoteFileEntry;
import com.chua.starter.project.model.remote.ProjectRemoteFileOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.model.remote.ProjectRemoteUploadResult;
import java.io.InputStream;
import java.util.List;

public interface SysProjectRemoteService {

    Integer resolveProjectIdByEndpoint(String endpoint);

    List<ProjectRemoteFileEntry> listFiles(Integer sysProjectId, String path) throws Exception;

    ProjectRemoteFileContent readFileContent(Integer sysProjectId, String path, Integer maxBytes) throws Exception;

    byte[] downloadFile(Integer sysProjectId, String path) throws Exception;

    ProjectRemoteUploadResult uploadFile(Integer sysProjectId, String directory, String fileName, InputStream inputStream) throws Exception;

    ProjectRemoteFileOperationResult writeFileContent(Integer sysProjectId, String path, String content) throws Exception;

    ProjectRemoteFileOperationResult createDirectory(Integer sysProjectId, String path) throws Exception;

    ProjectRemoteFileOperationResult renameFile(Integer sysProjectId, String path, String targetPath) throws Exception;

    ProjectRemoteFileOperationResult deleteFile(Integer sysProjectId, String path, boolean recursive) throws Exception;

    List<ProjectRemoteProject> listProjects(Integer sysProjectId);

    ProjectRemoteProject getProject(Integer sysProjectId, String projectId);

    ProjectRemoteProject createProject(Integer sysProjectId, ProjectRemoteProject project);

    ProjectRemoteProject updateProject(Integer sysProjectId, String projectId, ProjectRemoteProject project);

    void deleteProject(Integer sysProjectId, String projectId);

    ProjectRemoteOperationResult startProject(Integer sysProjectId, String projectId);

    ProjectRemoteOperationResult stopProject(Integer sysProjectId, String projectId);

    ProjectRemoteOperationResult restartProject(Integer sysProjectId, String projectId);

    ProjectRemoteOperationResult getProjectStatus(Integer sysProjectId, String projectId);

    String getProjectLogs(Integer sysProjectId, String projectId, int lines);

    String getProjectDeployLogs(Integer sysProjectId, String projectId, int lines);

    ProjectRemoteOperationResult backupProject(Integer sysProjectId, String projectId, String note);

    List<ProjectRemoteBackup> listProjectBackups(Integer sysProjectId, String projectId);

    ProjectRemoteOperationResult rollbackProject(Integer sysProjectId, String projectId, String backupId);

    ProjectRemoteOperationResult deployProject(Integer sysProjectId,
                                               String projectId,
                                               InputStream fileStream,
                                               String fileName,
                                               boolean stopBefore,
                                               boolean startAfter,
                                               boolean backupBefore,
                                               boolean clearBefore,
                                               boolean autoExtract,
                                               boolean deleteArchive,
                                               boolean configCheckBefore,
                                               boolean dependencyCheckBefore);
}
