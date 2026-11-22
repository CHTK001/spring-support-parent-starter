package com.chua.starter.eduai.service;

import com.chua.starter.eduai.model.dto.PaperAssignDTO;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import com.chua.starter.eduai.model.vo.PaperVO;

import java.util.List;

/**
 * 班级试卷布置服务：老师给班级组卷、学生按班可见。
 *
 * <p>归属一律取自登录态，不接受客户端传「我是谁」；老师的班级数据范围按
 * {@code edu_teacher_class} 判定，与教师中心「我的班级」同一口径。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduPaperAssignService {

    /**
     * 老师把一张试卷布置给一个班级。
     *
     * <p>同一 (试卷, 班级) 重复布置走「复活已有行」，不插新行 —— 唯一键在库上，
     * 先撤后布也不会堆出多行。</p>
     *
     * @param dto 布置信息
     * @return 布置记录标识
     */
    Long assign(PaperAssignDTO dto);

    /**
     * 我（老师）布置过的试卷。
     *
     * @return 布置视图列表
     */
    List<PaperAssignVO> mine();

    /**
     * 撤回布置（软删：状态置 0，学生端立即看不到）。
     *
     * @param id 布置标识
     * @return 是否成功
     */
    boolean revoke(Long id);

    /**
     * 学生：我所在班级收到的试卷。
     *
     * <p>按登录态定位自己的档案与班级，不接受客户端传班级 ID。</p>
     *
     * @return 布置视图列表（未绑定档案或未分班时为空列表，不是错误）
     */
    List<PaperAssignVO> myClassPapers();

    /**
     * 学生：查看布置给本班的某张试卷详情（含题目）。
     *
     * <p>按**布置关系**授权：只有「已布置给我班」才放行（试卷本身的
     * 归属人校验由 {@code getPaperDetailUnchecked} 的调用链豁免）。</p>
     *
     * @param paperId 试卷标识
     * @return 试卷详情（含题目）
     */
    PaperVO studentPaperDetail(Long paperId);
}
