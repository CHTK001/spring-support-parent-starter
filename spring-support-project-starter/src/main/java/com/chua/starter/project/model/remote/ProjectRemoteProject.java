package com.chua.starter.project.model.remote;

import lombok.Data;

@Data
public class ProjectRemoteProject {
    private String projectId;
    private String projectName;
    /** springboot / springboot_resource_isolation / tomcat / static */
    private String projectType;
    private String rootDirectory;
    private String libDirectory;
    private String configDirectory;
    private String tomcatHome;
    private String logDirectory;
    private String startupLogFile;
    private String startScript;
    private String stopScript;
    private String restartScript;
    private String statusScript;
    private String mainArtifact;
    private String mainArtifactMd5;
    private String jvmArgs;
    private String springArgs;
    private String healthCheckUrl;
    /** running / stopped / unknown / error */
    private String runtimeStatus;
    private Long pid;
    private String description;
    private String lastOperationTime;
    private String lastOperationMessage;
}
