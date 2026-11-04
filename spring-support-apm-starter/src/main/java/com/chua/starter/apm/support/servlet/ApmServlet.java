package com.chua.starter.apm.support.servlet;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.apm.support.web.ApmDispatcher;
import com.chua.starter.apm.support.web.ApmRequest;
import com.chua.starter.apm.support.web.ApmResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 嵌入模式的 APM 接口 Servlet。
 * <p>
 * 由 {@code ApmAutoConfiguration} 通过 {@code ServletRegistrationBean} 注册到宿主容器，
 * 随宿主 Spring 应用一起启动 —— 不占用任何 {@code @RequestMapping}，也不参与
 * DispatcherServlet 的异常处理链，因此不会和业务接口的全局异常处理器/鉴权拦截器纠缠。
 * </p>
 * <p>
 * 响应统一为 utils 层的 {@code ReturnResult}（{@code {code,data,msg}}，成功码 {@code 00000}），
 * 和仓库其它接口保持同一形状，前端可以直接复用 {@code unwrapResult}。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * JSON 内容类型
     */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final transient ApmDispatcher dispatcher;
    private final transient ObjectMapper mapper;

    /**
     * 构造 Servlet
     *
     * @param dispatcher 统一路由器
     * @param mapper     JSON 序列化器
     */
    public ApmServlet(ApmDispatcher dispatcher, ObjectMapper mapper) {
        this.dispatcher = dispatcher;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle("GET", request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle("POST", request, response);
    }

    /**
     * 处理请求
     *
     * @param method   HTTP 方法
     * @param request  请求
     * @param response 响应
     * @throws IOException 写出失败
     */
    private void handle(String method, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApmResult result;
        try {
            result = dispatcher.dispatch(new ApmRequest(method, path(request), params(request)));
        } catch (Exception e) {
            log.debug("[APM] 处理请求失败: {}", e.getMessage());
            result = ApmResult.fail("APM 请求处理失败: " + e.getMessage());
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Object body = result.ok()
                ? ReturnResult.ok(result.data())
                : ReturnResult.error(result.message());
        response.getWriter().write(mapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    /**
     * 取接口相对路径：优先 servletPath + pathInfo，避免依赖 contextPath 拼接
     *
     * @param request 请求
     * @return 路径
     */
    private String path(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.isEmpty()) {
            return pathInfo;
        }
        return request.getRequestURI();
    }

    /**
     * 取查询参数（单值）
     *
     * @param request 请求
     * @return 参数
     */
    private Map<String, String> params(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
