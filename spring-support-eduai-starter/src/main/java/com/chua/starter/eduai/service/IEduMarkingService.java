package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;

/**
 * 阅卷服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduMarkingService {

    /**
     * 提交答题卡，启动阅卷
     *
     * @param dto 答题卡数据
     * @return 任务ID
     */
    String submitAnswerSheet(MarkingSubmitDTO dto);

    /**
     * 轮询阅卷结果
     *
     * @param taskId 任务标识
     * @return 阅卷结果
     */
    MarkingResultVO getResult(String taskId);

    /**
     * 教师确认或修正评分
     *
     * @param dto 修正数据
     * @return 是否成功
     */
    boolean confirmMarking(MarkingConfirmDTO dto);
}