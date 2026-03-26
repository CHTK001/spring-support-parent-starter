package com.chua.starter.proxy.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统服务器配置表实体类
 *
 * @author CH
 * @since 2025/01/07
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "系统服务器配置表")
@Schema(description = "系统服务器配置表")
@Data
@TableName(value = "proxy_server_setting")
public class SystemServerSetting extends SysBase {

    /**
     * 系统服务器配置ID
     */
    @TableId(value = "proxy_server_setting_id", type = IdType.AUTO)
    @ApiModelProperty(value = "系统服务器配置ID")
    @Schema(description = "系统服务器配置ID")
    @NotNull(message = "系统服务器配置ID不能为空", groups = {UpdateGroup.class})
    private Integer systemServerSettingId;

    /**
     * 系统服务器配置名称
     */
    @TableField(value = "proxy_server_setting_name")
    @ApiModelProperty(value = "系统服务器配置名称")
    @Schema(description = "系统服务器配置名称")
    @NotBlank(message = "系统服务器配置名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Size(max = 255, message = "系统服务器配置名称最大长度要小于 255")
    private String systemServerSettingName;

    /**
     * 系统服务器配置类型 (SPI使用)
     */
    @TableField(value = "proxy_server_setting_type")
    @ApiModelProperty(value = "系统服务器配置类型")
    @Schema(description = "系统服务器配置类型")
    @NotBlank(message = "系统服务器配置类型不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Size(max = 100, message = "系统服务器配置类型最大长度要小于 100")
    private String systemServerSettingType;

    /**
     * 关联的服务器ID (外键)
     */
    @TableField(value = "proxy_server_setting_server_id")
    @ApiModelProperty(value = "关联的服务器ID")
    @Schema(description = "关联的服务器ID")
    @NotNull(message = "关联的服务器ID不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer systemServerSettingServerId;

    /**
     * 排序字段 (用于拖拽排序)
     */
    @TableField(value = "proxy_server_setting_order")
    @ApiModelProperty(value = "排序字段")
    @Schema(description = "排序字段")
    private Integer systemServerSettingOrder;

    /**
     * 配置描述
     */
    @TableField(value = "proxy_server_setting_description")
    @ApiModelProperty(value = "配置描述")
    @Schema(description = "配置描述")
    @Size(max = 500, message = "配置描述最大长度要小于 500")
    private String systemServerSettingDescription;

    /**
     * 配置状态 0:停用 1:启用
     */
    @TableField(value = "proxy_server_setting_enabled")
    @ApiModelProperty(value = "配置状态")
    @Schema(description = "配置状态")
    private Boolean systemServerSettingEnabled;

    /**
     * 配置类名 (用于SPI机制)
     */
    @TableField(value = "proxy_server_setting_class_name")
    @ApiModelProperty(value = "配置类名")
    @Schema(description = "配置类名")
    @Size(max = 500, message = "配置类名最大长度要小于 500")
    private String systemServerSettingClassName;

    /**
     * 配置版本
     */
    @TableField(value = "proxy_server_setting_version")
    @ApiModelProperty(value = "配置版本")
    @Schema(description = "配置版本")
    @Size(max = 50, message = "配置版本最大长度要小于 50")
    private String systemServerSettingVersion;

    /**
     * 配置JSON数据
     */
    @TableField(value = "proxy_server_setting_config")
    @ApiModelProperty(value = "配置JSON数据")
    @Schema(description = "配置JSON数据")
    private String systemServerSettingConfig;
    /**
     * 启动的时候生成
     */
    @TableField(value = "proxy_server_setting_filter_id")
    @ApiModelProperty(value = "启动的时候生成")
    @Schema(description = "启动的时候生成")
    private String systemServerSettingFilterId;

    /** ================= HTTPS 证书配置（直接字段+BLOB，非JSON） ================= */
    /**
     * 是否启用HTTPS
     */
    @TableField("proxy_server_setting_https_enabled")
    @ApiModelProperty("是否启用HTTPS")
    @Schema(description = "是否启用HTTPS")
    private Boolean systemServerSettingHttpsEnabled;

    /**
     * 证书类型: PEM/PFX/JKS
     */
    @TableField("proxy_server_setting_https_cert_type")
    @ApiModelProperty("证书类型: PEM/PFX/JKS")
    @Schema(description = "证书类型: PEM/PFX/JKS")
    private HttpsCertType systemServerSettingHttpsCertType;

    /**
     * PEM证书(BLOB)
     */
    @TableField("proxy_server_setting_https_pem_cert")
    @ApiModelProperty("PEM证书(BLOB)")
    @Schema(description = "PEM证书(BLOB)")
    private byte[] systemServerSettingHttpsPemCert;

    /**
     * PEM私钥(BLOB)
     */
    @TableField("proxy_server_setting_https_pem_key")
    @ApiModelProperty("PEM私钥(BLOB)")
    @Schema(description = "PEM私钥(BLOB)")
    private byte[] systemServerSettingHttpsPemKey;

    /**
     * PEM私钥密码
     */
    @TableField("proxy_server_setting_https_pem_key_password")
    @ApiModelProperty("PEM私钥密码")
    @Schema(description = "PEM私钥密码")
    private String systemServerSettingHttpsPemKeyPassword;

    /**
     * Keystore容器(BLOB) - PFX/JKS
     */
    @TableField("proxy_server_setting_https_keystore")
    @ApiModelProperty("Keystore容器(BLOB)")
    @Schema(description = "Keystore容器(BLOB)")
    private byte[] systemServerSettingHttpsKeystore;

    /**
     * Keystore密码 - PFX/JKS
     */
    @TableField("proxy_server_setting_https_keystore_password")
    @ApiModelProperty("Keystore密码")
    @Schema(description = "Keystore密码")
    private String systemServerSettingHttpsKeystorePassword;

    /**
     * HTTPS 证书类型枚举
     */
    public enum HttpsCertType {
        PEM, PFX, JKS
    }
}





