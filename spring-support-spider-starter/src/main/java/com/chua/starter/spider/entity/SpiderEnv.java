package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 环境变量实体。
 *
 * <p>对应 {@code spider_env} 表，供爬虫在运行期通过 {@code {{env.KEY}}}
 * 语法在 URL / 请求体 / 提取表达式等位置插值使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_env")
@Schema(name = "环境变量")
public class SpiderEnv extends SysBase {

    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID")
    /** ID */
    private Long id;

    /**
     * 变量名（如 COOKIE、TOKEN）
     */
    @TableField("env_key")
    @Schema(description = "变量名")
    /** ENV密钥 */
    private String envKey;

    /**
     * 变量值
     */
    @TableField("env_value")
    @Schema(description = "变量值")
    /** ENV值 */
    private String envValue;

    /**
     * 描述
     */
    @TableField("description")
    @Schema(description = "描述")
    /** 描述 */
    private String description;

    /**
     * 是否启用（1 启用 / 0 停用）
     */
    @TableField("enabled")
    @Schema(description = "是否启用")
    /** 是否启用 */
    private Integer enabled;

    /**
     * 逻辑删除标记（0 未删除 / 1 已删除）
     */
    @TableField("sys_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** SYS删除标记 */
    private Integer sysDeleted;
}