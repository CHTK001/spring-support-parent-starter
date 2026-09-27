package com.chua.starter.eduai.support.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 学段值归一化工具。
 *
 * <p>本项目同时存在两套学段写法：</p>
 * <ul>
 *   <li>码值：{@code PRIMARY / JUNIOR / SENIOR / UNIVERSITY}，前端下拉与维度表使用</li>
 *   <li>中文名：{@code 小学 / 初中 / 高中 / 大学}，种子数据与采集入库使用</li>
 * </ul>
 *
 * <p>两者不统一时，筛选条件查不到数据（例如题库页提交 {@code PRIMARY} 而库里存 {@code 小学}）。
 * 本工具把码值统一收敛为中文名，与种子数据保持一致；空值与未知值原样返回。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class GradeLevels {

    /**
     * 工具类禁止实例化。
     */
    private GradeLevels() {
    }

    /**
     * 把学段码值归一化为中文名。
     *
     * @param level 原始学段值，可为空
     * @return 归一化后的中文学段名；入参为空时原样返回
     */
    public static String normalize(String level) {
        if (!StringUtils.hasText(level)) {
            return level;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "PRIMARY", "小学" -> "小学";
            case "JUNIOR", "MIDDLE", "初中" -> "初中";
            case "SENIOR", "HIGH", "高中" -> "高中";
            case "UNIVERSITY", "COLLEGE", "大学" -> "大学";
            default -> level.trim();
        };
    }
}
