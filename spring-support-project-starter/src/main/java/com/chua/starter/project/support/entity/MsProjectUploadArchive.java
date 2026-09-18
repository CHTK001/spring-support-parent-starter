package com.chua.starter.project.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
* 部署包归档实体
* <p>
* 对应表 {@code ms_project_upload_archive}，打通「资产 → 部署包 → 部署目录」链路：
* 记录每个版本的部署包归档路径、落盘的目标资产与末端部署目录，支撑资产维度的
* 版本历史追溯与按版本回滚。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部署包归档")
@TableName("ms_project_upload_archive")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsProjectUploadArchive extends SysBase {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
    * 关联项目 标识
    */
    @TableField("ms_project_upload_archive_project_id")
    @Schema(description = "项目ID", example = "1")
    private Long msProjectUploadArchiveProjectId; // msprojectuploadArch Linux Linux Linuxprojectid

    /**
    * 项目名称（冗余）
    */
    @TableField("ms_project_upload_archive_project_name")
    @Schema(description = "项目名称", example = "user-service")
    private String msProjectUploadArchiveProjectName; // msprojectuploadArch Linux Linux Linuxproject名称

    /**
    * 目标主机 标识（资产维度锚点）
    */
    @TableField("ms_project_upload_archive_host_id")
    @Schema(description = "目标主机ID", example = "1")
    private Long msProjectUploadArchiveHostId; // msprojectuploadArch Linux Linux Linux主机标识

    /**
    * 主机名称（冗余）
    */
    @TableField("ms_project_upload_archive_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String msProjectUploadArchiveHostName; // msprojectuploadArch Linux Linux Linux主机名称

    /**
    * 操作类型：DEPLOY/UPGRADE/ROLLBACK
    */
    @TableField("ms_project_upload_archive_action")
    @Schema(description = "操作类型", example = "UPGRADE")
    private String msProjectUploadArchiveAction; // msprojectuploadArch Linux Linux Linux动作

    /**
    * 版本标识（原始包文件名）
    */
    @TableField("ms_project_upload_archive_version")
    @Schema(description = "版本标识(原始包文件名)", example = "app-v1.0.0.jar")
    private String msProjectUploadArchiveVersion; // msprojectuploadArch Linux Linux Linux版本

    /**
    * 包类型：单个_文件/COMPRESSED
    */
    @TableField("ms_project_upload_archive_type")
    @Schema(description = "包类型: SINGLE_FILE/COMPRESSED", example = "SINGLE_FILE")
    private String msProjectUploadArchiveType; // msprojectuploadArch Linux Linux Linux类型

    /**
    * 归档存储路径
    */
    @TableField("ms_project_upload_archive_upload_path")
    @Schema(description = "归档存储路径")
    private String msProjectUploadArchiveUploadPath; // msprojectuploadArch Linux Linux Linuxupload路径

    /**
    * 末端部署目录
    */
    @TableField("ms_project_upload_archive_deploy_dir")
    @Schema(description = "末端部署目录", example = "/opt/apps/user-service")
    private String msProjectUploadArchiveDeployDir; // msprojectuploadArch Linux Linux Linuxdeploydir

    /**
    * 包大小（字节）
    */
    @TableField("ms_project_upload_archive_file_size")
    @Schema(description = "包大小(字节)", example = "52428800")
    private Long msProjectUploadArchiveFileSize; // msprojectuploadArch Linux Linux Linux文件大小

    /**
    * 包 MD5
    */
    @TableField("ms_project_upload_archive_md5")
    @Schema(description = "包MD5")
    private String msProjectUploadArchiveMd5; // msprojectuploadArch Linux Linux Linuxmd5

    /**
    * 部署结果：成功/失败
    */
    @TableField("ms_project_upload_archive_status")
    @Schema(description = "部署结果", example = "SUCCESS")
    private String msProjectUploadArchiveStatus; // msprojectuploadArch Linux Linux Linux状态

    /**
    * 部署时间
    */
    @TableField("ms_project_upload_archive_deploy_time")
    @Schema(description = "部署时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime msProjectUploadArchiveDeployTime; // msprojectuploadArch Linux Linux Linuxdeploy时间
}
