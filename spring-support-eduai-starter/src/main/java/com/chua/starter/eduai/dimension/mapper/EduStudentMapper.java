package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生档案 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduStudentMapper extends BaseMapper<EduStudent> {

    /**
     * 列表学生（XML 语句 listStudents：在读状态 + 班级/学段/年级筛选 + 归属隔离，按学生ID升序）。
     *
     * @param classId    班级标识，为空时不限定
     * @param gradeLevel 学段，为空时不限学段
     * @param grade      年级，为空时不限年级
     * @param scope      归属隔离参数
     * @return 学生列表
     */
    List<EduStudent> listStudents(@Param("classId") Long classId,
                                  @Param("gradeLevel") String gradeLevel,
                                  @Param("grade") String grade,
                                  @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 按学号取学生（XML 语句 selectByStudentNo）。
     *
     * <p>学生端令牌里的 {@code userId} 就是学号，故此查询<b>不加归属过滤</b>——
     * 归属字段是给管理端列表用的隔离依据，登录态自身已限定身份。</p>
     *
     * @param studentNo 学号
     * @return 学生档案，不存在返回 {@code null}
     */
    EduStudent selectByStudentNo(@Param("studentNo") String studentNo);

    /**
     * 判断学号是否已存在（XML 语句 existsByStudentNo）。
     *
     * <p>用于学号生成的插入前查重。并发下的最终保证仍是库上的
     * {@code UNIQUE(student_no)} 约束，此处只是让报错更友好。</p>
     *
     * @param studentNo 学号
     * @return 存在返回 1，否则返回 {@code null}
     */
    Integer existsByStudentNo(@Param("studentNo") String studentNo);

    /**
     * 取某前缀下最大的纯数字后缀学号（XML 语句 selectMaxStudentNoByPrefix）。
     *
     * @param prefix 学号前缀（入学年份）
     * @return 最大学号，无匹配返回 {@code null}
     */
    String selectMaxStudentNoByPrefix(@Param("prefix") String prefix);
}
