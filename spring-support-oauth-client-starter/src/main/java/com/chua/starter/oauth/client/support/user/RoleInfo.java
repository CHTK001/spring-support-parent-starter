package com.chua.starter.oauth.client.support.user;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 角色信息
 *
 * @author CH
 */
@Accessors(chain = true)
@Data
public class RoleInfo {

    /**
     * 角色名称
     */
    private String roleName;
    /**
     * 角色id
     */
    private String roleId;

    /**
     * 角色编码
     */
    private String roleCode;
    /**
     * 角色描述
     */
    private String roleDesc;

    /**
     * 是否可读
     */
    private boolean readable;

    /**
     * 是否可写
     */
    private boolean writeable;

    /**
     * 是否可执行
     */
    private boolean executable;
}
