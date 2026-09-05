package com.chua.starter.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 数据权限测试实体：用户表（含框架默认数据权限列名 sys_dept_id / create_by）。
 *
 * @author CH
 * @since 2026/09/04
 */
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String sysDeptId;
    private String createBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSysDeptId() {
        return sysDeptId;
    }

    public void setSysDeptId(String sysDeptId) {
        this.sysDeptId = sysDeptId;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
}
