package com.chua.starter.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_project_default")
public class SysProjectDefault {

    @TableId(value = "sys_project_default_id", type = IdType.AUTO)
    private Integer sysProjectDefaultId;
    private String sysProjectDefaultType;
    private Integer sysProjectId;
}
