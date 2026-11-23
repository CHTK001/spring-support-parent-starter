package com.chua.starter.datapool.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据源实体（数据源中心台账）。
 *
 * <p>表 {@code datasource_info}：业务列一律以完整表名作前缀（如 {@code datasource_info_name}）。
 * 创建人/更新人/创建时间/更新时间等审计字段继承自 {@link SysBase}，落通用列
 * {@code create_name / create_by / create_time / update_name / update_by / update_time}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源")
@TableName("datasource_info")
public class MsDatasource extends SysBase {

    /**
     * 串行版本 UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 数据源名称
     */
    @TableField("datasource_info_name")
    @Schema(description = "数据源名称", example = "订单库")
    private String datasourceName;

    /**
     * 数据源类型（jdbc/redis/influxdb/neo4j/nacos/zookeeper/nginx）
     */
    @TableField("datasource_info_type")
    @Schema(description = "数据源类型", example = "jdbc")
    private String datasourceType;

    /**
     * 服务端大版本（当前仅 influxdb 使用：2=Flux/Bucket，3=SQL/InfluxQL/Database）。
     */
    @TableField("datasource_info_version")
    @Schema(description = "服务端大版本（influxdb: 2/3）", example = "3")
    private String datasourceVersion;

    /**
     * 主机地址
     */
    @TableField("datasource_info_host")
    @Schema(description = "主机地址", example = "127.0.0.1")
    private String datasourceHost;

    /**
     * 端口，空则按类型默认
     */
    @TableField("datasource_info_port")
    @Schema(description = "端口", example = "3306")
    private Integer datasourcePort;

    /**
     * 库名/命名空间；jdbc 可存完整 JDBC URL
     */
    @TableField("datasource_info_database")
    @Schema(description = "库名", example = "demo")
    private String datasourceDatabase;

    /**
     * 用户名
     */
    @TableField("datasource_info_username")
    @Schema(description = "用户名", example = "root")
    private String datasourceUsername;

    /**
     * 口令
     */
    @TableField("datasource_info_password")
    @Schema(description = "口令", example = "******")
    private String datasourcePassword;

    /**
     * 备注
     */
    @TableField("datasource_info_remark")
    @Schema(description = "备注", example = "只读账号")
    private String datasourceRemark;

    /**
     * 状态，0 停用、1 启用。
     *
     * <p>停用的数据源不允许被采集流程、SQL 节点引用，避免流程跑到一半才发现目标库不可用。</p>
     */
    @TableField("datasource_info_status")
    @Schema(description = "状态 0停用 1启用", example = "1")
    private Integer datasourceStatus;

    /**
     * JDBC 驱动类名。
     *
     * <p>非 MySQL 系的库建议显式指定；为空时由连接层按 URL 推断。</p>
     */
    @TableField("datasource_info_driver_class_name")
    @Schema(description = "JDBC 驱动类名", example = "org.postgresql.Driver")
    private String datasourceDriverClassName;

    /**
     * 引用的驱动 jar（{@code datasource_jdbc_driver_jar.id}）；为空则用应用 classpath 上的内置驱动。
     *
     * <p>本机不可能备齐所有数据库的驱动 jar：ojdbc、mssql-jdbc 与各类国产库驱动
     * 体积都在数 MB、许可各异，全打进产物等于让每个部署都背上几十 MB 用不上的东西；
     * 而让用户「把 jar 丢进某个目录」又不可控（版本、命名、驱动类名都得自己对）。
     * 所以做成「上传 → 落盘 → 按该 jar 建独立类加载器」的窄口子。</p>
     */
    @TableField("datasource_info_driver_jar_id")
    @Schema(description = "引用的驱动 jar ID，空则用内置驱动")
    private Long datasourceDriverJarId;

    /**
     * 连接延迟（毫秒）。
     *
     * <p>建立连接耗时：TCP 建链 + 协议握手。由「测试连接」回写，覆盖写、不留历史。
     * 为空表示从未测试过或测试未通过。</p>
     */
    @TableField("datasource_info_connect_latency_ms")
    @Schema(description = "连接延迟(毫秒)", example = "12")
    private Long datasourceConnectLatencyMs;

    /**
     * 会话延迟（毫秒）。
     *
     * <p>已建立连接上的一次往返耗时：jdbc 跑 {@code SELECT 1}、redis 跑 {@code PING}，
     * 其余类型做一次 TCP 探活。由「测试连接」回写。</p>
     */
    @TableField("datasource_info_session_latency_ms")
    @Schema(description = "会话延迟(毫秒)", example = "2")
    private Long datasourceSessionLatencyMs;

    /**
     * 最近一次连通性测试时间
     */
    @TableField("datasource_info_last_test_time")
    @Schema(description = "最近一次测试时间", example = "2026-09-27T10:00:00")
    private LocalDateTime datasourceLastTestTime;

    /**
     * 最近一次测试是否连通，0 失败、1 成功
     */
    @TableField("datasource_info_last_test_ok")
    @Schema(description = "最近一次测试是否连通 0失败 1成功", example = "1")
    private Integer datasourceLastTestOk;

    /**
     * 进程控制方式：{@code systemd} / {@code local}；为 null 表示自动探测。
     *
     * <p>仅 nginx 类型数据源使用。{@code systemd} 表示 start/stop/restart 交给
     * {@code systemctl}，{@code local} 表示直接用 {@code nginx -s} 与裸启动。
     * 为 null 时由后端自动判断，与该字段引入前的行为一致。</p>
     */
    @TableField("datasource_info_control_mode")
    @Schema(description = "进程控制方式 systemd/local，空为自动探测", example = "systemd")
    private String datasourceControlMode;

    /**
     * 控制方式为 {@code systemd} 时的托管单元名，如 {@code nginx.service}
     */
    @TableField("datasource_info_control_target")
    @Schema(description = "托管单元名", example = "nginx.service")
    private String datasourceControlTarget;

    /**
     * 置顶标记：1 置顶、0 普通。列表按「置顶在前、ID 升序」排序。
     *
     * <p>置顶是台账上的用户偏好，不参与连通性判定，也不随测试回写；
     * 新增数据源默认不置顶（{@code 0}）。</p>
     */
    @TableField("datasource_info_top_flag")
    @Schema(description = "置顶标记 1置顶 0普通", example = "1")
    private Integer datasourceTopFlag;
}
