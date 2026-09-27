package com.chua.starter.job.support.glue.compile;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 让 {@code javac} 用「运行时类路径索引」解析 {@code CLASS_PATH} 的文件管理器。
 *
 * <p>与默认实现相比只有一处不同：{@link #list} 在 {@link StandardLocation#CLASS_PATH}
 * 上会额外并入 {@link RuntimeClassPath} 的索引。默认实现读的是 {@code -classpath}，
 * 而 fat jar 下那个值等于没有；两者取并集，索引有缺口时也还有真实类路径兜底。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class ClassPathFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

    /**
     * 包名（点分，根包为空串）到该包下全部条目的映射
     */
    private final Map<String, List<ClassPathEntry>> byPackage;

    /**
     * 构造 类路径文件管理器。
     *
     * @param delegate 委托的标准文件管理器
     */
    ClassPathFileManager(StandardJavaFileManager delegate) {
        super(delegate);
        this.byPackage = RuntimeClassPath.groupByPackage(RuntimeClassPath.index());
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        if (location != StandardLocation.CLASS_PATH) {
            return super.list(location, packageName, kinds, recurse);
        }
        var merged = new ArrayList<JavaFileObject>();
        var seen = new HashSet<URI>();
        // 先收真实类路径，再用索引补齐；按 URI 去重，两边同时命中同一个类时只留一份
        for (var item : super.list(location, packageName, kinds, recurse)) {
            if (seen.add(item.toUri())) {
                merged.add(item);
            }
        }
        if (!kinds.contains(JavaFileObject.Kind.CLASS)) {
            return merged;
        }
        for (var entry : indexedOf(packageName, recurse)) {
            if (seen.add(entry.toUri())) {
                merged.add(entry);
            }
        }
        return merged;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (location == StandardLocation.CLASS_PATH && file instanceof ClassPathEntry entry) {
            return entry.binaryName();
        }
        return super.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        if (a instanceof ClassPathEntry first && b instanceof ClassPathEntry second) {
            return first.binaryName().equals(second.binaryName());
        }
        return super.isSameFile(a, b);
    }

    /**
     * 索引中属于指定包（或其子包）的全部 class 条目。
     *
     * @param packageName 包名，允许为 null / 空串（表示从根包递归）
     * @param recurse     是否包含子包
     * @return 命中的条目列表
     */
    private List<ClassPathEntry> indexedOf(String packageName, boolean recurse) {
        var root = packageName == null ? "" : packageName;
        if (!recurse) {
            return byPackage.getOrDefault(root, List.of());
        }
        var prefix = root.isEmpty() ? "" : root + ".";
        var result = new ArrayList<ClassPathEntry>();
        for (var grouped : byPackage.entrySet()) {
            var name = grouped.getKey();
            if (name.equals(root) || name.startsWith(prefix)) {
                result.addAll(grouped.getValue());
            }
        }
        return result;
    }
}
