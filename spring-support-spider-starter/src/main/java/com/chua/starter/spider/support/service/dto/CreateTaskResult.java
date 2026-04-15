package com.chua.starter.spider.support.service.dto;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建任务结果 DTO。
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskResult {

    /** 新建任务 ID */
    private Long taskId;

    /** 默认编排定义（含 START/END 节点） */
    private SpiderFlowDefinition flow;
}
