package com.chua.starter.job.support.glue.compile;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * 类路径上的一个 {@code .class} 文件，对 {@code javac} 呈现为只读输入。
 *
 * <p>刻意不缓存字节流：懒打开能省下 fat jar 启动时一次性读全量 class 的开销。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class ClassPathEntry extends SimpleJavaFileObject {

    /**
     * 字节来源（目录下的文件，或 jar 内条目）
     */
    private final ByteSource source;

    /**
     * 二进制类名（点分）
     */
    private final String binaryName;

    /**
     * 构造 类路径条目。
     *
     * @param binaryName 二进制类名（点分）
     * @param uri        合成 URI，须在类路径内唯一
     * @param source     字节来源
     */
    ClassPathEntry(String binaryName, URI uri, ByteSource source) {
        super(uri, Kind.CLASS);
        this.binaryName = binaryName;
        this.source = source;
    }

    /**
     * 获取二进制类名。
     *
     * @return 二进制类名（点分）
     */
    String binaryName() {
        return binaryName;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return source.open();
    }

    /**
     * {@link JavaFileObject#getName()} 覆写为二进制类名，
     * 供 {@code inferBinaryName} 与去重使用。
     *
     * @return 二进制类名（点分）
     */
    @Override
    public String getName() {
        return binaryName;
    }

    /**
     * 类路径上的 class 没有可靠的修改时间语义，固定 0 让 javac 走“无需重新检查”。
     *
     * @return 固定 0
     */
    @Override
    public long getLastModified() {
        return 0L;
    }

    /**
     * 类文件字节来源。
     */
    @FunctionalInterface
    interface ByteSource {

        /**
         * 打开字节流，调用方负责关闭。
         *
         * @return 字节流
         * @throws IOException 打开失败
         */
        InputStream open() throws IOException;
    }
}
