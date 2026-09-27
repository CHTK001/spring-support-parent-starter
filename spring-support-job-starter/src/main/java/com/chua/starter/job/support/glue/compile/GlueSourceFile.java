package com.chua.starter.job.support.glue.compile;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

/**
 * 内存中的 Java 源码文件，供 {@code javac} 读取。
 *
 * <p>不落盘：源码只存在于任务台账里，编译单元交给 {@code JavaFileManager} 即可。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class GlueSourceFile extends SimpleJavaFileObject {

    /**
     * 源码正文
     */
    private final String source;

    /**
     * 构造 内存源码文件。
     *
     * @param binaryName 主类二进制类名（点分）
     * @param source     源码正文
     */
    GlueSourceFile(String binaryName, String source) {
        super(URI.create("string:///" + binaryName.replace('.', '/') + Kind.SOURCE.extension),
                Kind.SOURCE);
        this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return source;
    }
}
