package com.chua.starter.datasourcehub.support.dbconsole;

import java.io.IOException;
import java.util.List;

/**
 * Excel 导入入口（POI 可用性探测 + 委派）。
 *
 * <p>POI 在 hub 模块里是 {@code optional} 依赖：宿主没装配时 classpath 上没有
 * {@code org.apache.poi.*}，此时 {@link #available()} 返回 false，调用方据此把
 * “Excel”选项换成「当前实例未装配 Excel 解析能力」，而不是给一个点了就
 * {@code NoClassDefFoundError} 的按钮。</p>
 *
 * <p>真正的 POI 调用全在 {@link DbExcelSheetReader} 里。本类**不能**出现任何
 * {@code org.apache.poi} 的类型引用（连 import 都不行）：JVM 解析 {@code DbImportExcel}
 * 的方法签名／字段类型时才去加载被引用的类，方法体里的引用是惰性的 ——
 * 把 POI 类型挡在另一个类的签名之外，才能保证「没有 POI 也能安全加载本类」。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class DbImportExcel {

    /** 探测结果缓存：classpath 在一次运行内不会变，反复 {@code Class.forName} 无意义 */
    private static volatile Boolean available;

    private DbImportExcel() {
    }

    /**
     * 当前实例是否装配了 Excel 解析能力。
     *
     * @return true=POI 在 classpath 上
     */
    public static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean detected;
        try {
            // WorkbookFactory 是 POI 的入口类；用它的存在代表「能读 xls/xlsx」
            Class.forName("org.apache.poi.ss.usermodel.WorkbookFactory", false,
                    DbImportExcel.class.getClassLoader());
            detected = true;
        } catch (ClassNotFoundException | LinkageError e) {
            detected = false;
        }
        available = detected;
        return detected;
    }

    /**
     * 列出工作簿的工作表名。
     *
     * @param bytes Excel 字节
     * @return 工作表名
     * @throws IOException 文件损坏或 POI 不可用
     */
    public static List<String> sheetNames(byte[] bytes) throws IOException {
        requireAvailable();
        return DbExcelSheetReader.sheetNames(bytes);
    }

    /**
     * 读取整张工作表为二维文本。
     *
     * @param bytes      Excel 字节
     * @param sheetName  工作表名；空取第一张
     * @return 行 × 列文本矩阵
     * @throws IOException 文件损坏、工作表不存在或 POI 不可用
     */
    public static List<List<String>> read(byte[] bytes, String sheetName) throws IOException {
        requireAvailable();
        return DbExcelSheetReader.read(bytes, sheetName);
    }

    /**
     * require Available
     * @return 返回值
     * @throws IOException 异常
    */
    private static void requireAvailable() throws IOException {
        if (!available()) {
            throw new IOException("当前实例未装配 Excel 解析能力（需要 classpath 上有 Apache POI），"
                    + "请改用 CSV 导入，或让宿主引入 utils-support-word-starter / poi-ooxml");
        }
    }
}
