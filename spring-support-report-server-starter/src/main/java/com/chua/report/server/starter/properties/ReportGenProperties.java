package com.chua.report.server.starter.properties;

import com.chua.common.support.utils.FileUtils;
import com.chua.report.server.starter.entity.MonitorSysGen;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

/**
 * gen配置
 * @author CH
 * @since 2024/9/26
 */
@Data
@ConfigurationProperties(prefix = ReportGenProperties.PRE, ignoreInvalidFields = true)
public class ReportGenProperties {

    public static final String PRE = "plugin.report.server.gen";
    /**
     * 生成包路径
     */
    private String packageName;
    /**
     * 作者
     */
    private String author;

    /**
     * 临时路径
     */
    private String tempPath = "gen";
    /**
     * 模板路径
     */

    private String templatePath;

    /**
     * 获取临时路径。
     * 为每个系统生成器在临时目录中创建一个特定的目录，用于存储数据库文件。
     *
     * @param sysGen 监控系统生成器对象，用于获取生成器的唯一标识。
     * @return 返回创建的目录对象，表示数据库文件的临时存储路径。
     */
    public File getTempPathForTemplate(MonitorSysGen sysGen, String type) {
        return FileUtils.mkdir(new File(getTempPath(), sysGen.getGenId() + "/" + type));
    }
    /**
     * 获取数据库文件的临时路径。
     * 为每个系统生成器在临时目录中创建一个特定的目录，用于存储数据库文件。
     *
     * @param sysGen 监控系统生成器对象，用于获取生成器的唯一标识。
     * @return 返回创建的目录对象，表示数据库文件的临时存储路径。
     */
    public File getTempPathForDatabaseFile(MonitorSysGen sysGen) {
        return getTempPathForTemplate(sysGen, "data");
    }

    /**
     * 获取驱动程序的临时路径。
     * 为每个系统生成器在临时目录中创建一个特定的目录，用于存储驱动程序。
     *
     * @param sysGen 监控系统生成器对象，用于获取生成器的唯一标识。
     * @return 返回创建的目录对象，表示驱动程序的临时存储路径。
     */
    public File getTempPathForDriver(MonitorSysGen sysGen) {
        return getTempPathForTemplate(sysGen, "driver");
    }

    /**
     * 获取文档的临时路径。
     * 为每个系统生成器在临时目录中创建一个特定的目录，用于存储文档。
     *
     * @param sysGen 监控系统生成器对象，用于获取生成器的唯一标识。
     * @return 返回创建的目录对象，表示文档的临时存储路径。
     */
    public File getTempPathForDoc(MonitorSysGen sysGen) {
        return getTempPathForTemplate(sysGen, "doc");
    }

    /**
     * 刷新数据库文件路径。
     *
     * @param sysGen 监控系统生成器对象，用于获取生成器的唯一标识。
     * @param url
     */
    public void register(MonitorSysGen sysGen, String type, String url) {
        if("data".equalsIgnoreCase(type)) {
            sysGen.setGenDatabaseFile(url);
            return ;
        }

        if("driver".equalsIgnoreCase(type)) {
            sysGen.setGenDriverFile(url);
        }
    }
    public void refresh(MonitorSysGen sysGen, String type) {
        if("data".equalsIgnoreCase(type)) {
            sysGen.setGenDatabaseFile("");
            return ;
        }

        if("driver".equalsIgnoreCase(type)) {
            sysGen.setGenDriverFile("");
        }
    }
}
