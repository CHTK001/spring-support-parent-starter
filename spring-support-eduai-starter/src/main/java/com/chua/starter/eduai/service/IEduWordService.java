package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduWord;

import java.util.List;
import java.util.Map;

/**
 * 词语库服务。
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface IEduWordService extends IService<EduWord> {

    /**
     * 分页查询词语。
     *
     * @param keyword    关键字（整词 / 拼音 / 无声调拼音 / 释义 / 近义 / 反义 / 同音）
     * @param gradeLevel 学段（可空；兼容 PRIMARY 码值与「小学」中文名）
     * @param kind       语料类型（可空：WORD / XIEHOUYU / PHRASE）
     * @param pageNum    页码（可空）
     * @param pageSize   每页条数（可空）
     * @return 分页结果
     */
    PageResult<EduWord> pageQuery(String keyword, String gradeLevel, String kind, Integer pageNum, Integer pageSize);

    /**
     * 保存词语（按词唯一：同一词重复提交则更新）。
     *
     * @param entity 词语
     * @return 词语标识
     */
    Long saveWord(EduWord entity);

    /**
     * 删除词语。
     *
     * @param id 词语标识
     * @return 是否成功
     */
    boolean deleteWord(Long id);

    /**
     * 关联检索：给某个词找它的「相似词语 / 反向词语 / 同音词 / 同音字词 / 可造句例句 / 含相同字的词」。
     *
     * <p>对应词语库列表的右键菜单。每种口径都先看词条自己维护的字段（近义 / 反义 / 同音），
     * 字段为空时再退化为按库内数据自动匹配，保证右键任何一项都有可读结果或明确的空态说明。</p>
     *
     * @param wordId 词语标识
     * @param mode   检索口径：similar 相似 / antonym 反向 / homophone 同音 / pinyin 同音字词 /
     *               sentence 造句 / char 含相同字
     * @return 检索结果：{ mode, label, source, items[] }；词不存在时抛业务异常
     */
    Map<String, Object> related(Long wordId, String mode);

    /**
     * 按关键字联想词语（页面搜索框的候选提示用）。
     *
     * @param keyword 关键字
     * @param count   条数，服务端夹在 1~50
     * @return 词语列表
     */
    List<EduWord> suggest(String keyword, int count);
}
