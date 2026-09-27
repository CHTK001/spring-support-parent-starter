package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目管理实体
 *
 * @author CH
 */
@Data
@TableName("ms_server_project")
public class MsServerProject {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发布ID
     */
    private Long releaseId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目类型（springboot/fatjar/static/nginx等）
     */
    private String projectType;

    /**
     * 主入口名称（如 jar 文件名 / 二进制名 / 静态目录，脚本模板据此生成）
     */
    private String mainEntry;

    /**
     * 启动脚本
     */
    private String startScript;

    /**
     * 停止脚本
     */
    private String stopScript;

    /**
     * 重启脚本
     */
    private String restartScript;

    /**
     * 项目状态（running/stopped/unknown）
     */
    private String status;

    /**
     * 项目地址
     */
    private String projectPath;

    /**
     * 日志地址
     */
    private String logPath;

    /**
     * 重启时日志处理方式：append=追加保留历史（默认）；truncate=清空后重写
     */
    private String logMode;

    /**
     * 描述
     */
    private String description;

    /**
     * 项目图标（图标选择器值，如 ep:cpu / ri:java-line）
     */
    private String icon;

    /**
     * 环境变量（JSON 数组，元素形如 {"key":"K","value":"V"}），写入启动/重启脚本
     */
    private String envVars;

    /**
     * 命令参数（JSON 数组，元素为参数字符串，如 "--spring.profiles.active=prod"），写入启动/重启脚本
     */
    private String cmdArgs;

    /**
     * JVM 参数（JSON 数组，仅 Java 类项目使用，位于 -jar 之前，如 "-Xmx512m"），写入启动/重启脚本
     */
    private String jvmArgs;

    /**
     * 状态检测脚本（返回运行状态，供守护进程与状态查询使用）
     */
    private String statusScript;

    /**
     * 系统服务名（非空表示已挂载为系统服务，Linux 为 systemd 单元名，Windows 为计划任务前缀）
     */
    private String serviceName;

    /**
     * 是否开机自启动
     */
    private Boolean autostart;

    /**
     * 是否启用守护进程（定时检测状态，异常自动重启）
     */
    private Boolean guardian;

    /**
     * 是否启用（false=已禁用，禁用项目卡片置灰且排到后面，不参与操作）
     */
    private Boolean enabled;

    /**
     * 运行时进程 ID（启动成功后回写，停止后清空）
     */
    private String pid;

    /**
     * SpringBoot 专属配置（JSON，形如 [{"key":"server.port","value":"8081"}]，
     * 启动时生成外部配置文件并以 spring.config.additional-location 覆盖应用内配置）
     */
    private String appConfig;

    /**
     * 分离式部署的配置目录（如 config）
     */
    private String configDir;

    /**
     * 分离式部署的依赖库目录（如 lib）
     */
    private String libDir;

    /**
     * Nginx 安装 / 配置目录
     */
    private String nginxDir;

    /**
     * 本次启动时间（运行时长基准，启动时写入、停止时清空）
     */
    private LocalDateTime runningSince;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
