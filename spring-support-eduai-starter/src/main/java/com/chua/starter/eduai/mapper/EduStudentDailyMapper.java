package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduStudentDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学生每日学习汇总 Mapper
 *
 * <p>写入走 BaseMapper（本表列由实体自己声明，列清单与库表一致）；
 * 首页统计需要按日期范围聚合与按班级排名，这两类查询走自定义 XML。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduStudentDailyMapper extends BaseMapper<EduStudentDaily> {

    /**
     * 汇总某个学生在指定日期区间内的学习量
     *
     * @param studentId 学生ID
     * @param from      起始日期（含）
     * @param to        结束日期（含）
     * @return 含 studyCount / studyMinutes / questionCount / correctCount / stars / points 的合计；无记录时各项为 0
     */
    Map<String, Object> sumRange(@Param("studentId") Long studentId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /**
     * 取某学生最近有学习记录的日期（倒序），用于计算连续打卡天数
     *
     * @param studentId 学生ID
     * @param until     不晚于该日期
     * @param limit     最多取几天
     * @return 日期列表，倒序
     */
    List<LocalDate> selectActiveDates(@Param("studentId") Long studentId,
                                      @Param("until") LocalDate until,
                                      @Param("limit") int limit);

    /**
     * 按学生汇总积分，用于班级排名
     *
     * @param studentIds 学生ID集合，不能为空
     * @param from       起始日期（含）
     * @param to         结束日期（含）
     * @return 每行含 studentId 与 points
     */
    List<Map<String, Object>> sumPointsByStudents(@Param("studentIds") List<Long> studentIds,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);
}
