package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.BatchMarkingRequest;
import com.chua.starter.eduai.model.dto.MarkingToBankDTO;
import com.chua.starter.eduai.model.vo.MarkingProgressVO;
import com.chua.starter.eduai.model.vo.MarkingToBankResultVO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishPreviewVO;
import com.chua.starter.eduai.model.vo.MarkingUndoResultVO;
import com.chua.starter.eduai.model.vo.BatchMarkingResultVO;

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
     * 读取答卷图片的指定页，可指定图片变体。
     *
     * @param taskId  任务标识
     * @param page    页码（从 1 开始）
     * @param variant 变体：{@code thumb} 取缩略图（最长边 800px 的灰度 PNG），
     *                其它值或 null 取原图
     * @return 图片字节
     */
    byte[] getSheetImage(String taskId, int page, String variant);

    /**
     * 教师确认或修正评分
     *
     * @param dto 修正数据
     * @return 是否成功
     */
    boolean confirmMarking(MarkingConfirmDTO dto);

    /**
     * 阅卷完成：按最终分把该卷错题归入对应学生的错题库。
     *
     * <p>老师复核收口的动作。学生取答卷识别 / 人工纠正后的学号，
     * 缺学生时拒绝完成（错题必须落到「对应学生」）。
     * 幂等：同一任务重复调用不会重复累加错题次数。</p>
     *
     * @param taskId 任务业务标识
     * @return 完成结果（学生 / 试卷 / 本次登记的错题数 / 是否此前已完成）
     */
    MarkingFinishResultVO finishMarking(String taskId);

    /**
     * 阅卷完成（支持剔除题目）。
     *
     * <p>完成前老师可能用预览弹窗勾选剔除部分题目（如得分率刚好压线、
     * 主观题存疑等）。剔除列表里的题目即使按阈值判定为「错」，本次也不登记；
     * 已登记的错题行不受影响（剔除只影响本次新增）。</p>
     *
     * @param taskId            任务业务标识
     * @param excludeQuestionIds 本次不登记进错题本的题目列表（可空）
     * @return 完成结果
     */
    MarkingFinishResultVO finishMarking(String taskId, List<Long> excludeQuestionIds);

    /**
     * 阅卷完成预览：按当前判错阈值算出将登记的题目清单。
     *
     * <p>老师点「完成」前先看清单，确认后再调用 {@link #finishMarking(String, List)}。
     * 预览不改任何数据。</p>
     *
     * @param taskId 任务业务标识
     * @return 预览结果（学生 / 将登记题数 / 题目清单）
     */
    MarkingFinishPreviewVO previewFinishMarking(String taskId);

    /**
     * 批量阅卷完成。
     *
     * <p>逐任务执行，单个任务失败只记录失败原因，不整体回滚。
     * 每个任务可按 {@code excludeQuestionIdsByTask} 剔除部分题目。</p>
     *
     * @param request 批量请求（任务列表 + 可选剔除映射）
     * @return 逐任务完成结果
     */
    List<BatchMarkingResultVO> batchFinishMarking(BatchMarkingRequest request);

    /**
     * 批量撤销「完成」。
     *
     * <p>逐任务执行，单个任务失败只记录失败原因，不整体回滚。</p>
     *
     * @param request 批量请求（任务列表）
     * @return 逐任务撤销结果
     */
    List<BatchMarkingResultVO> batchUnfinishMarking(BatchMarkingRequest request);

    /**
     * 撤销「完成」：删除本次登记的错题并复位完成标记。
     *
     * @param taskId 任务业务标识
     * @return 撤销结果（删除条数 / 之前是否已完成）
     */
    MarkingUndoResultVO unfinishMarking(String taskId);

    /**
     * 取任务当前进度快照（SSE 建连时先推一帧，避免前端空等）。
     *
     * @param taskId 任务业务标识
     * @return 进度快照
     */
    MarkingProgressVO getProgress(String taskId);

    /**
     * 把外来卷识别出的题目加入题库。
     *
     * <p><b>为什么只有外来卷</b>：自有卷（{@code SELF}）的题目本来就来自题库，
     * 阅卷结果行上的 {@code questionId} 直指已有题目，再「入库」只会造出重复题目。
     * 外来卷（{@code ANY}）的题目是 OCR + 模型现场抽出来的，此前判完就散掉；
     * 本方法让老师复核后把它们沉淀成题库题目。</p>
     *
     * <p>题干与学生作答一律回读数据库明细，不采信客户端传值；标准答案优先取老师补写，
     * 未补写时只在「该题本次判为满分」的情况下用学生作答代替（答对的作答即正确解），
     * 判错又没补答案的题不入库，作为 {@code needAnswerNos} 返回。</p>
     *
     * @param taskId 任务业务标识
     * @param dto    入题库请求（选中题目 + 学科/学段/年级 + 补充答案）
     * @return 逐条容错的入库结果
     */
    MarkingToBankResultVO addToQuestionBank(String taskId, MarkingToBankDTO dto);
}
