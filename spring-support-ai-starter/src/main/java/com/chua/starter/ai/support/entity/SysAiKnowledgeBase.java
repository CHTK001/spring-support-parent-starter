package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识库元数据，对应表 {@code sys_ai_knowledge_base}。
 *
 * <p>本表只承载「库本身」的元数据（名称、根目录、托管标记、采集来源配置），
 * 知识点文件内容仍由 {@code <uploadDir>/kb-<knowledge_base_id>} 目录承载，
 * 不进数据库。</p>
 *
 * <p>为什么必须有这张表：本项目原先把知识库列表放在
 * {@code ApiKnowledgeController} 的纯内存 {@code ConcurrentHashMap} 里，
 * 后端每次重启库列表连同文件树一起消失，属于数据丢失级问题。
 * 该表是「写穿缓存」的持久化侧——启动时全量载入内存，之后每次写操作同步落库。</p>
 *
 * <p>为什么不给 {@code sys_ai_knowledge_library} 加列：init SQL 的 ONCE 语义是
 * 「仅当库为空时执行」，而 MySQL 的 {@code ADD COLUMN} 不支持 {@code IF NOT EXISTS}，
 * 线上非空库永远加不上列；且两者本就不是同一层实体（前者是图谱/模型库目录，
 * 后者是带 {@code root_path} 的 RAG 知识库）。</p>
 *
 * <p>所有业务列都显式写 {@code @TableField}：本仓表列统一带模块前缀，
 * 驼峰自动转下划线得到的名字与真实列名对不上，漏写会在运行期才报列不存在。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_knowledge_base")
public class SysAiKnowledgeBase {

    /**
     * 主键（UUID，对应内存记录的 {@code knowledge_base_id}）
     */
    @TableId(value = "sys_ai_knowledge_base_id", type = IdType.INPUT)
    private String id;

    /**
     * 知识库名称
     */
    @TableField("sys_ai_knowledge_base_name")
    private String name;

    /**
     * 知识库描述
     */
    @TableField("sys_ai_knowledge_base_description")
    private String description;

    /**
     * 根目录；托管库为 {@code <uploadDir>/kb-<id>}，外部库为用户指定目录
     */
    @TableField("sys_ai_knowledge_base_root_path")
    private String rootPath;

    /**
     * 类型：local 外部本地目录 / web 网址采集
     */
    @TableField("sys_ai_knowledge_base_kind")
    private String kind;

    /**
     * 目录是否由本服务托管（1 托管，0 外部目录）
     */
    @TableField("sys_ai_knowledge_base_managed")
    private Integer managed;

    /**
     * 是否公开可读（1 公开，0 仅归属人与管理员）
     */
    @TableField("sys_ai_knowledge_base_is_public")
    private Integer isPublic;

    /**
     * 标签，JSON 数组文本（如 {@code ["三国","验收"]}）
     */
    @TableField("sys_ai_knowledge_base_tags")
    private String tags;

    /**
     * 采集来源配置（JSON，{@code {mode, entries[]}}）；kind=web 时非空
     */
    @TableField("sys_ai_knowledge_base_source")
    private String source;

    /**
     * 文件数（缓存值，读取时按需重算）
     */
    @TableField("sys_ai_knowledge_base_file_count")
    private Long fileCount;

    /**
     * 目录总字节数（缓存值，读取时按需重算）
     */
    @TableField("sys_ai_knowledge_base_total_size")
    private Long totalSize;

    /**
     * 分片最大长度（字符）；入库时按它切分知识点
     */
    @TableField("sys_ai_knowledge_base_chunk_max_size")
    private Integer chunkMaxSize;

    /**
     * 分片重叠长度（字符）；必须小于分片最大长度
     */
    @TableField("sys_ai_knowledge_base_chunk_overlap_size")
    private Integer chunkOverlapSize;

    /**
     * 分片最小长度（字符）
     */
    @TableField("sys_ai_knowledge_base_chunk_min_size")
    private Integer chunkMinSize;

    /**
     * 归属用户ID（写入者，非空表示仅本人与管理员可见可写）
     */
    @TableField("sys_ai_knowledge_base_user_id")
    private String userId;

    /**
     * 创建人
     */
    @TableField("create_name")
    private String createName;

    /**
     * 创建人ID
     */
    @TableField("create_by")
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField("update_name")
    private String updateName;

    /**
     * 更新人ID
     */
    @TableField("update_by")
    private Integer updateBy;
}
