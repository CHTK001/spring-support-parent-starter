package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 主机-身份关联实体
 * <p>
 * 对应表 {@code ms_server_host_identity}，一台主机可绑定多个登录身份，
 * 排序 越小优先级越高，远控时默认取第一个可用身份。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "主机身份关联")
@TableName("ms_server_host_identity")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsServerHostIdentity implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 主机 标识
     */
    @TableField("ms_server_host_identity_host_id")
    @Schema(description = "主机ID", example = "1")
    private Long msServerHostIdentityHostId; // ms服务端主机identity主机标识

    /**
     * 身份 标识
     */
    @TableField("ms_server_host_identity_identity_id")
    @Schema(description = "身份ID", example = "1")
    private Long msServerHostIdentityIdentityId; // ms服务端主机identityidentityid

    /**
     * 优先级（越小越优先）
     */
    @TableField("ms_server_host_identity_sort")
    @Schema(description = "优先级", example = "1")
    private Integer msServerHostIdentitySort; // ms服务端主机identity排序
}
