package com.chua.starter.job.support.glue.compile;

import lombok.extern.slf4j.Slf4j;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Java 胶水片段编译器：在进程内用 {@code javax.tools} 把片段源码编译成 class 文件。
 *
 * <p>刻意不落 {@code .java} 中间文件——源码只来自任务台账，
 * {@code JavaFileManager} 直接吃内存里的 {@link GlueSourceFile}，
 * 只有 class 产物落到 {@code outputDir} 供类加载器读取。</p>
 *
 * <p>需要 JDK：{@link ToolProvider#getSystemJavaCompiler()} 在 JRE（或裁剪过的 jlink 镜像）
 * 下返回 {@code null}，此时抛出带引导语的可读异常，由执行器记成 FAILURE。</p>
 *
 * <p>类路径解析见 {@link RuntimeClassPath}——不能直接用 {@code java.class.path}，
 * fat jar 下它只有一个值。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public final class JavaGlueCompiler {

    /**
     * 编译选项：关警告、关注解处理（片段不该触发 lombok 等处理器）、固定 UTF-8
     */
    private static final List<String> OPTIONS =
            List.of("-encoding", "UTF-8", "-nowarn", "-proc:none", "-g:none");

    /**
     * 编译片段源码。
     *
     * @param coordinates 主类坐标（由 {@link SourceCoordinates#of(String)} 解析）
     * @param source      源码正文
     * @param outputDir   class 输出目录，不存在时创建
     * @throws IllegalStateException 无编译器 / 源码编译失败 / 目录不可写
     */
    public static void compile(SourceCoordinates coordinates, String source, Path outputDir) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("GLUE_JAVA 片段需要 JDK 才能编译，"
                    + "当前运行环境没有 Java 编译器（疑似 JRE 或裁剪过的运行时镜像）");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var standard = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        try (standard) {
            var manager = new ClassPathFileManager(standard);
            standard.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
            var units = List.<JavaFileObject>of(new GlueSourceFile(coordinates.binaryName(), source));
            var task = compiler.getTask(null, manager, diagnostics, OPTIONS, null, units);
            var succeeded = task != null && Boolean.TRUE.equals(task.call());
            if (!succeeded) {
                throw new IllegalStateException("GLUE_JAVA 片段编译失败: " + render(diagnostics));
            }
            log.info("[job] Java 片段已编译 class={} outputDir={}", coordinates.binaryName(), outputDir);
        } catch (IOException ex) {
            throw new IllegalStateException("GLUE_JAVA 片段编译失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 把诊断集合渲染成可直接贴进任务日志的多行文本。
     *
     * @param diagnostics 诊断收集器
     * @return 渲染后的错误文本
     */
    private static String render(DiagnosticCollector<JavaFileObject> diagnostics) {
        var errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .toList();
        if (errors.isEmpty()) {
            return "编译器未给出诊断信息（源码为空或输出目录不可写）";
        }
        var text = new StringBuilder();
        for (var diagnostic : errors) {
            text.append('\n').append("第 ").append(diagnostic.getLineNumber())
                    .append(" 行: ").append(diagnostic.getMessage(Locale.ROOT));
        }
        return text.toString();
    }

    /**
     * 禁止实例化 Java 胶水片段编译器
     */
    private JavaGlueCompiler() {
    }
}
