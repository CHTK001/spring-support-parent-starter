package com.chua.starter.ai.support.chat;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 已安装技能支撑：扫描技能根目录，产出可注入对话的技能指令与启用状态。
 *
 * <p>技能是宿主级共享资源（安装/卸载写文件系统的 {@code skillsRoot/user}），不像记忆那样按用户隔离；
 * 因此启用状态也落在全局设置分组 {@code skills} 的 {@code disabled_skills} 字段上，被停用的技能
 * 不参与对话注入。</p>
 *
 * <p>注入形态为 SKILL.md 正文：普通对话没有技能工具执行环，只列名字与描述等于没装，
 * 故按预算把正文并入 system prompt。目录扫描结果短暂缓存，避免每轮对话都读盘。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ChatSkillSupport {

    /**
     * 技能根目录，与管理端安装目录一致
     */
    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot; // skills根

    /**
     * 技能设置分组
     */
    private static final String SKILL_SETTING_GROUP = "skills";

    /**
     * 被停用技能名单字段名（逗号分隔）
     */
    private static final String DISABLED_FIELD = "disabled_skills";

    /**
     * 单个技能正文注入上限（字符）
     */
    private static final int MAX_SKILL_CHARS = 4000;

    /**
     * 全部技能正文注入总上限（字符）
     */
    private static final int MAX_TOTAL_CHARS = 12000;

    /**
     * 目录扫描缓存有效期（毫秒）
     */
    private static final long CACHE_TTL = 30000L;

    /**
     * 全局设置服务（读停用名单）
     */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    /**
     * 扫描结果缓存
     */
    private volatile List<InstalledSkill> cached = List.of();

    /**
     * 缓存写入时间戳（毫秒）
     */
    private volatile long cachedAt;

    /**
     * 构造已安装技能支撑。
     *
     * @param sysAiGlobalSettingService 全局设置服务
     */
    public ChatSkillSupport(SysAiGlobalSettingService sysAiGlobalSettingService) {
        this.sysAiGlobalSettingService = sysAiGlobalSettingService;
    }

    /**
     * 注入对话的技能指令块（全部已启用技能）。
     *
     * @return 技能正文拼装出的提示词块，无启用技能时返回空串
     */
    public String promptBlock() {
        List<InstalledSkill> enabled = new ArrayList<>();
        for (InstalledSkill skill : skills()) {
            if (skill.enabled()) {
                enabled.add(skill);
            }
        }
        return buildBlock(enabled);
    }

    /**
     * 注入对话的技能指令块，仅纳入指定名称的已启用技能。
     * 用于用户在输入框显式选中某个/某些技能的场景。
     *
     * @param names 显式选中的技能名集合
     * @return 技能正文块；names 为空或无匹配时返回空串
     */
    public String promptBlock(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        List<InstalledSkill> picked = new ArrayList<>();
        for (InstalledSkill skill : skills()) {
            if (skill.enabled() && names.contains(skill.name())) {
                picked.add(skill);
            }
        }
        return buildBlock(picked);
    }

    /**
     * 按总字符预算把给定技能拼装成正文块。
     *
     * @param picked 待注入的技能列表
     * @return 正文块；列表为空时返回空串
     */
    private String buildBlock(List<InstalledSkill> picked) {
        if (picked.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是当前宿主已安装的技能，请在匹配其适用场景时严格按其指示执行：");
        int used = 0;
        for (InstalledSkill skill : picked) {
            String body = skill.content();
            if (body.length() > MAX_SKILL_CHARS) {
                body = body.substring(0, MAX_SKILL_CHARS) + "\n…（正文已截断）";
            }
            if (used + body.length() > MAX_TOTAL_CHARS) {
                log.info("[ChatSkill] 注入超出预算 {} 字符，省略剩余技能 (候选 {} 个)",
                        MAX_TOTAL_CHARS, picked.size());
                break;
            }
            used += body.length();
            sb.append("\n\n### 技能：").append(skill.name()).append('\n').append(body);
        }
        return sb.toString();
    }

    /**
     * 列出已安装技能（含启用状态），短暂缓存扫描结果。
     *
     * @return 已安装技能列表
     */
    public List<InstalledSkill> skills() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CACHE_TTL) {
            return cached;
        }
        Set<String> disabled = disabledNames();
        List<InstalledSkill> scanned = new ArrayList<>();
        for (String subdir : List.of("builtin", "user")) {
            Path dir = Paths.get(skillsRoot).resolve(subdir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                List<Path> children = stream.filter(Files::isDirectory).sorted().toList();
                for (Path child : children) {
                    Path md = skillMark(child);
                    if (md == null) {
                        continue;
                    }
                    String name = child.getFileName().toString();
                    boolean custom = "user".equals(subdir);
                    String id = skillId(custom, name);
                    String content = readContent(md);
                    scanned.add(new InstalledSkill(name, content, describe(content), iconOf(content),
                            child.toAbsolutePath().toString(), custom,
                            !disabled.contains(id) && !disabled.contains(name)));
                }
            } catch (IOException e) {
                log.debug("[ChatSkill] 扫描技能目录失败: {}", dir, e);
            }
        }
        cached = scanned;
        cachedAt = now;
        return scanned;
    }

    /**
     * 组装技能限定标识。
     *
     * @param custom 是否用户安装
     * @param name   技能名
     * @return {@code user:<name>} 或 {@code builtin:<name>}
     */
    private static String skillId(boolean custom, String name) {
        return (custom ? "user" : "builtin") + ":" + name;
    }

    /**
     * 切换技能启用状态。
     *
     * @param skillKey 技能限定标识（{@code user:<name>} / {@code builtin:<name>}），
     *                 或裸技能名——此时同名技能的 builtin/user 两份一起切换
     * @param enabled  是否启用
     */
    public void toggle(String skillKey, boolean enabled) {
        Set<String> disabled = qualify(disabledNames());
        Set<String> targets = expandKeys(skillKey);
        if (enabled) {
            disabled.removeAll(targets);
        } else {
            disabled.addAll(targets);
        }
        sysAiGlobalSettingService.upsertValue(SKILL_SETTING_GROUP, DISABLED_FIELD,
                String.join(",", disabled), "已停用的技能名单");
        cachedAt = 0L;
    }

    /**
     * 把一个技能键展开为磁盘上真实存在的停用名单条目。
     *
     * @param skillKey 限定标识或裸技能名
     * @return 待增删的名单条目
     */
    private Set<String> expandKeys(String skillKey) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(skillKey);
        if (skillKey.indexOf(':') >= 0) {
            return keys;
        }
        for (InstalledSkill skill : skills()) {
            if (skill.name().equals(skillKey)) {
                keys.add(skill.id());
            }
        }
        return keys;
    }

    /**
     * 把历史裸技能名展开为限定标识。
     *
     * <p>旧名单可能存的是裸技能名（同名时会同时压掉 builtin 与 user 两份），写入前先按磁盘上的
     * 实际技能展开成限定标识，之后逐个开关才不再互相牵连。</p>
     *
     * @param tokens 原始停用名单
     * @return 展开后的停用名单
     */
    private Set<String> qualify(Set<String> tokens) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.indexOf(':') >= 0) {
                expanded.add(token);
                continue;
            }
            boolean matched = false;
            for (InstalledSkill skill : skills()) {
                if (skill.name().equals(token)) {
                    expanded.add(skill.id());
                    matched = true;
                }
            }
            if (!matched) {
                expanded.add(token);
            }
        }
        return expanded;
    }

    /**
     * 使扫描缓存失效：安装/卸载/扫描/重载后调用，强制下次 {@link #skills()} 重新读盘。
     */
    public void invalidate() {
        cachedAt = 0L;
    }

    /**
     * 读取被停用技能名单。
     *
     * @return 停用技能名集合
     */
    private Set<String> disabledNames() {
        String raw = sysAiGlobalSettingService.getValue(SKILL_SETTING_GROUP, DISABLED_FIELD).orElse(null);
        Set<String> names = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return names;
        }
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                names.add(token);
            }
        }
        return names;
    }

    /**
     * 定位技能目录下的说明文件。
     *
     * @param dir 技能目录
     * @return 说明文件路径，不存在返回 null
     */
    private static Path skillMark(Path dir) {
        Path upper = dir.resolve("SKILL.md");
        if (Files.isRegularFile(upper)) {
            return upper;
        }
        Path lower = dir.resolve("skill.md");
        return Files.isRegularFile(lower) ? lower : null;
    }

    /**
     * 读取技能正文。
     *
     * @param md 说明文件路径
     * @return 正文内容，读取失败返回空串
     */
    private static String readContent(Path md) {
        try {
            return Files.readString(md, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.debug("[ChatSkill] 读取技能正文失败: {}", md, e);
            return "";
        }
    }

    /**
     * 从技能正文提炼描述。
     *
     * @param content SKILL.md 正文
     * @return 首个非空行清理后的描述，无有效行时返回空串
     */
    public static String describe(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content.replaceFirst("^\\uFEFF", "").stripLeading();

        // 1) 解析 YAML frontmatter，优先取其中的 description 字段
        if (text.startsWith("---")) {
            int firstNl = text.indexOf('\n', 3);
            if (firstNl >= 0) {
                int closing = text.indexOf("\n---", firstNl);
                String head = closing >= 0 ? text.substring(firstNl + 1, closing) : text.substring(firstNl + 1);
                String[] headLines = head.split("\n");
                for (int i = 0; i < headLines.length; i++) {
                    String line = headLines[i];
                    int indent = line.length() - line.stripLeading().length();
                    String trimmed = line.trim();
                    if (indent > 0 || trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(":")) {
                        continue;
                    }
                    int colon = trimmed.indexOf(':');
                    String key = trimmed.substring(0, colon).trim();
                    if (!"description".equalsIgnoreCase(key)) {
                        continue;
                    }
                    String value = trimmed.substring(colon + 1).trim();
                    boolean blockScalar = value.startsWith(">") || value.startsWith("|");
                    boolean quoted = value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")));
                    // 块标量（> 折叠 / | 字面）或空值的多行写法：收集后续缩进行，按空格折叠
                    if (blockScalar || value.isEmpty()) {
                        StringBuilder cont = new StringBuilder();
                        for (int j = i + 1; j < headLines.length; j++) {
                            String cl = headLines[j];
                            if (cl.isBlank()) {
                                continue;
                            }
                            int ci = cl.length() - cl.stripLeading().length();
                            if (ci == 0) {
                                break;
                            }
                            if (cont.length() > 0) {
                                cont.append(' ');
                            }
                            cont.append(cl.trim());
                        }
                        value = cont.toString().trim();
                    } else if (quoted) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!value.isBlank()) {
                        return value.length() > 120 ? value.substring(0, 120) : value;
                    }
                }
            }
        }

        // 2) 无 frontmatter 或其中缺少 description：跳过 frontmatter 块，取正文首个有效行
        for (String line : stripFrontmatter(text).split("\n")) {
            String t = line.replaceFirst("^#+\\s*", "").replace("**", "").trim();
            if (t.isEmpty() || t.startsWith("---")) {
                continue;
            }
            return t.length() > 120 ? t.substring(0, 120) : t;
        }
        return "";
    }

    /**
     * 读取 frontmatter 中声明的图标（简单标量）。
     *
     * @param content SKILL.md 正文
     * @return 图标图片地址或图标名，未声明时返回空串
     */
    public static String iconOf(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content.replaceFirst("^\\uFEFF", "").stripLeading();
        if (!text.startsWith("---")) {
            return "";
        }
        int firstNl = text.indexOf('\n', 3);
        if (firstNl < 0) {
            return "";
        }
        int closing = text.indexOf("\n---", firstNl);
        String head = closing >= 0 ? text.substring(firstNl + 1, closing) : text.substring(firstNl + 1);
        for (String raw : head.split("\n")) {
            int indent = raw.length() - raw.stripLeading().length();
            String trimmed = raw.trim();
            if (indent > 0 || trimmed.isEmpty() || !trimmed.contains(":")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            String key = trimmed.substring(0, colon).trim();
            if (!"icon".equalsIgnoreCase(key)) {
                continue;
            }
            String value = trimmed.substring(colon + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }

    /**
     * 去掉开头的 YAML frontmatter，返回正文（无 frontmatter 时原样返回）。
     * @param text 字符串
     * @return 返回值
    */
    private static String stripFrontmatter(String text) {
        String t = text.stripLeading();
        if (!t.startsWith("---")) {
            return t;
        }
        int firstNl = t.indexOf('\n', 3);
        if (firstNl < 0) {
            return t;
        }
        int closing = t.indexOf("\n---", firstNl);
        if (closing < 0) {
            return t;
        }
        int after = closing + 4;
        int nl = t.indexOf('\n', after);
        return nl >= 0 ? t.substring(nl + 1) : t.substring(after);
    }

    /**
     * 已安装技能。
     *
     * @param name        技能名（目录名），跨 builtin/user 可能重名
     * @param content     SKILL.md 正文
     * @param description 从正文提炼的描述
     * @param icon        frontmatter 中声明的图标（图片地址或图标名，可空）
     * @param location    技能目录绝对路径
     * @param custom      是否用户安装（false 表示内置）
     * @param enabled     是否参与对话注入
     */
    public record InstalledSkill(String name, String content, String description, String icon, String location,
                                 boolean custom, boolean enabled) {

        /**
         * 技能限定标识，用于唯一定位同名技能。
         *
         * @return {@code user:<name>} 或 {@code builtin:<name>}
         */
        public String id() {
            return skillId(custom, name);
        }
    }
}
