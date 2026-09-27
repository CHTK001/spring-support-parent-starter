package com.chua.starter.ai.support.chat;

import com.chua.starter.ai.support.util.ClasspathDirSupport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 内置技能初始化器。
 *
 * <p>把打包在 classpath {@code skills-builtin/} 下的默认常用技能（docx、excel、pdf、
 * agent-browser、playwright 等）在 Bean 初始化阶段（早于 Web 服务对外）同步到
 * {@code {skillsRoot}/builtin}，保证无论进程工作目录在哪（模块目录 / 部署目录 / 服务器），
 * 默认技能都可用。内置技能为受管只读资源，每次启动按 classpath 覆盖，以便版本升级后自动更新。</p>
 *
 * <p>资源读取统一交给 {@link ClasspathDirSupport}：可执行 jar 下 {@code classpath*:} 枚举不到
 * {@code BOOT-INF/classes} 内的目录项，由该类回退为直接遍历 jar 条目。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinSkillInitializer {

    /**
     * classpath 内置技能目录
     */
    private static final String CLASSPATH_DIR = "skills-builtin";

    private final ChatSkillSupport chatSkillSupport;

    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    @PostConstruct
    void sync() {
        try {
            // 必须 normalize：默认值为 ./skills，未归一时路径含 "." 段，
            // 下面 target.startsWith(builtinRoot) 的逃逸检查会全部失配
            Path builtinRoot = Paths.get(skillsRoot).resolve("builtin").normalize();
            Files.createDirectories(builtinRoot);

            var entries = ClasspathDirSupport.readDirectory(CLASSPATH_DIR);
            int copied = 0;
            for (var entry : entries.entrySet()) {
                Path target = builtinRoot.resolve(entry.getKey()).normalize();
                // 防止异常相对路径逃逸出 builtin 根目录
                if (!target.startsWith(builtinRoot)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
                copied++;
            }
            chatSkillSupport.invalidate();
            log.info("[BuiltinSkill] 内置技能同步完成 count={} root={}", copied, builtinRoot.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[BuiltinSkill] 内置技能同步失败: {}", e.getMessage());
        }
    }
}
