package com.chua.starter.datasourcehub.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传的驱动 jar。
 *
 * <p>驱动 jar 落在应用管理的目录里，台账只记元信息与落盘文件名；
 * 真正的字节在文件系统上（{@code plugin.datasource.hub.driver-jar-dir}）。</p>
 *
 * <p>JDBC 驱动与「单 jar 的中间件客户端」共用这张表：前者校验入口类是不是
 * {@link java.sql.Driver}，后者校验是不是 {@code MiddlewareClientProvider}，
 * 由 {@code jar_kind} 区分。</p>
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
     * 驱动类全名，如 {@code org.h2.Driver}。
     *
     * <p>{@code kind=jdbc} 时是 {@link java.sql.Driver} 的实现类；
     * {@code kind=middleware} 时是 {@code MiddlewareClientProvider} 的实现类，
     * 两类 jar 的「入口类」语义不同但都是「jar 里那个能被实例化的类」。</p>
     */
    @TableField("driver_class_name")
    private String driverClassName;

    /**
     * jar 类别：{@code jdbc} 或 {@code middleware}。
     *
     * <p>同一张台账管两类 jar，是为了复用上传、落盘、指纹、隔离加载、删除保护这一整套；
     * 二者唯一不同的是「上传时用哪个接口校验入口类」。</p>
     */
    @TableField("jar_kind")
    private String jarKind;

    /**
     * 中间件类型名（{@code kafka} / {@code mongodb} …），仅 {@code kind=middleware} 有值。
     *
     * <p>取自实现类的 {@code name()}，由后端回填，前端只读：类型名是注册表的键，
     * 让用户手填就会出现「jar 里叫 kafka、这里填 kafka2」这种对不上的情况。</p>
     */
    @TableField("provider_name")
    private String providerName;

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