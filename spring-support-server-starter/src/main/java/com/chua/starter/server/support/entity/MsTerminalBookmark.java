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
* 终端路径书签实体
* <p>
* 对应表 {@code ms_terminal_bookmark}，为指定主机收藏常用 SFTP 路径。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "路径书签")
@TableName("ms_terminal_bookmark")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsTerminalBookmark implements Serializable {

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
    @TableField("ms_terminal_bookmark_host_id")
    @Schema(description = "主机ID", example = "1")
    private Long msTerminalBookmarkHostId; // msterminalbookmark主机标识

    /**
    * 书签名称
     */
    @TableField("ms_terminal_bookmark_name")
    @Schema(description = "书签名称", example = "应用日志目录")
    private String msTerminalBookmarkName; // msterminalbookmark名称

    /**
    * 书签路径
     */
    @TableField("ms_terminal_bookmark_path")
    @Schema(description = "书签路径", example = "/var/log/app")
    private String msTerminalBookmarkPath; // msterminalbookmark路径

    /**
    * 排序号
     */
    @TableField("ms_terminal_bookmark_sort")
    @Schema(description = "排序号", example = "1")
    private Integer msTerminalBookmarkSort; // msterminalbookmark排序
}
