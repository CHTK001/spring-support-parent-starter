package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 趣味模块配置实体
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_fun_config")
public class EduFunConfig extends SysBase {

    /**
     * 配置标识
     */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /**
     * 模块编码
     */
    private String moduleCode;

    /**
     * 适用学段：PRIMARY / JUNIOR / SENIOR；null 表示各学段通用
     *
     * <p>与学生端配合使用：学生端首页按自己的学段取这里配置的模块清单，
     * 因此同一个 moduleCode 可以在三个学段各有一行（唯一键是 code + 学段）。</p>
     */
    private String funConfigGradeLevel;

    /**
     * 学生端卡片副标题
     */
    private String funConfigSubtitle;

    /**
     * 学生端卡片图标渐变起始色
     */
    private String funConfigColor;

    /**
     * 学生端卡片图标渐变结束色
     */
    private String funConfigColorTo;

    /**
     * 是否显示未完成角标：0-否 1-是（小学端用）
     */
    private Integer funConfigShowBadge;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块描述
     */
    private String moduleDesc;

    /**
     * 模块图标
     */
    private String moduleIcon;

    /**
     * 模块路径（前端路由或iframe URL）
     */
    private String modulePath;

    /**
     * 是否启用（1=启用, 0=禁用）
     */
    private Integer enabled;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 配置JSON（扩展配置）
     */
    private String configJson;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("fun_config_owner_user_id")
    private String ownerUserId;
}
