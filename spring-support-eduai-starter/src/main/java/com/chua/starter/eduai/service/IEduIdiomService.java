package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduIdiom;

import java.util.List;
import java.util.Map;

/**
 * 成语服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduIdiomService extends IService<EduIdiom> {

    /**
     * 根据学段获取成语列表
     *
     * @param gradeLevel 学段
     * @param grade      年级
     * @param count      数量
     * @return 成语列表
     */
    List<EduIdiom> listByGradeLevel(String gradeLevel, String grade, int count);

    /**
     * 分页查询成语（管理端全量浏览；稳定排序，非随机）。
     *
     * @param keyword    关键字（成语 / 拼音 / 释义），可为空
     * @param gradeLevel 学段，可为空
     * @param grade      年级，可为空
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    PageResult<EduIdiom> pageQuery(String keyword, String gradeLevel, String grade,
                                  Integer pageNum, Integer pageSize);

    /**
     * 生成成语消消乐游戏数据
     *
     * @param gradeLevel 学段
     * @param grade      年级
     * @param count      成语数量
     * @return 游戏数据（包含成语、拼音、释义等；{@code scope} 表明取材范围：
     *         {@code level} 按学段 / {@code mixed} 本学段不够用全库补齐 / {@code all} 整盘来自全库）
     */
    Map<String, Object> generateIdiomGame(String gradeLevel, String grade, int count);

    /**
     * 验证成语配对
     *
     * @param idiomId     成语ID
     * @param pinyin      拼音
     * @param meaning     释义
     * @return 是否匹配
     */
    boolean validateIdiomPair(Long idiomId, String pinyin, String meaning);

    /**
     * 自动生成成语接龙题目。
     *
     * <p>规则：下一个成语的**首字**等于当前成语的**末字**。服务端从成语库
     * （按学段过滤）挑一个可接续的起点，并按该规则向后推演出期望步数；
     * 返回值**不含答案**，答案由 {@link #validateChain(String, String)} 校验，
     * 避免前端拿到答案直接通关。</p>
     *
     * @param gradeLevel 学段（可空）
     * @param grade      年级（可空）
     * @param length     期望步数，服务端夹在 3~10
     * @return start（起始成语）+ length（实际步数）+ 规则说明
     */
    Map<String, Object> generateChain(String gradeLevel, String grade, int length);

    /**
     * 校验一步接龙。
     *
     * @param current 当前成语
     * @param answer  用户接的成语
     * @return ok / message / next（接上后的末字）/ meaning
     */
    Map<String, Object> validateChain(String current, String answer);

    /**
     * 接龙提示：为当前成语给出一个可用答案。
     *
     * @param current 当前成语
     * @return ok + content/pinyin/meaning；无可用答案时 ok=false
     */
    Map<String, Object> chainHint(String current);
}
