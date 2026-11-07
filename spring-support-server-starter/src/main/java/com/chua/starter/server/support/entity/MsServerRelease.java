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
 * @since 4.0.0.51
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
     * 是否启用 SkyWalking（发布级 APM 注入开关）
     * <p>发布下所有项目共享同一份 agent 与 OAP 地址；项目级 APM 配置优先。</p>
     */
    private Boolean swEnabled;

    /**
     * SkyWalking agent jar 路径（目标主机上的绝对路径）
     */
    private String swJarPath;

    /**
     * SkyWalking service_name 前缀
     * <p>组装后的 service_name 为「<b>前缀-项目名</b>」，前缀为空则只取项目名。</p>
     */
    private String swServiceNamePrefix;

    /**
     * OAP 集群地址（collector.backend_service，如 oap:11800）
     */
    private String swBackendService;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
