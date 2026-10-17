package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 昆虫深度讲解与测验保存 DTO
 *
 * <p>与读取端（{@code /v2/edu/insect/guide/{insectCode}} 的返回结构）同形，
 * 前端把编辑好的整份讲解提交上来即可，不需要自己序列化 JSON 文本 ——
 * lesson / quiz 的序列化在服务层完成。</p>
 *
 * <p>校验约定：lesson 与 quiz 允许为空（新物种可以先只建物种信息，讲解后补）；
 * quiz 的 answer 必须是 options 的合法下标，非法时按 0 处理并保留原选项。</p>
 *
 * @param insectCode 物种英文标识（slug），关联 edu_insect.insect_code
 * @param lesson     分步讲解；每一步可带 anchor，用于驱动 3D 镜头对位
 * @param motion     动态演示
 * @param quiz       单选题
 * @param habitat    栖境（它在哪里）
 * @since 4.0.0.42
 * @author CH
 */
public record InsectGuideSaveDTO(
        String insectCode,
        List<LessonStep> lesson,
        Motion motion,
        List<QuizItem> quiz,
        Habitat habitat) {

    /**
     * 分步讲解的一步
     *
     * @param title  步骤标题
     * @param body   步骤正文
     * @param anchor 对应 3D 模型的部位锚点；为空表示本步不联动镜头
     */
    public record LessonStep(
            String title,
            String body,
            String anchor) {
    }

    /**
     * 动态演示内容
     *
     * @param title 标题
     * @param body  正文
     */
    public record Motion(
            String title,
            String body) {
    }

    /**
     * 一道单选题
     *
     * @param question 题干
     * @param options  选项（通常 3 个）
     * @param answer   正确选项下标
     * @param explain  作答后的解释
     */
    public record QuizItem(
            String question,
            List<String> options,
            Integer answer,
            String explain) {
    }

    /**
     * 栖境内容
     *
     * @param title 标题
     * @param body  正文
     */
    public record Habitat(
            String title,
            String body) {
    }
}
