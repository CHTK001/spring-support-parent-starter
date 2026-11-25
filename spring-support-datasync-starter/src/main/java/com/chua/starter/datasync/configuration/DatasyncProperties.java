package com.chua.starter.datasync.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据同步 配置属性。
 *
 * <p>配置前缀为 {@code plugin.datasync}。完整 YAML 配置示例（所有键均可选，
 * 未配置的键取各字段注释中标注的默认值）：</p>
 *
 * <pre>{@code
 * plugin:
 *   datasync:
 *     # 模块总开关（同时是自动装配开关），默认 true
 *     enabled: true
 *     # 映射来源：file（文件目录）| database（数据库表），默认 file。
 *     # 注意：装配文件扫描器的条件要求此处显式写 file（条件注解无 matchIfMissing）
 *     mapping-source: file
 *     # 文件模式配置
 *     file-mapping:
 *       # 映射配置文件扫描目录（递归遍历），默认 ./datasync/mappings
 *       directory-path: ./datasync/mappings
 *     # 数据库模式配置
 *     database-mapping:
 *       # 是否启用数据库映射源（装配 DatabaseMappingLoader），默认 false
 *       enabled: false
 *       # 映射表名前缀，实际读取 {前缀}mapping 与 {前缀}field_mapping，默认 datasync_
 *       table-prefix: datasync_
 *       # 声明字段，当前实现未消费（加载器只在启动时一次性读表，不轮询），默认 0/5 * * * * ?
 *       cron: "0/5 * * * * ?"
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.datasync")
public class DatasyncProperties {

    /**
     * 是否启用 数据同步。
     *
     * <p>含义：模块总开关，同时是自动装配开关（条件注解 {@code matchIfMissing = true}，
     * 不配置即启用；显式设为 {@code false} 时整个自动配置不生效）。</p>
     * <p>默认值：{@code true}（字段初始化值；本字段为基本类型 boolean，无空值回落语义）。</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 映射来源：文件 | database。
     *
     * <p>含义：映射配置来源。{@code file} 表示从文件目录扫描加载；{@code database} 表示从数据库表读取。
     * 该值只决定是否装配文件扫描器 {@code DirectoryDataSyncMappingScanner}（条件判定要求显式写
     * {@code file}，无 {@code matchIfMissing}，POJO 默认值不参与条件判定），不限制数据库加载器。</p>
     * <p>默认值：{@code file}（字段初始化值；仅当配置了其它非空字符串时才会覆盖，
     * 不会自动回落到其它取值）。</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     mapping-source: file
     * }</pre>
     */
    private String mappingSource = "file";

    /**
     * 文件模式配置。
     *
     * <p>含义：文件模式（{@code mapping-source=file}）的子配置，见 {@link FileMapping}。</p>
     * <p>默认值：{@code new FileMapping()}（其中 directoryPath = ./datasync/mappings）。</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     file-mapping:
     *       directory-path: ./datasync/mappings
     * }</pre>
     */
    private FileMapping fileMapping = new FileMapping();

    /**
     * 数据库模式配置。
     *
     * <p>含义：数据库模式（{@code database-mapping.enabled=true}）的子配置，见 {@link DatabaseMapping}。</p>
     * <p>默认值：{@code new DatabaseMapping()}（其中 enabled = false、tablePrefix = datasync_、
     * cron = 0/5 * * * * ?）。</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     database-mapping:
     *       enabled: false
     *       table-prefix: datasync_
     *       cron: "0/5 * * * * ?"
     * }</pre>
     */
    private DatabaseMapping databaseMapping = new DatabaseMapping();

    @Data
    /**
     * 文件mapping类。
     *
     * <p>文件模式（{@code plugin.datasync.mapping-source=file}）的子配置。
     * 完整 YAML 配置示例（挂在父前缀 {@code plugin.datasync} 下）：</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     file-mapping:
     *       # 映射配置文件目录路径（递归扫描 .yaml/.yml、.json、.properties），默认 ./datasync/mappings
     *       directory-path: ./datasync/mappings
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class FileMapping {
        /**
         * 映射配置文件目录路径。
         *
         * <p>含义：文件模式下递归扫描的映射配置目录，支持 {@code .yaml/.yml}、{@code .json}、
         * {@code .properties} 三种格式；目录不存在时扫描器只记 warn 并跳过扫描，不抛异常。</p>
         * <p>默认值：{@code ./datasync/mappings}（字段初始化值；配置为空字符串时按空路径处理、
         * 扫描器同样跳过，不会回落到默认目录，请显式配置有效路径）。</p>
         *
         * <pre>{@code
         * plugin:
         *   datasync:
         *     file-mapping:
         *       directory-path: ./datasync/mappings
         * }</pre>
         */
        private String directoryPath = "./datasync/mappings";
    }

    @Data
    /**
     * databasemapping类。
     *
     * <p>数据库模式（{@code plugin.datasync.database-mapping.enabled=true}）的子配置。
     * 完整 YAML 配置示例（挂在父前缀 {@code plugin.datasync} 下）：</p>
     *
     * <pre>{@code
     * plugin:
     *   datasync:
     *     database-mapping:
     *       # 是否启用数据库映射源，默认 false
     *       enabled: false
     *       # 映射表名前缀，实际读取 {前缀}mapping 与 {前缀}field_mapping，默认 datasync_
     *       table-prefix: datasync_
     *       # 声明字段，当前实现未消费（启动时一次性读表，不轮询），默认 0/5 * * * * ?
     *       cron: "0/5 * * * * ?"
     * }</pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class DatabaseMapping {
        /**
         * 是否启用数据库映射源。
         *
         * <p>含义：为 {@code true} 时装配 {@code DatabaseMappingLoader}，在启动期
         * （{@code InitializingBean} 回调）一次性从 {@code {table-prefix}mapping} /
         * {@code {table-prefix}field_mapping} 读取映射；主表加载失败只记 error 不阻断启动。</p>
         * <p>默认值：{@code false}（字段初始化值；基本类型 boolean，无空值回落语义）。</p>
         *
         * <pre>{@code
         * plugin:
         *   datasync:
         *     database-mapping:
         *       enabled: false
         * }</pre>
         */
        private boolean enabled = false;

        /**
         * 表名前缀。
         *
         * <p>含义：映射主表与字段映射表的表名前缀，实际读取 {@code {前缀}mapping} 与
         * {@code {前缀}field_mapping}；前缀会经 {@code SqlName#checkSimple(String, String)}
         * 校验（仅允许简单标识符字符）。</p>
         * <p>默认值：{@code datasync_}（字段初始化值；配置为空时校验抛
         * {@code IllegalArgumentException: 映射表前缀不能为空}，导致映射加载失败，
         * 不存在空值回落，请保持非空合法标识符）。</p>
         *
         * <pre>{@code
         * plugin:
         *   datasync:
         *     database-mapping:
         *       table-prefix: datasync_
         * }</pre>
         */
        private String tablePrefix = "datasync_";

        /**
         * 数据库轮询间隔（Cron 表达式）。
         *
         * <p>含义：声明字段，当前实现未消费——{@code DatabaseMappingLoader} 只在启动时
         * 一次性读表，不按该表达式轮询；修改表数据需重启（或自行触发加载）。</p>
         * <p>默认值：{@code 0/5 * * * * ?}（字段初始化值；运行期无消费方，配置为空也不会引发回落或报错）。</p>
         *
         * <pre>{@code
         * plugin:
         *   datasync:
         *     database-mapping:
         *       cron: "0/5 * * * * ?"
         * }</pre>
         */
        private String cron = "0/5 * * * * ?";
    }
}
