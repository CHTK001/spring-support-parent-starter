package com.chua.starter.eduai.model.dto;

import lombok.Data;

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
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class InsectGuideSaveDTO {

    /**
     * 物种英文标识（slug），关联 edu_insect.insect_code
     */
    private String insectCode;

    /**
     * 分步讲解；每一步可带 anchor，用于驱动 3D 镜头对位
     */
    private List<LessonStep> lesson;

    /**
     * 动态演示
     */
    private Motion motion;

    /**
     * 单选题
     */
    private List<QuizItem> quiz;

    /**
     * 栖境（它在哪里）
     */
    private Habitat habitat;

    /**
     * 分步讲解的一步
     */
    @Data
    public static class LessonStep {

        /**
         * 步骤标题
         */
        private String title;

        /**
         * 步骤正文
         */
        private String body;

        /**
         * 对应 3D 模型的部位锚点；为空表示本步不联动镜头
         */
        private String anchor;
    }

    /**
     * 动态演示内容
     */
    @Data
    public static class Motion {

        /**
         * 标题
         */
        private String title;

        /**
         * 正文
         */
        private String body;
    }

    /**
     * 一道单选题
     */
    @Data
    public static class QuizItem {

        /**
         * 题干
         */
        private String question;

        /**
         * 选项（通常 3 个）
         */
        private List<String> options;

        /**
         * 正确选项下标
         */
        private Integer answer;

        /**
         * 作答后的解释
         */
        private String explain;
    }

    /**
     * 栖境内容
     */
    @Data
    public static class Habitat {

        /**
         * 标题
         */
        private String title;

        /**
         * 正文
         */
        private String body;
    }
}
