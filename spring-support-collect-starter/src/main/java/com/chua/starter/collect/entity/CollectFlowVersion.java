package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程版本历史。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("collect_flow_version")
public class CollectFlowVersion {

    @TableId(value = "collect_flow_version_id", type = IdType.AUTO)
    private Integer collectFlowVersionId;

    private Integer collectFlowId;

    /** 版本号，从 1 开始递增 */
    private Integer versionNo;

    /** DSL 内容快照，JSON */
    private String flowDsl;

    /** 版本描述（用户填写或自动生成） */
    private String versionRemark;

    private String createName;
    private LocalDateTime createTime;
}
