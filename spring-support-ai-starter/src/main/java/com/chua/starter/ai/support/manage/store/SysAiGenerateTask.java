package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 生成任务（文生图 / 文生视频）持久化实体。
 *
 * <p>任务主键即对外暴露的 {@code taskId}，与 provider 侧
 * {@code ImageClient#createTask} / {@code VideoClient#createTask} 返回的任务 ID 同值，
 * 因此轮询时无需再做 ID 映射。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_generate_task_} 作为前缀，实体字段保持简短语义名，
 * 通过 {@link TableField} 显式映射。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName(value = "sys_ai_generate_task", autoResultMap = true)
public class SysAiGenerateTask implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 任务状态：已创建
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 任务状态：执行中
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 任务状态：成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 任务状态：失败
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 任务状态：已取消
     */
    public static final String STATUS_CANCELED = "CANCELED";

    /**
     * 任务ID（对外暴露）
     */
    @TableId(value = "sys_ai_generate_task_id", type = IdType.INPUT)
    private String taskId; // 任务ID

    /**
     * 任务类型（image / video）
     */
    @TableField("sys_ai_generate_task_type")
    private String type; // 类型

    /**
     * 任务状态
     */
    @TableField("sys_ai_generate_task_status")
    private String status; // 状态

    /**
     * 进度百分比
     */
    @TableField("sys_ai_generate_task_progress")
    private Integer progress; // 进度

    /**
     * 正向提示词
     */
    @TableField("sys_ai_generate_task_prompt")
    private String prompt; // 提示词

    /**
     * 负面提示词
     */
    @TableField("sys_ai_generate_task_negative")
    private String negativePrompt; // 负面提示词

    /**
     * 供应商标识
     */
    @TableField("sys_ai_generate_task_provider_id")
    private String providerId; // 供应商标识

    /**
     * 模型名
     */
    @TableField("sys_ai_generate_task_model")
    private String model; // 模型

    /**
     * 分辨率
     */
    @TableField("sys_ai_generate_task_size")
    private String size; // 分辨率

    /**
     * 视频时长（秒）
     */
    @TableField("sys_ai_generate_task_duration")
    private Integer duration; // 时长

    /**
     * 请求张数
     */
    @TableField("sys_ai_generate_task_count")
    private Integer count; // 张数

    /**
     * 随机种子
     */
    @TableField("sys_ai_generate_task_seed")
    private Long seed; // 种子

    /**
     * 风格
     */
    @TableField("sys_ai_generate_task_style")
    private String style; // 风格

    /**
     * 质量档
     */
    @TableField("sys_ai_generate_task_quality")
    private String quality; // 质量

    /**
     * 推理步数
     */
    @TableField("sys_ai_generate_task_steps")
    private Integer steps; // 步数

    /**
     * 参考图（data URL / base64）
     */
    @TableField("sys_ai_generate_task_reference")
    private String referenceImage; // 参考图

    /**
     * 生成结果JSON（媒体URL列表等）
     */
    @TableField(value = "sys_ai_generate_task_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> result; // 结果

    /**
     * 失败原因
     */
    @TableField("sys_ai_generate_task_error")
    private String errorMessage; // 失败原因

    /**
     * 创建人ID
     */
    @TableField("sys_user_id")
    private String userId; // 创建人

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_generate_task_create_time")
    private Long createdAt; // 创建时间

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_generate_task_update_time")
    private Long updatedAt; // 更新时间

    /**
     * 生成耗时（毫秒）
     *
     * <p>从提交到拿到结果的实际耗时：同步实现由本服务计时；
     * 异步实现由轮询到终态时补记。仅用于展示，不参与调度。</p>
     */
    @TableField("sys_ai_generate_task_elapsed")
    private Long elapsedMillis; // 生成耗时（毫秒）

    /**
     * 取出结果中的媒体地址列表。
     *
     * @return 媒体地址列表，无结果时返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> resultUrls() {
        if (result == null) {
            return List.of();
        }
        Object urls = result.get("urls");
        if (urls instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }
}
