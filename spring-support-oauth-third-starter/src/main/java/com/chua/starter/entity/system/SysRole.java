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
 * 角色表
 *
 * @since 2024/8/7
 * @author CH
 */
@Schema(description = "角色表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_role")
public class SysRole extends SysBase implements Serializable {
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 角色标识
     */
    @TableId(value = "sys_role_id", type = IdType.AUTO)
    @Schema(description = "角色ID")
    @NotNull(message = "角色ID不能为空", groups = {UpdateGroup.class})
    /**
     * sysroleid
    */
    private Integer sysRoleId;

    /**
     * 角色名称
     */
    @TableField(value = "sys_role_name")
    @Schema(description = "角色名称")
    @Size(max = 255, message = "角色名称最大长度需小于255")
    /**
     * sysrole名称
    */
    private String sysRoleName;

    /**
     * 角色编码，唯一
     */
    @TableField(value = "sys_role_code")
    @Schema(description = "角色编码，唯一")
    @Size(max = 255, message = "角色编码最大长度需小于255")
    @NotEmpty(message = "角色编码不能为空", groups = {AddGroup.class})
    /**
     * sysrole代码
    */
    private String sysRoleCode;

    /**
     * 是否启用; 0:不启用
     */
    @TableField(value = "sys_role_status")
    @Schema(description = "是否启用; 0:不启用")
    /**
     * sysrole状态
    */
    private Integer sysRoleStatus;

    /**
     * 优先级
     */
    @TableField(value = "sys_role_sort")
    @Schema(description = "优先级")
    /**
     * sysrolesort
    */
    private Integer sysRoleSort;

    /**
     * 是否系统角色; 0:非系统角色
     */
    @TableField(value = "sys_role_in_system")
    @Schema(description = "是否系统角色; 0:非系统角色")
    /**
     * sysroleinsystem
    */
    private Integer sysRoleInSystem;

    /**
     * 备注
     */
    @TableField(value = "sys_role_remark")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度需小于255")
    /**
     * sysroleremark
    */
    private String sysRoleRemark;

    /**
     * 数据权限范围: 1-全部数据 2-本部门数据 3-本部门及以下 4-仅本人数据 5-自定义部门数据
     */
    @TableField(value = "sys_role_data_scope")
    @Schema(description = "数据权限范围: 1-全部数据 2-本部门数据 3-本部门及以下 4-仅本人数据 5-自定义部门数据")
    /**
     * sysroledatascope
    */
    private Integer sysRoleDataScope = 1;

    /**
     * 自定义数据权限部门ID列表(逗号分隔), data_scope=5时生效
     */
    @TableField(value = "sys_role_data_scope_dept_ids")
    @Schema(description = "自定义数据权限部门ID列表(逗号分隔)")
    /**
     * sysroledatascopedeptids
    */
    private String sysRoleDataScopeDeptIds;

    /**
     * 角色是否可读
     */
    @TableField(value = "sys_role_readable")
    @Schema(description = "角色是否可读")
    /**
     * sysrolereadable
    */
    private Integer sysRoleReadable = 0x0000_0001;

    /**
     * 角色是否可写
     */
    @TableField(value = "sys_role_writeable")
    @Schema(description = "角色是否可写")
    /**
     * sysrolewriteable
    */
    private Integer sysRoleWriteable;

    /**
     * 角色是否可执行
     */
    @TableField(value = "sys_role_executable")
    @Schema(description = "角色是否可执行")
    /**
     * sysroleexecutable
    */
    private Integer sysRoleExecutable;

    /**
     * 角色看板类型
     */
    @TableField(value = "sys_role_board_card")
    @Schema(description = "角色看板类型")
    /**
     * sysroleboardcard
    */
    private Integer sysRoleBoardCard;

    /**
     * 是否只读角色: 0-否 1-是
     */
    @TableField(value = "sys_role_readonly")
    @Schema(description = "是否只读角色: 0-否 1-是")
    /**
     * sysrolereadonly
    */
    private Integer sysRoleReadonly = 0;

    /**
     * 用户标识
     */
    @Schema(description = "用户ID")
    @TableField(exist = false)
    /**
     * SYS用户标识
    */
    private Integer sysUserId;
}
