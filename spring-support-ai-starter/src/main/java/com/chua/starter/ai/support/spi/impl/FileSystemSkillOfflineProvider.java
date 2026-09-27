package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.spi.SkillOfflineProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件系统本地技能离线 Provider。
 *
 * <p>扫描本地技能根目录（{@code nomifun.skills-root} 系统属性，缺省回退到项目
 * {@code skills} 目录）下的一级子目录作为本地技能来源。通过
 * {@code META-INF/services/com.chua.starter.ai.support.spi.SkillOfflineProvider}
 * 以 ServiceLoader 注册，供「Skills 设置」页枚举本地 Provider 开关。</p>
 *
 * <p>注意：ServiceLoader 通过无参构造实例化，不注入 Spring Bean，因此路径从系统属性
 * / 环境变量读取，保持自包含。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public class FileSystemSkillOfflineProvider implements SkillOfflineProvider {

    /**
     * Provider 展示名
     */
    @Override
    public String name() {
        return "本地技能目录";
    }

    /**
     * 扫描技能根目录一级子目录，每个子目录作为一个技能条目。
     *
     * @return 技能列表（含 code/name/description）
     */
    @Override
    public List<Map<String, Object>> listSkills() {
        List<Map<String, Object>> skills = new ArrayList<>();
        File root = resolveRoot();
        if (root == null || !root.isDirectory()) {
            return skills;
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return skills;
        }
        for (File dir : children) {
            String code = dir.getName();
            if (code.startsWith(".")) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", code);
            item.put("description", "本地技能目录: " + dir.getAbsolutePath());
            skills.add(item);
        }
        return skills;
    }

    /**
     * 解析技能根目录：优先系统属性 nomifun.skills-root，回退常见绝对路径。
     *
     * @return 根目录，不存在时返回 空
     */
    private File resolveRoot() {
        String configured = System.getProperty("nomifun.skills-root");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("NOMIFUN_SKILLS_ROOT");
        }
        if (configured != null && !configured.isBlank()) {
            File f = new File(configured);
            if (f.isDirectory()) {
                return f;
            }
        }
        // 只保留跨平台可用的相对目录；绝对路径一律由系统属性或 NOMIFUN_SKILLS_ROOT 提供，
        // 不可写死某台机器的路径（曾在 Linux 上被当成相对路径，匹配到字面 Windows 目录）
        String[] candidates = {
                "skills",
                "./skills"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.isDirectory()) {
                return f;
            }
        }
        return null;
    }
}
