package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduMarkingSheetParse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 试卷解析结果 Mapper。
 *
 * @author CH
 */
@Mapper
public interface EduMarkingSheetParseMapper extends BaseMapper<EduMarkingSheetParse> {

    /**
     * 按任务标识取全部解析项（XML 语句 selectByTaskId）。
     *
     * <p>按 (kind, question_no) 排序，保证「学生 → 总分 → 逐题」的稳定回显顺序。</p>
     *
     * @param taskId 阅卷任务业务标识
     * @return 解析项列表
     */
    List<EduMarkingSheetParse> selectByTaskId(String taskId);

    /**
     * 按任务标识删除全部解析项（重跑前清理，保证唯一键不会挡住新写入）。
     *
     * @param taskId 阅卷任务业务标识
     * @return 删除行数
     */
    int deleteByTaskId(String taskId);
}