package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduMarkingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅卷任务 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduMarkingTaskMapper extends BaseMapper<EduMarkingTask> {

    /**
     * 按业务标识取任务。
     *
     * @param taskId 任务业务标识
     * @return 任务实体，不存在返回 null
     */
    EduMarkingTask selectByTaskId(@Param("taskId") String taskId);

    /**
     * 取待处理/处理中的任务（重启续跑用）。
     *
     * <p>按创建时间升序返回，保证先提交的先处理。</p>
     *
     * @param limit 最多取多少条
     * @return 任务列表
     */
    List<EduMarkingTask> selectResumable(@Param("limit") int limit);

    /**
     * 乐观地把任务从「待处理」置为「处理中」。
     *
     * <p>用状态作为条件，避免重启续跑与正常提交同时入队导致同一任务被处理两次。</p>
     *
     * @param taskId 业务标识
     * @param now    当前时间
     * @return 更新行数；0 表示已被其它线程抢占
     */
    int claimPending(@Param("taskId") String taskId, @Param("now") LocalDateTime now);

    /**
     * 更新任务状态与阶段信息。
     *
     * @param taskId   业务标识
     * @param status   状态
     * @param stage    阶段描述（可为空）
     * @param total    题目总数（可为空）
     * @param done     已判分数（可为空）
     * @param error    失败原因（可为空）
     * @param finish   是否写入完成时间
     * @param now      当前时间
     * @return 更新行数
     */
    int updateProgress(@Param("taskId") String taskId,
                       @Param("status") Integer status,
                       @Param("stage") String stage,
                       @Param("total") Integer total,
                       @Param("done") Integer done,
                       @Param("error") String error,
                       @Param("finish") boolean finish,
                       @Param("now") LocalDateTime now);

    /**
     * 写回任务处理产物（答题卡 / 图片路径 / 输入参数）。
     *
     * @param taskId         业务标识
     * @param answerSheetId  答题卡标识
     * @param imagePath      图片路径（重新保存时更新）
     * @param now            当前时间
     * @return 更新行数
     */
    int updateArtifacts(@Param("taskId") String taskId,
                        @Param("answerSheetId") Long answerSheetId,
                        @Param("imagePath") String imagePath,
                        @Param("now") LocalDateTime now);
}