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
 * 内置角色模板初始化器。
 *
 * <p>把打包在 classpath {@code characters-builtin/} 下的默认角色模板在 Bean 初始化阶段
 * 同步到 {@code {skillsRoot}/characters-builtin}，保证无论进程工作目录在哪，
 * 默认角色模板都可用。</p>
 *
 * <p>资源读取统一交给 {@link ClasspathDirSupport}：可执行 jar 下 {@code classpath*:} 枚举不到
 * {@code BOOT-INF/classes} 内的目录项，由该类回退为直接遍历 jar 条目。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinCharacterInitializer {

    /**
     * classpath 内置角色模板目录
     */
    private static final String CLASSPATH_DIR = "characters-builtin";

    @Value("${nomifun.skills-root:./skills}")
    private String skillsRoot;

    @PostConstruct
    void sync() {
        try {
            // 必须 normalize：默认值为 ./skills，未归一时路径含 "." 段，
            // 下面 target.startsWith(builtinRoot) 的逃逸检查会全部失配
            Path builtinRoot = Paths.get(skillsRoot).resolve("characters-builtin").normalize();
            Files.createDirectories(builtinRoot);

            var entries = ClasspathDirSupport.readDirectory(CLASSPATH_DIR);
            int copied = 0;
            for (var entry : entries.entrySet()) {
                Path target = builtinRoot.resolve(entry.getKey()).normalize();
                if (!target.startsWith(builtinRoot)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.write(target, entry.getValue());
                copied++;
            }
            log.info("[BuiltinCharacter] 内置角色模板同步完成 count={} root={}", copied, builtinRoot.toAbsolutePath());
        } catch (Exception e) {
            log.error("[BuiltinCharacter] 同步失败: {}", e.getMessage(), e);
        }
    }
}
