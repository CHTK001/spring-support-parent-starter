package com.chua.starter.monitor.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 脚本管理实体
 * @author CH
 * @since 2024/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_sys_gen_script")
public class MonitorSysGenScript extends SysBase {

    /**
     * 脚本ID
     */
    @TableId(value = "monitor_sys_gen_script_id", type = IdType.AUTO)
    private Integer scriptId;

    /**
     * 脚本名称
     */
    @TableField("monitor_sys_gen_script_name")
    private String scriptName;

    /**
     * 脚本类型
     */
    @TableField("monitor_sys_gen_script_type")
    private String scriptType;

    /**
     * 脚本描述
     */
    @TableField("monitor_sys_gen_script_description")
    private String scriptDescription;

    /**
     * 脚本内容
     */
    @TableField("monitor_sys_gen_script_content")
    private String scriptContent;

    /**
     * 默认参数(JSON格式)
     */
    @TableField("monitor_sys_gen_script_parameters")
    private String scriptParameters;

    /**
     * 执行超时时间(秒)
     */
    @TableField("monitor_sys_gen_script_timeout")
    private Integer scriptTimeout;

    /**
     * 脚本状态
     */
    @TableField("monitor_sys_gen_script_status")
    private ScriptStatus scriptStatus;

    /**
     * 脚本标签
     */
    @TableField("monitor_sys_gen_script_tags")
    private String scriptTags;

    /**
     * 脚本分类
     */
    @TableField("monitor_sys_gen_script_category")
    private String scriptCategory;

    /**
     * 脚本版本
     */
    @TableField("monitor_sys_gen_script_version")
    private String scriptVersion;

    /**
     * 脚本作者
     */
    @TableField("monitor_sys_gen_script_author")
    private String scriptAuthor;

    /**
     * 最后执行时间
     */
    @TableField("monitor_sys_gen_script_last_execute_time")
    private LocalDateTime scriptLastExecuteTime;

    /**
     * 执行次数
     */
    @TableField("monitor_sys_gen_script_execute_count")
    private Integer scriptExecuteCount;

    /**
     * 脚本状态枚举
     */
    public enum ScriptStatus {
        DISABLED(0, "禁用"),
        ENABLED(1, "启用");

        private final Integer code;
        private final String desc;

        ScriptStatus(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public Integer getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static ScriptStatus fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (ScriptStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 脚本类型枚举
     */
    public enum ScriptType {
        SHELL("shell", "Shell脚本", ".sh"),
        BATCH("batch", "批处理脚本", ".bat"),
        POWERSHELL("powershell", "PowerShell脚本", ".ps1"),
        PYTHON("python", "Python脚本", ".py"),
        JAVASCRIPT("javascript", "JavaScript脚本", ".js"),
        GROOVY("groovy", "Groovy脚本", ".groovy"),
        LUA("lua", "Lua脚本", ".lua"),
        PERL("perl", "Perl脚本", ".pl"),
        RUBY("ruby", "Ruby脚本", ".rb"),
        PHP("php", "PHP脚本", ".php");

        private final String code;
        private final String desc;
        private final String extension;

        ScriptType(String code, String desc, String extension) {
            this.code = code;
            this.desc = desc;
            this.extension = extension;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public String getExtension() {
            return extension;
        }

        public static ScriptType fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ScriptType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 获取脚本类型枚举
     */
    public ScriptType getScriptTypeEnum() {
        return ScriptType.fromCode(scriptType);
    }

    /**
     * 获取脚本状态枚举
     */
    public ScriptStatus getScriptStatusEnum() {
        return ScriptStatus.fromCode(scriptStatus != null ? scriptStatus.getCode() : null);
    }

    /**
     * 检查脚本是否启用
     */
    public boolean isEnabled() {
        return ScriptStatus.ENABLED.equals(scriptStatus);
    }

    /**
     * 获取格式化的标签列表
     */
    public String[] getTagArray() {
        if (scriptTags == null || scriptTags.trim().isEmpty()) {
            return new String[0];
        }
        return scriptTags.split(",");
    }

    /**
     * 设置标签数组
     */
    public void setTagArray(String[] tags) {
        if (tags == null || tags.length == 0) {
            this.scriptTags = null;
        } else {
            this.scriptTags = String.join(",", tags);
        }
    }

    /**
     * 获取默认超时时间
     */
    public int getTimeoutOrDefault() {
        return scriptTimeout != null ? scriptTimeout : 300;
    }

    /**
     * 获取执行次数
     */
    public int getExecuteCountOrDefault() {
        return scriptExecuteCount != null ? scriptExecuteCount : 0;
    }

    /**
     * 增加执行次数
     */
    public void incrementExecuteCount() {
        this.scriptExecuteCount = getExecuteCountOrDefault() + 1;
        this.scriptLastExecuteTime = LocalDateTime.now();
    }
}
