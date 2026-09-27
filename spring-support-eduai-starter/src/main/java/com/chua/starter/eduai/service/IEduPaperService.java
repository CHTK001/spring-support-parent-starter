package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.dto.PaperComposeUpdateDTO;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.dto.PaperQuestionItemDTO;
import com.chua.starter.eduai.model.vo.PaperHistoryVO;
import com.chua.starter.eduai.model.vo.PaperQuestionQrVO;
import com.chua.starter.eduai.model.vo.PaperVO;

import java.util.List;

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
     * @param paperId 试卷标识
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
     * @param paperId       试卷标识
     * @param oldQuestionId 旧题目标识
     * @param newQuestionId 新题目标识
     * @return 是否成功
     */
    boolean replaceQuestion(Long paperId, Long oldQuestionId, Long newQuestionId);

    /**
     * 新建空白试卷（手动组卷，source=MANUAL，status=草稿，总分 100，版本 1）。
     *
     * @param dto 卷头信息，可为空（为空时使用默认值，后续通过更新接口修改）
     * @return 试卷ID
     */
    Long createManualPaper(PaperComposeUpdateDTO dto);

    /**
     * 批量加题（生成题项唯一编号，按当前顺序递增）。
     *
     * @param paperId 试卷标识
     * @param items   加题项列表（题目ID + 分值）
     * @return 实际加入的题目数量
     */
    int addQuestions(Long paperId, List<PaperQuestionItemDTO> items);

    /**
     * 移除试卷中的题目（题项唯一编号保留，不再变化）。
     *
     * @param paperId    试卷标识
     * @param questionId 题目标识
     * @return 是否成功
     */
    boolean removeQuestion(Long paperId, Long questionId);

    /**
     * 按题项编号列表的顺序重排题目顺序。
     *
     * @param paperId     试卷标识
     * @param questionNos 题项唯一编号列表（必须与本试卷现有题项一一对应）
     * @return 是否成功
     */
    boolean sortQuestions(Long paperId, List<String> questionNos);

    /**
     * 把当前所有题项按题数均分 100 分（取整，余数从前向后每题 +1）。
     *
     * @param paperId 试卷标识
     * @return 是否成功
     */
    boolean evenQuestionScores(Long paperId);

    /**
     * 更新试卷卷头/纸张/模板/页头页脚（字段为空表示不修改）。
     *
     * @param paperId 试卷标识
     * @param dto     更新内容
     * @return 更新后的试卷详情（不含题目列表）
     */
    PaperVO updatePaper(Long paperId, PaperComposeUpdateDTO dto);

    /**
     * 发布试卷（强制校验题项分值合计 == 100）。
     *
     * @param paperId 试卷标识
     * @return 是否成功
     */
    boolean publishPaper(Long paperId);

    /**
     * 试卷历史列表（按版本倒序，不含快照大字段）。
     *
     * @param paperId 试卷标识
     * @return 历史列表
     */
    List<PaperHistoryVO> listHistory(Long paperId);

    /**
     * 历史快照详情（含快照，并校验快照摘要是否被改写）。
     *
     * @param historyId 历史标识
     * @return 历史详情
     */
    PaperHistoryVO getHistoryDetail(Long historyId);

    /**
     * 按题项唯一编号反查（二维码扫码：题目、所属试卷、版本、分值、顺序）。
     *
     * @param questionNo 题项唯一编号
     * @return 题项反查结果
     */
    PaperQuestionQrVO queryQuestionByNo(String questionNo);
}
