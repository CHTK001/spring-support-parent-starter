package com.chua.starter.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
* 数据权限测试实体：用户表（含框架默认数据权限列名 sys_dept_标识 / 创建_by）。
*
* @author CH
* @since 2026/09/04
 */
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id; // 标识
    private String name; // 名称
    private String sysDeptId; // sysdeptid
    private String createBy; // 创建by

    /**
    * 获取标识。
    * @return 获取id的结果
     */
    public Long getId() {
        return id;
    }

    /**
    * 设置标识。
    * @param id 标识
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
    * 获取名称。
    * @return 获取名称的结果
     */
    public String getName() {
        return name;
    }

    /**
    * 设置名称。
    * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
    * 获取sysdeptid。
    * @return 获取sysdeptid的结果
     */
    public String getSysDeptId() {
        return sysDeptId;
    }

    /**
    * 设置sysdeptid。
    * @param sysDeptId sysdeptid
     */
    public void setSysDeptId(String sysDeptId) {
        this.sysDeptId = sysDeptId;
    }

    /**
    * 获取创建by。
    * @return 获取创建by的结果
     */
    public String getCreateBy() {
        return createBy;
    }

    /**
    * 设置创建by。
    * @param createBy 创建by
     */
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
}
