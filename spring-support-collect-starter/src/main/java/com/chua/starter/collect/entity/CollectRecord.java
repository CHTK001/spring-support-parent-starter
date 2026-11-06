package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集结果记录。
 *
 * <p>结果统一存 JSON payload，配合 collect_field 渲染表格列与导出；
 * 当 collectRecordKey 非空时按同键覆盖写入，实现增量采集去重。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_record")
@Schema(description = "数据采集-结果记录")
public class CollectRecord extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_record_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long collectRecordId;

    /**
     * 流程ID。
     */
    @Schema(description = "流程ID")
    private Integer collectRecordFlowId;

    /**
     * 产出该记录的任务ID。
     */
    @Schema(description = "产出任务ID")
    private Long collectRecordTaskId;

    /**
     * 产出节点ID。
     */
    @Schema(description = "产出节点ID")
    private String collectRecordNodeId;

    /**
     * 业务主键值，用于同键覆盖。
     */
    @Schema(description = "业务主键值")
    private String collectRecordKey;

    /**
     * 来源 URL。
     */
    @Schema(description = "来源URL")
    private String collectRecordUrl;

    /**
     * 记录内容（JSON）。
     */
    @Schema(description = "记录内容JSON")
    private String collectRecordPayload;
}
