package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 终端命令片段实体
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "命令片段")
@TableName("ms_terminal_snippet")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsTerminalSnippet extends SysBase {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 片段名称
     */
    @TableField("ms_terminal_snippet_name")
    @Schema(description = "片段名称", example = "查看磁盘占用")
    private String msTerminalSnippetName; // msterminalsnippet名称

    /**
     * 片段内容
     */
    @TableField("ms_terminal_snippet_content")
    @Schema(description = "片段内容", example = "df -h")
    private String msTerminalSnippetContent; // msterminalsnippet内容

    /**
     * 备注
     */
    @TableField("ms_terminal_snippet_remark")
    @Schema(description = "备注")
    private String msTerminalSnippetRemark; // msterminalsnippet评论
}
