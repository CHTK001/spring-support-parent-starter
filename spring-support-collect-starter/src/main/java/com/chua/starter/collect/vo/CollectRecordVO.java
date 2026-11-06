package com.chua.starter.collect.vo;

import com.chua.starter.collect.dto.CollectParamDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集结果记录出参。
 *
 * <p>payload JSON 在服务层解析成有序的 cells，接口不把 Map 当作业务数据外传。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "数据采集-结果记录出参")
public class CollectRecordVO {

    /**
     * 主键ID。
     */
    @Schema(description = "主键ID")
    private Long collectRecordId;

    /**
     * 流程ID。
     */
    @Schema(description = "流程ID")
    private Integer collectRecordFlowId;

    /**
     * 产出任务ID。
     */
    @Schema(description = "产出任务ID")
    private Long collectRecordTaskId;

    /**
     * 产出节点ID。
     */
    @Schema(description = "产出节点ID")
    private String collectRecordNodeId;

    /**
     * 业务主键值。
     */
    @Schema(description = "业务主键值")
    private String collectRecordKey;

    /**
     * 来源 URL。
     */
    @Schema(description = "来源URL")
    private String collectRecordUrl;

    /**
     * 字段值列表。
     */
    @Schema(description = "字段值列表")
    private List<CollectParamDTO> cells = new ArrayList<>();

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
