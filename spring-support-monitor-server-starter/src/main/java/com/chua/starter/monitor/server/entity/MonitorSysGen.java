package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.crypto.Codec;
import com.chua.common.support.datasource.annotation.ColumnDesc;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.utils.StringUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Base64;

/**
 * 数据库配置
 */
@ApiModel(description="数据库配置")
@Schema(description="数据库配置")
@Data
@TableName(value = "monitor_sys_gen")
public class MonitorSysGen implements Serializable {
    @TableId(value = "gen_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer genId;

    /**
     * 数据库类型
     */
    @TableField(value = "gen_name")
    @ApiModelProperty(value="数据库名称说明")
    @Schema(description="数据库名称说明")
    @Size(max = 255,message = "数据库类型最大长度要小于 255")
    private String genName;

    /**
     * 数据库类型
     */
    @TableField(value = "gen_type")
    @ApiModelProperty(value="数据库类型")
    @Schema(description="数据库类型")
    @Size(max = 255,message = "数据库类型最大长度要小于 255")
    private String genType;

    /**
     * 数据库用户名
     */
    @TableField(value = "gen_user")
    @ApiModelProperty(value="数据库用户名")
    @Schema(description="数据库用户名")
    @Size(max = 255,message = "数据库用户名最大长度要小于 255")
    private String genUser;

    /**
     * 数据库密码
     */
    @TableField(value = "gen_password")
    @ApiModelProperty(value="数据库密码")
    @Schema(description="数据库密码")
    @Size(max = 255,message = "数据库密码最大长度要小于 255")
    private String genPassword;

    /**
     * 数据库地址
     */
    @TableField(value = "gen_host")
    @ApiModelProperty(value="数据库地址")
    @Schema(description="数据库地址")
    @Size(max = 255,message = "数据库地址最大长度要小于 255")
    private String genHost;
    /**
     * 数据库端口"
     */
    @TableField(value = "gen_port")
    @ApiModelProperty(value="数据库端口")
    @Schema(description="数据库端口\"")
    @Size(max = 255,message = "数据库地址最大长度要小于 255")
    private Integer genPort;

    /**
     * 数据库说明
     */
    @TableField(value = "gen_desc")
    @ApiModelProperty(value="数据库说明")
    @Schema(description="数据库说明")
    @Size(max = 255,message = "数据库说明最大长度要小于 255")
    private String genDesc;

    /**
     * 数据库驱动地址
     */
    @TableField(value = "gen_driver")
    @ApiModelProperty(value="数据库驱动地址")
    @Schema(description="数据库驱动地址")
    @Size(max = 255,message = "数据库驱动地址最大长度要小于 255")
    private String genDriver;

    /**
     * 数据库驱动下载地址
     */
    @TableField(value = "gen_driver_url")
    @ApiModelProperty(value="数据库驱动下载地址")
    @Schema(description="数据库驱动下载地址")
    @Size(max = 255,message = "数据库驱动下载地址最大长度要小于 255")
    private String genDriverUrl;

    /**
     * 数据库名称
     */
    @TableField(value = "gen_database")
    @ApiModelProperty(value="数据库名称")
    @Schema(description="数据库名称")
    @Size(max = 255,message = "数据库名称最大长度要小于 255")
    private String genDatabase;

    /**
     * 文件数据库数据目录
     */
    @TableField(value = "gen_database_file")
    @ApiModelProperty(value="文件数据库数据目录")
    @Schema(description="文件数据库数据目录")
    @Size(max = 255,message = "文件数据库数据目录最大长度要小于 255")
    private String genDatabaseFile;

    /**
     * 加密方式
     */
    @TableField(value = "gen_uid")
    @ApiModelProperty(value="文件数据库数据目录")
    @Schema(description="文件数据库数据目录")
    @Size(max = 255,message = "文件数据库数据目录最大长度要小于 255")
    private String genUid;

    private static final long serialVersionUID = 1L;
    /**
     * 表名称
     */
    @ColumnDesc("表名称")
    @TableField(exist = false)
    private String tabName;

    /**
     * 描述
     */
    @ColumnDesc("描述")
    @TableField(exist = false)
    private String tabDesc;

    /**
     * 是否支持备份
     */
    @ColumnDesc("是否支持备份")
    @TableField(exist = false)
    private Boolean supportBackup;
    /**
     * url
     */
    @ColumnDesc("url")
    @TableField(exist = false)
    private String genUrl;

    /**
     * 新数据库选项
     *
     * @return {@link DataSourceOptions}
     */
    public DataSourceOptions newDatabaseOptions() {
        return newDatabaseOptions(true);
    }
    /**
     * 新数据库选项
     * 新数据库配置
     *
     * @param codecPassword 编解码器密码
     * @return {@link DataSourceOptions}
     */
    public DataSourceOptions newDatabaseOptions(boolean codecPassword) {
        DataSourceOptions databaseOptions = new DataSourceOptions();
        databaseOptions.setName(genDatabase);
        databaseOptions.setDriver(genDriver);
        databaseOptions.setDatabaseFile(genDatabaseFile);
        databaseOptions.setUsername(genUser);
        databaseOptions.setDriverPath(genDriverUrl);
        databaseOptions.setPassword(genPassword);
        databaseOptions.setGenType(genType);
        databaseOptions.setSecretKey(genUid);
        databaseOptions.setUrl(genHost + ":" + genPort);
        this.genUrl = initialGenUrl(databaseOptions, codecPassword);
        databaseOptions.setUrl(genUrl);
        if(null != genUid && codecPassword) {
            databaseOptions.setPassword(Codec.build(databaseOptions.getSecretKeyType(), genUid).decodeBase64(StringUtils.utf8Str(Base64.getDecoder().decode(genPassword))));
        }

        return databaseOptions;
    }

    public String initialGenUrl(DataSourceOptions databaseOptions, boolean codecPassword) {
        if(!"jdbc".equalsIgnoreCase(genType)) {
            return genHost + ":" + genPort;
        }
        Dialect dialect = DialectFactory.createDriver(genDriver);
        if(null != dialect) {
            return dialect.getUrl(databaseOptions);
        }

        return genHost + ":" + genPort;
    }
}