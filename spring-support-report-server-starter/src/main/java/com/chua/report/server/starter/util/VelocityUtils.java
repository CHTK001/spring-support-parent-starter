package com.chua.report.server.starter.util;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorSysGenColumn;
import com.chua.report.server.starter.entity.MonitorSysGenTable;
import com.chua.report.server.starter.entity.MonitorSysGenTemplate;
import com.chua.report.server.starter.query.Download;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.velocity.VelocityContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.chua.common.support.constant.CommonConstant.SYMBOL_EMPTY;
import static com.chua.common.support.constant.NameConstant.*;

/**
 * 模板处理工具类
 *
 * @author ruoyi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VelocityUtils {

    /**
     * 项目空间路径
     */
    private static final String PROJECT_PATH = "main/java";

    /**
     * mybatis空间路径
     */
    private static final String MYBATIS_PATH = "main/resources/mapper";

    /**
     * 默认上级菜单，系统工具
     */
    private static final String DEFAULT_PARENT_MENU_ID = "3";
    private static final String VUE = "vue";

    /**
     * 设置模板变量信息
     *
     * @return 模板列表
     */
    public static VelocityContext prepareContext(MonitorSysGenTable genTable, List<MonitorSysGenColumn> sysGenColumns, Download download) {
        String moduleName = StringUtils.defaultString(download.getModuleName(), SYMBOL_EMPTY);
        String businessName = genTable.getTabBusinessName();
        String packageName = StringUtils.defaultString(download.getPackageName(), genTable.getTabPackageName());
        String tplCategory = genTable.getTabTplCategory();
        String functionName = StringUtils.defaultString(download.getFunctionName(), genTable.getTabFunctionName());

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("openSwagger", download.getOpenSwagger());
        velocityContext.put("tplCategory", genTable.getTabTplCategory());
        velocityContext.put("tableName", genTable.getTabName());
        velocityContext.put("version", download.getVersion());
        velocityContext.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        velocityContext.put("ClassName", genTable.getTabClassName());
        velocityContext.put("className", StringUtils.uncapitalize(genTable.getTabClassName()));
        velocityContext.put("moduleName", genTable.getTabModuleName());
        velocityContext.put("BusinessName", StringUtils.capitalize(genTable.getTabBusinessName()));
        velocityContext.put("businessName", genTable.getTabBusinessName());
        velocityContext.put("Entity", genTable.getTabClassName());
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", download.getAuthor());
        velocityContext.put("datetime", DateTime.now().toStandard());
        velocityContext.put("pkColumn", genTable.getTabPkColumn());
        velocityContext.put("importList", getImportList(sysGenColumns));
        velocityContext.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        velocityContext.put("columns", sysGenColumns);
        velocityContext.put("table", genTable);
        return velocityContext;
    }

    /**
     * 获取模板信息
     *
     * @return 模板列表
     */
    public static List<String> getTemplateList(String templatePath) {
        List<String> templates = new ArrayList<String>();
        if (StringUtils.isEmpty(templatePath)) {
            try {
                PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = pathMatchingResourcePatternResolver.getResources("classpath:vm/**/*.vm");
                for (Resource resource : resources) {
                    templates.add(resource.getURL().toExternalForm());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return templates;
        }

        try {
            Files.walkFileTree(Paths.get(templatePath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    File file1 = file.toFile();
                    boolean endsWith = file1.getName().endsWith(".vm");
                    if(endsWith) {
                        templates.add(file1.toURI().toURL().toExternalForm());
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return templates;
    }

    /**
     * 获取文件名
     */
    public static String getFileName(MonitorSysGenTable genTable, Download download, MonitorSysGenTemplate sysGenTemplate) {
        // 文件名称
        String fileName = "";
        String moduleName =  StringUtils.defaultString(download.getModuleName(), SYMBOL_EMPTY);
        String packageName = StringUtils.defaultString(download.getPackageName(), genTable.getTabPackageName());
        // 大写类名
        String className = genTable.getTabClassName();
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        String vuePath = "vue";

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        if(StringUtils.isEmpty(sysGenTemplate.getTemplatePath()) || !sysGenTemplate.getTemplatePath().contains("{}")) {
            return fileName;
        }

        String path = javaPath;
        if(XML.equalsIgnoreCase(sysGenTemplate.getTemplateType())) {
            path = mybatisPath;
        }
        if(VUE.equalsIgnoreCase(sysGenTemplate.getTemplateType())) {
            path = vuePath;
        }
        return FileUtils.normalize(StringUtils.format(sysGenTemplate.getTemplatePath(), path, className));
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, MonitorSysGenTable genTable, Download download) {
        // 文件名称
        String fileName = "";
        String moduleName =  StringUtils.defaultString(download.getModuleName(), SYMBOL_EMPTY);
        String businessName = genTable.getTabBusinessName();
        String packageName = StringUtils.defaultString(download.getPackageName(), genTable.getTabPackageName());
        String tplCategory = genTable.getTabTplCategory();
        String functionName = StringUtils.defaultString(download.getFunctionName(), genTable.getTabFunctionName());
        // 大写类名
        String className = genTable.getTabClassName();

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        String vuePath = "vue";

        if (template.contains("entity.java.vm")) {
            fileName = StringUtils.format("{}/entity/{}.java", javaPath, className);
        }
        if (template.contains("sub-domain.java.vm") && StringUtils.equals(TPL_SUB, genTable.getTabTplCategory())) {
            fileName = StringUtils.format("{}/entity/{}.java", javaPath, genTable.getTabClassName());
        } else if (template.contains("mapper.java.vm")) {
            fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
        } else if (template.contains("service.java.vm")) {
            fileName = StringUtils.format("{}/service/{}Service.java", javaPath, className);
        }else if (template.endsWith("query.java.vm")) {
            fileName = StringUtils.format("{}/query/PageQuery.java", javaPath);
        } else if (template.contains("serviceImpl.java.vm")) {
            fileName = StringUtils.format("{}/service/impl/{}ServiceImpl.java", javaPath, className);
        } else if (template.contains("controller.java.vm")) {
            fileName = StringUtils.format("{}/controller/{}Controller.java", javaPath, className);
        } else if (template.contains("mapper.xml.vm")) {
            fileName = StringUtils.format("{}/{}Mapper.xml", mybatisPath, className);
        } else if (template.contains("sql.vm")) {
            fileName = businessName + "Menu.sql";
        } else if (template.contains("api.js.vm")) {
            fileName = StringUtils.format("{}/api/{}/{}.js", vuePath, moduleName, businessName);
        } else if (template.contains("index.vue.vm")) {
            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
        } else if (template.contains("index-tree.vue.vm")) {
            fileName = StringUtils.format("{}/views/{}/{}/index.vue", vuePath, moduleName, businessName);
        }
        return fileName;
    }

    /**
     * 获取包前缀
     *
     * @param packageName 包名称
     * @return 包前缀名称
     */
    public static String getPackagePrefix(String packageName) {
        packageName = StringUtils.defaultString(packageName, "");
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtils.substring(packageName, 0, lastIndex);
    }

    /**
     * 获取导入列表
     * 根据列类型获取导入包
     *
     * @param columns 列
     * @return 返回需要导入的包列表
     */
    public static Set<String> getImportList(List<MonitorSysGenColumn> columns) {
        Set<String> importList = new HashSet<>();
        for (MonitorSysGenColumn column : columns) {
            if (TYPE_DATE.equals(column.getColJavaType())) {
                importList.add("java.util.Date");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
            } else if (TYPE_BIGDECIMAL.equals(column.getColJavaType())) {
                importList.add("java.math.BigDecimal");
            }
        }
        return importList;
    }


    /**
     * 获取权限前缀
     *
     * @param moduleName   模块名称
     * @param businessName 业务名称
     * @return 返回权限前缀
     */
    public static String getPermissionPrefix(String moduleName, String businessName) {
        return StringUtils.format("{}:{}", moduleName, businessName);
    }

}
