package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;

import java.util.List;
import java.util.Map;

/**
 * 阅卷服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduMarkingService {

    /**
     * 提交答题卡，启动阅卷
     *
     * @param dto 答题卡数据
     * @return 任务ID
     */
    String submitAnswerSheet(MarkingSubmitDTO dto);

    /**
     * 扫描试卷图片并阅卷（支持两类试卷）。
     *
     * <p>流程：版面还原（pp-doc-layout）→ 文字提取（paddleocrv6 检测+识别）→ 结构化 → 判分。</p>
     * <ul>
     * <li>{@code SELF} 自有卷：只从识别文本里抽「题号 + 作答」，分值与标准答案一律回数据库取
     * （{@code edu_paper_question.paper_question_score} / {@code edu_question.question_answer}），
     * 客观题精确比对、主观题交 AI；准确性来自数据库而非模型。</li>
     * <li>{@code ANY} 任意卷：题库无对应记录，题干与作答都由模型抽取并自行判定。</li>
     * </ul>
     *
     * <p><b>支持多张图片</b>：一份试卷通常跨多页，调用方按页顺序传入多张；
     * 服务端逐张做版面还原 + OCR，再按传入顺序拼接识别文本，统一结构化为题目。</p>
     *
     * @param images    试卷/答题卡图片字节，**按页顺序**，至少 1 张
     * @param mode      试卷类型：{@code SELF} 自有卷 / {@code ANY} 任意卷
     * @param paperId   自有卷对应的试卷 ID；任意卷可为空
     * @param studentId 学生标识
     * @param examId    考试 ID，自有卷用于取卷面分值；可为空
     * @param maxScore  任意卷每题满分；为空时取 {@code plugin.eduai.marking.max-score}
     * @return 任务 ID（用 {@code GET /v2/edu/marking/result/{taskId}} 轮询结果）
     */
    String scanAndMark(List<byte[]> images, String mode, Long paperId, String studentId, Long examId, Integer maxScore);

    /**
     * 轮询阅卷结果
     *
     * @param taskId 任务标识
     * @return 阅卷结果
     */
    MarkingResultVO getResult(String taskId);

    /**
     * 学生查看自己的阅卷结果（归属按任务上的学生标识，而非任务归属人）
     *
     * @param taskId    任务标识
     * @param studentId 当前学生学号
     * @return 阅卷结果
     */
    MarkingResultVO getResultForStudent(String taskId, String studentId);

    /**
     * 获取「与图片同版式」的结构化解析结果。
     *
     * <p>返回逐页的版面区域坐标、文字块坐标、页尺寸，以及题号锚点与判分明细（含知识点），
     * 供前端把分数标记/对错/知识点按原图位置叠放并能点击编辑。</p>
     *
     * @param taskId 任务标识
     * @return 结构化数据
     */
    Map<String, Object> getStructure(String taskId);

    /**
     * 读取阅卷所用答卷图片的指定页（同版式展示的打底图）。
     *
     * @param taskId 任务标识
     * @param page   页码（从 1 开始）
     * @return 图片字节
     */
    byte[] getSheetImage(String taskId, int page);

    /**
     * 教师确认或修正评分
     *
     * @param dto 修正数据
     * @return 是否成功
     */
    boolean confirmMarking(MarkingConfirmDTO dto);
}
