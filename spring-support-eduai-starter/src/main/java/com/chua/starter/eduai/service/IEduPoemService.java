package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduPoem;

import java.util.List;
import java.util.Map;

/**
 * 古诗服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduPoemService extends IService<EduPoem> {

    /**
     * 按学段/年级取古诗（学段为空时不限）
     *
     * @param gradeLevel 学段（小学/初中/高中）
     * @param grade      年级（可为空）
     * @param count      数量上限
     * @return 古诗列表
     */
    List<EduPoem> listByGradeLevel(String gradeLevel, String grade, int count);

    /**
     * 分页查询古诗（管理端全量浏览；稳定排序，非随机）。
     *
     * @param keyword    关键字（标题 / 作者 / 正文），可为空
     * @param gradeLevel 学段（小学/初中/高中，可传码值），可为空
     * @param grade      年级，可为空
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    PageResult<EduPoem> pageQuery(String keyword, String gradeLevel, String grade,
                                 Integer pageNum, Integer pageSize);

    /**
     * 生成古诗趣味游戏数据（补全下句：给上一句，选正确的下一句）
     *
     * @param gradeLevel 学段（可为空）
     * @param grade      年级（可为空）
     * @param count      题目数量
     * @return 游戏数据；{@code scope} 表明取材范围：{@code level} 按学段 / {@code all} 本学段无内容时来自全库
     */
    Map<String, Object> generatePoemGame(String gradeLevel, String grade, int count);
}
