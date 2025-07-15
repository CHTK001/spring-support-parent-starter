package com.chua.starter.monitor.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 脚本管理DTO
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptManagementDTO {

    /**
     * 脚本ID
     */
    private Integer scriptId;

    /**
     * 脚本名称
     */
    @NotBlank(message = "脚本名称不能为空")
    @Size(max = 255, message = "脚本名称长度不能超过255个字符")
    private String scriptName;

    /**
     * 脚本类型
     */
    @NotBlank(message = "脚本类型不能为空")
    private String scriptType;

    /**
     * 脚本描述
     */
    @Size(max = 1000, message = "脚本描述长度不能超过1000个字符")
    private String scriptDescription;

    /**
     * 脚本内容
     */
    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;

    /**
     * 默认参数
     */
    private String scriptParameters;

    /**
     * 执行超时时间(秒)
     */
    private Integer scriptTimeout;

    /**
     * 脚本状态
     */
    private Integer scriptStatus;

    /**
     * 脚本标签
     */
    private String[] scriptTags;

    /**
     * 脚本分类
     */
    private String scriptCategory;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 脚本作者
     */
    private String scriptAuthor;

    /**
     * 最后执行时间
     */
    private LocalDateTime scriptLastExecuteTime;

    /**
     * 执行次数
     */
    private Integer scriptExecuteCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 脚本执行请求DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptExecuteRequestDTO {
        
        /**
         * 脚本ID
         */
        @NotNull(message = "脚本ID不能为空")
        private Integer scriptId;

        /**
         * 执行参数
         */
        private String[] parameters;

        /**
         * 工作目录
         */
        private String workingDirectory;

        /**
         * 超时时间(秒)
         */
        private Integer timeout;

        /**
         * 环境变量
         */
        private java.util.Map<String, String> environment;

        /**
         * 是否异步执行
         */
        private Boolean async;

        /**
         * 执行服务器ID
         */
        private Integer serverId;

        /**
         * 触发类型
         */
        private String triggerType;
    }

    /**
     * 脚本执行响应DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptExecuteResponseDTO {
        
        /**
         * 执行记录ID
         */
        private Long executionId;

        /**
         * 脚本ID
         */
        private Integer scriptId;

        /**
         * 脚本名称
         */
        private String scriptName;

        /**
         * 执行状态
         */
        private String executionStatus;

        /**
         * 开始时间
         */
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        private LocalDateTime endTime;

        /**
         * 执行耗时(毫秒)
         */
        private Long duration;

        /**
         * 退出码
         */
        private Integer exitCode;

        /**
         * 标准输出
         */
        private String stdout;

        /**
         * 错误输出
         */
        private String stderr;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 进程ID
         */
        private Long processId;

        /**
         * 执行服务器ID
         */
        private Integer serverId;

        /**
         * 触发类型
         */
        private String triggerType;

        /**
         * 执行人
         */
        private String executeBy;
    }

    /**
     * 脚本查询条件DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptQueryDTO {
        
        /**
         * 脚本名称(模糊查询)
         */
        private String scriptName;

        /**
         * 脚本类型
         */
        private String scriptType;

        /**
         * 脚本分类
         */
        private String scriptCategory;

        /**
         * 脚本状态
         */
        private Integer scriptStatus;

        /**
         * 脚本标签
         */
        private String scriptTag;

        /**
         * 脚本作者
         */
        private String scriptAuthor;

        /**
         * 创建时间开始
         */
        private LocalDateTime createTimeStart;

        /**
         * 创建时间结束
         */
        private LocalDateTime createTimeEnd;

        /**
         * 页码
         */
        private Integer pageNum;

        /**
         * 页大小
         */
        private Integer pageSize;

        /**
         * 排序字段
         */
        private String sortBy;

        /**
         * 排序方向
         */
        private String sortOrder;
    }

    /**
     * 脚本统计DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptStatisticsDTO {
        
        /**
         * 总脚本数
         */
        private Long totalCount;

        /**
         * 启用脚本数
         */
        private Long enabledCount;

        /**
         * 禁用脚本数
         */
        private Long disabledCount;

        /**
         * 总执行次数
         */
        private Long totalExecutions;

        /**
         * 各类型脚本统计
         */
        private List<ScriptTypeStatistics> typeStatistics;

        /**
         * 各分类脚本统计
         */
        private List<ScriptCategoryStatistics> categoryStatistics;

        /**
         * 执行统计
         */
        private ExecutionStatistics executionStatistics;
    }

    /**
     * 脚本类型统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptTypeStatistics {
        private String scriptType;
        private String scriptTypeDesc;
        private Long count;
    }

    /**
     * 脚本分类统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptCategoryStatistics {
        private String category;
        private Long count;
    }

    /**
     * 执行统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStatistics {
        private Long totalExecutions;
        private Long successCount;
        private Long failedCount;
        private Long timeoutCount;
        private Long runningCount;
        private Double avgDuration;
        private Double successRate;
    }
}
