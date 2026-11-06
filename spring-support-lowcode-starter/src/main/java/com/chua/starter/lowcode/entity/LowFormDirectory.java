package com.chua.starter.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 低代码-目录（树形结构，承载在文件管理器中的目录列表）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("low_form_directory")
@Schema(description = "低代码—目录")
public class LowFormDirectory extends SysBase {

    /**
     * 目录ID，自增。
     */
    @TableId(value = "low_form_directory_id", type = IdType.AUTO)
    @Schema(description = "目录ID")
    private Integer lowFormDirectoryId;

    /**
     * 目录名称。
     */
    @Schema(description = "目录名称")
    private String lowFormDirectoryName;

    /**
     * 父目录ID，顶级为 0。
     */
    @Schema(description = "父目录ID")
    private Integer lowFormDirectoryPid;

    /**
     * 目录路径链，根到当前节点的ID路径。
     */
    @Schema(description = "目录路径链")
    private String lowFormDirectoryTreeId;

    /**
     * 排序，越小越靠前。
     */
    @Schema(description = "排序")
    private Integer lowFormDirectorySort;

    /**
     * 备注。
     */
    @Schema(description = "备注")
    private String lowFormDirectoryRemark;
}
