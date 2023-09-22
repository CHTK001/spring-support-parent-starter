package com.chua.starter.gen.support.util;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.velocity.VelocityContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 设置模板变量信息
     *
     * @return 模板列表
     */
    public static VelocityContext prepareContext(SysGenTable genTable, List<SysGenColumn> sysGenColumns) {
        String moduleName = genTable.getTabModuleName();
        String businessName = genTable.getTabBusinessName();
        String packageName = genTable.getTabPackageName();
        String tplCategory = genTable.getTabTplCategory();
        String functionName = genTable.getTabFunctionName();

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("tplCategory", genTable.getTabTplCategory());
        velocityContext.put("tableName", genTable.getTabName());
        velocityContext.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        velocityContext.put("ClassName", genTable.getTabClassName());
        velocityContext.put("className", StringUtils.uncapitalize(genTable.getTabClassName()));
        velocityContext.put("moduleName", genTable.getTabModuleName());
        velocityContext.put("BusinessName", StringUtils.capitalize(genTable.getTabBusinessName()));
        velocityContext.put("businessName", genTable.getTabBusinessName());
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", genTable.getTabFunctionAuthor());
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
    public static List<String> getTemplateList(String tplCategory) {
        List<String> templates = new ArrayList<String>();
        templates.add("vm/java/entity.java.vm");
        templates.add("vm/java/mapper.java.vm");
        templates.add("vm/java/service.java.vm");
        templates.add("vm/java/serviceImpl.java.vm");
        templates.add("vm/java/controller.java.vm");
        templates.add("vm/xml/mapper.xml.vm");
        templates.add("vm/sql/sql.vm");
        templates.add("vm/js/api.js.vm");
        templates.add("vm/vue3/api.ts.vm");
        return templates;
    }

    /**
     * 获取文件名
     */
    public static String getFileName(String template, SysGenTable genTable) {
        // 文件名称
        String fileName = "";
        // 包路径
        String packageName = genTable.getTabPackageName();
        // 模块名
        String moduleName = genTable.getTabModuleName();
        // 大写类名
        String className = genTable.getTabClassName();
        // 业务名称
        String businessName = genTable.getTabBusinessName();

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        String vuePath = "vue";

        if (template.contains("entity.java.vm")) {
            fileName = StringUtils.format("{}/entity/{}Entity.java", javaPath, className);
        }
        if (template.contains("sub-domain.java.vm") && StringUtils.equals(TPL_SUB, genTable.getTabTplCategory())) {
            fileName = StringUtils.format("{}/entity/{}Entity.java", javaPath, genTable.getTabClassName());
        } else if (template.contains("mapper.java.vm")) {
            fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
        } else if (template.contains("service.java.vm")) {
            fileName = StringUtils.format("{}/service/{}Service.java", javaPath, className);
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
    public static Set<String> getImportList(List<SysGenColumn> columns) {
        Set<String> importList = new HashSet<>();
        for (SysGenColumn column : columns) {
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
