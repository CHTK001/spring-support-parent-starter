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
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
