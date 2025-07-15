package com.chua.starter.monitor.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本分类实体
 * @author CH
 * @since 2024/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_sys_gen_script_category")
public class MonitorSysGenScriptCategory extends SysBase {

    /**
     * 分类ID
     */
    @TableId(value = "monitor_sys_gen_script_category_id", type = IdType.AUTO)
    private Integer categoryId;

    /**
     * 分类名称
     */
    @TableField("monitor_sys_gen_script_category_name")
    private String categoryName;

    /**
     * 分类描述
     */
    @TableField("monitor_sys_gen_script_category_description")
    private String categoryDescription;

    /**
     * 父分类ID
     */
    @TableField("monitor_sys_gen_script_category_parent_id")
    private Integer categoryParentId;

    /**
     * 排序
     */
    @TableField("monitor_sys_gen_script_category_sort")
    private Integer categorySort;

    /**
     * 状态
     */
    @TableField("monitor_sys_gen_script_category_status")
    private CategoryStatus categoryStatus;

    /**
     * 分类状态枚举
     */
    public enum CategoryStatus {
        DISABLED(0, "禁用"),
        ENABLED(1, "启用");

        private final Integer code;
        private final String desc;

        CategoryStatus(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public Integer getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static CategoryStatus fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (CategoryStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 检查分类是否启用
     */
    public boolean isEnabled() {
        return CategoryStatus.ENABLED.equals(categoryStatus);
    }

    /**
     * 检查是否为根分类
     */
    public boolean isRootCategory() {
        return categoryParentId == null || categoryParentId == 0;
    }

    /**
     * 获取排序值
     */
    public int getSortOrDefault() {
        return categorySort != null ? categorySort : 0;
    }
}
