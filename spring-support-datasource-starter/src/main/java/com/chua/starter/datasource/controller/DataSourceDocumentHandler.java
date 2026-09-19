package com.chua.starter.datasource.controller;

import com.chua.common.support.reflection.ReflectUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 文档控制器注册器
 * <p>
 * 通过反射调用 {@link DataSourceDocumentController} 的方法，将 URL 路径映射到对应的方法。
 * 不使用 {@code @RequestMapping} 注解，避免在注解处理器扫描时引入硬编码路径（路径由配置决定）。
 * </p>
 *
 * <h3>路径规则（以 basePath=/plugin/datasource/document 为例）：</h3>
 * <pre>
 * GET  /preview             -> preview()
 * POST /apply               -> apply()
 * GET  /list                -> list()
 * GET  /table/{name}        -> table(name)
 * GET  /relationship.json   -> relationshipJson()
 * GET  /graph.json          -> graphJson()
 * GET  /graph.html          -> graphHtml()
 * GET  /graph/table/{name}.json -> graphTable(name)
 * POST /graph/apply         -> graphApply()
 * </pre>
 *
 * @since 2026/8/5
 * @author CH
 */
@Slf4j
public class DataSourceDocumentHandler {

    /**
     * 控制器
     */
    private final DataSourceDocumentController controller;

    /**
     * 基础路径
     */
    private final String basePath;

    /**
     * 数据源文档处理器。
     * @param controller 控制器
     * @param basePath 基础路径
     */
    public DataSourceDocumentHandler(DataSourceDocumentController controller, String basePath) {
        this.controller = controller;
        this.basePath = trimTrailingSlash(basePath);
    }

    /**
     * 注册到 Spring MVC 路径映射
     * @param mapping mapping
     */
    public void register(RequestMappingHandlerMapping mapping) {
        registerPath(mapping, "GET", "/preview", "preview", HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "POST" "POST"
         * @param "/apply" "/apply"
         * @param "apply" "apply"
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "POST", "/apply", "apply", HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/list" "/列表"
         * @param "list" "列表"
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/list", "list", HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/table/{name}" "/table/{名称}"
         * @param "table" "table"
         * @param HttpServletRequest.class httpservlet请求.类
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/table/{name}", "table", HttpServletRequest.class, HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/relationship.json" "/relationship.json"
         * @param "relationshipJson" "relationshipjson"
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/relationship.json", "relationshipJson", HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/graph.json" "/图计算.json"
         * @param "graphJson" "图计算json"
         * @param HttpServletRequest.class httpservlet请求.类
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/graph.json", "graphJson", HttpServletRequest.class, HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/graph.html" "/图计算.HTML"
         * @param "graphHtml" "图计算HTML"
         * @param HttpServletRequest.class httpservlet请求.类
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/graph.html", "graphHtml", HttpServletRequest.class, HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "GET" "获取"
         * @param "/graph/table/{name}.json" "/图计算/table/{名称}.json"
         * @param "graphTable" "图计算table"
         * @param HttpServletRequest.class httpservlet请求.类
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "GET", "/graph/table/{name}.json", "graphTable", HttpServletRequest.class, HttpServletResponse.class);
        /**
         * 注册路径。
         * @param mapping mapping
         * @param "POST" "POST"
         * @param "/graph/apply" "/图计算/apply"
         * @param "graphApply" "图计算apply"
         * @param HttpServletResponse.class httpservlet响应.类
         */
        registerPath(mapping, "POST", "/graph/apply", "graphApply", HttpServletResponse.class);
        log.info("[spring-support-datasource] 数据库文档预览与图谱已注册: basePath={}", basePath);
    }

    private void registerPath(RequestMappingHandlerMapping mapping,
                              String method, String path, String handlerMethod,
                              Class<?>... paramTypes) {
        Method m = ReflectUtils.findMethod(DataSourceDocumentController.class, handlerMethod, paramTypes);
        if (m == null) {
            log.error("注册文档路径失败: {} {} -> {} 未找到处理方法", method, path, handlerMethod);
            return;
        }
        RequestMappingInfo info = RequestMappingInfo.paths(basePath + path)
                .methods(org.springframework.web.bind.annotation.RequestMethod.valueOf(method))
                .build();
        mapping.registerMapping(info, controller, m);
    }

    /**
     * 修剪trailingslash。
     * @param s s
     * @return 修剪trailingslash的结果
     */
    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
