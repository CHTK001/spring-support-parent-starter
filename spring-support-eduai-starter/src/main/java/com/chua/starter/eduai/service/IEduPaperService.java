package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.vo.PaperVO;

/**
 * 试卷服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduPaperService extends IService<EduPaper> {

    /**
     * 分页查询试卷
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PaperVO> pageQuery(PaperQueryDTO query);

    /**
     * 获取试卷详情（含题目列表）
     *
     * @param paperId 试卷ID
     * @return 试卷详情
     */
    PaperVO getPaperDetail(Long paperId);

    /**
     * AI生成试卷
     *
     * @param dto 组卷配置
     * @return 试卷ID
     */
    Long generatePaper(PaperGenerateDTO dto);

    /**
     * 替换试卷中的某道题
     *
     * @param paperId       试卷ID
     * @param oldQuestionId 旧题目ID
     * @param newQuestionId 新题目ID
     * @return 是否成功
     */
    boolean replaceQuestion(Long paperId, Long oldQuestionId, Long newQuestionId);
}