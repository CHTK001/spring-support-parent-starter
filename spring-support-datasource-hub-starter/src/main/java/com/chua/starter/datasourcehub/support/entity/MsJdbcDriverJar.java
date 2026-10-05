package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传的 JDBC 驱动 jar。
 *
 * <p>驱动 jar 落在应用管理的目录里，台账只记元信息与落盘文件名；
 * 真正的字节在文件系统上（{@code datasource.hub.driver-jar-dir}）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Data
@TableName("ms_jdbc_driver_jar")
public class MsJdbcDriverJar implements Serializable {

    /**
     * 序列版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 上传时的原始文件名
     */
    @TableField("jar_name")
    private String jarName;

    /**
     * 落盘文件名（含内容指纹）
     */
    @TableField("stored_name")
    private String storedName;

    /**
     * 驱动类全名，如 {@code org.h2.Driver}
     */
    @TableField("driver_class_name")
    private String driverClassName;

    /**
     * 字节数
     */
    @TableField("jar_size")
    private Long jarSize;

    /**
     * 内容 sha256
     */
    @TableField("jar_sha256")
    private String jarSha256;

    /**
     * 备注
     */
    @TableField("jar_remark")
    private String jarRemark;

    /**
     * 上传时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}