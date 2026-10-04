package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduHanzi;

import java.util.List;
import java.util.Map;

/**
 * 汉字基础数据服务。
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface IEduHanziService extends IService<EduHanzi> {

    /**
     * 分页查询汉字。
     *
     * @param keyword    关键字（匹配字 / 拼音 / 释义）
     * @param gradeLevel 学段（可空）
     * @param pageNum    页码（可空）
     * @param pageSize   每页条数（可空）
     * @return 分页结果
     */
    PageResult<EduHanzi> pageQuery(String keyword, String gradeLevel, Integer pageNum, Integer pageSize);

    /**
     * 保存汉字（按字唯一：同一字重复提交则更新）。
     *
     * @param entity 汉字
     * @return 汉字标识
     */
    Long saveHanzi(EduHanzi entity);

    /**
     * 删除汉字。
     *
     * @param id 汉字标识
     * @return 是否成功
     */
    boolean deleteHanzi(Long id);

    /**
     * 生成识字练习题（消费「读音 / 笔画 / 笔顺 / 字画」这些维护字段）。
     *
     * <p>题型在服务端自动生成并轮转：{@code pinyin}（给字选读音）、
     * {@code strokes}（给字选笔画数）、{@code order}（把打散的笔画按书写顺序点出来）。
     * 正确答案与干扰项一起下发，客户端只负责展示与判分，
     * 无需自己造题；题干里带上 {@code strokeOrder} / {@code radical} / {@code structure} /
     * {@code meaning} / {@code image}，答完后直接当「学习卡」讲笔顺与释义。</p>
     *
     * @param gradeLevel 学段（可空；兼容 PRIMARY 码值与「小学」中文名）
     * @param count      题目数量，服务端夹在 1~20
     * @return 题目列表
     */
    List<Map<String, Object>> generatePractice(String gradeLevel, int count);
}
