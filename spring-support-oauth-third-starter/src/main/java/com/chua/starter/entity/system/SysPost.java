package com.chua.starter.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 岗位表
 *
 * @since 4.0.0.50
 * @author CH
 */
@Schema(description = "岗位表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_post")
public class SysPost extends SysBase implements Serializable {
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 岗位标识
     */
    @TableId(value = "sys_post_id", type = IdType.AUTO)
    @Schema(description = "岗位ID")
    @NotNull(message = "岗位ID不能为空", groups = {UpdateGroup.class})
    /**
     * syspostid
    */
    private Long sysPostId;

    /**
     * 岗位名称
     */
    @TableField(value = "sys_post_name")
    @Schema(description = "岗位名称")
    @Size(max = 100, message = "岗位名称最大长度需小于100")
    @NotEmpty(message = "岗位名称不能为空", groups = {AddGroup.class})
    /**
     * syspost名称
    */
    private String sysPostName;

    /**
     * 岗位编码
     */
    @TableField(value = "sys_post_code")
    @Schema(description = "岗位编码")
    @Size(max = 100, message = "岗位编码最大长度需小于100")
    @NotEmpty(message = "岗位编码不能为空", groups = {AddGroup.class})
    /**
     * syspost代码
    */
    private String sysPostCode;

    /**
     * 排序
     */
    @TableField(value = "sys_post_sort")
    @Schema(description = "排序")
    /**
     * syspostsort
    */
    private Integer sysPostSort;

    /**
     * 状态: 0-启用 1-禁用
     */
    @TableField(value = "sys_post_status")
    @Schema(description = "状态: 0-启用 1-禁用")
    /**
     * syspost状态
    */
    private Integer sysPostStatus;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @Schema(description = "备注")
    @Size(max = 500, message = "备注最大长度需小于500")
    /**
     * syspost备注
    */
    private String remark;

    /**
     * 关联用户数(非数据库字段, 分页列表展示用)
     */
    @TableField(exist = false)
    @Schema(description = "关联用户数")
    /**
     * syspost用户数量
    */
    private Integer userCount;
}
