package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
     * 生成成语消消乐游戏数据
     *
     * @param gradeLevel 学段
     * @param grade      年级
     * @param count      成语数量
     * @return 游戏数据（包含成语、拼音、释义等）
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
}
