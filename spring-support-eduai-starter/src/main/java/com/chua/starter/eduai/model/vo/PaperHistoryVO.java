package com.chua.starter.eduai.model.vo;

import java.time.LocalDateTime;

/**
 * 试卷历史VO
 *
 * @param historyId 历史标识
 * @param paperId 试卷标识
 * @param paperNo 试卷唯一编号
 * @param version 版本号
 * @param action 动作: CREATE/UPDATE/ADD_QUESTION/REMOVE_QUESTION/REORDER/PUBLISH/EVEN
 * @param hash 快照 SHA-256 摘要
 * @param hashValid 快照摘要校验结果（仅详情接口计算；列表接口为 null）
 * @param createTime 留痕时间
 * @param createBy 操作人
 * @param snapshot 试卷快照（仅详情接口返回；列表接口为 null）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperHistoryVO(
        Long historyId,
        Long paperId,
        String paperNo,
        Integer version,
        String action,
        String hash,
        Boolean hashValid,
        LocalDateTime createTime,
        String createBy,
        String snapshot
) {
}
