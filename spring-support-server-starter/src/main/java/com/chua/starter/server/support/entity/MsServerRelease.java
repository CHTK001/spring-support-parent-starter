package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布管理实体
 *
 * @author CH
 */
@Data
@TableName("ms_server_release")
public class MsServerRelease {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发布名称
     */
    private String releaseName;

    /**
     * 主机ID
     */
    private Long hostId;

    /**
     * 主机IP
     */
    private String hostIp;

    /**
     * 描述
     */
    private String description;

    /**
     * 背景图URL
     */
    private String backgroundImage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
