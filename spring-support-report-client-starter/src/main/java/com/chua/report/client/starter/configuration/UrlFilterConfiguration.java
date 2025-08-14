package com.chua.report.client.starter.configuration;

import com.chua.common.support.invoke.annotation.GetRequestLine;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.protocol.client.NitriteProtocolClient;
import com.chua.common.support.protocol.nitrite.NitriteRepository;
import com.chua.common.support.protocol.request.HttpServletResponse;
import com.chua.common.support.utils.FileUtils;
import com.chua.report.client.starter.entity.UrlCat;
import com.chua.report.client.starter.entity.UrlQuery;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * url过滤器配置类，用于注册URL过滤器
 *
 * @author CH
 * @since 2025/8/14 17:29
 */
public class UrlFilterConfiguration {
    private NitriteRepository<UrlCat> repository;
    NitriteProtocolClient nitriteProtocolClient;

    /**
     * 获取URL列表
     *
     * @param request 请求参数
     * @return 响应结果
     */
    @GetRequestLine("url")
    public com.chua.common.support.protocol.request.ServletResponse findUrl(com.chua.common.support.protocol.request.ServletRequest request) {
        UrlQuery urlsQuery = Json.fromJson(request.getBody(), UrlQuery.class);
        Date startTime = urlsQuery.getStartTime();
        Date endTime = urlsQuery.getEndTime();
        return HttpServletResponse.ok(urlsQuery(urlsQuery, startTime, endTime));
    }

    /**
     * 查询URL列表
     *
     * @param urlsQuery 查询参数
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 查询结果
     */
    private byte[] urlsQuery(UrlQuery urlsQuery, Date startTime, Date endTime) {
        if(null == startTime || null == endTime) {
            return Json.toJSONBytes(repository.query(urlsQuery.getFilterQuery()));
        }
        LocalDate start = DateUtils.toLocalDate(startTime);
        LocalDate end = DateUtils.toLocalDate(endTime);
        List<UrlCat> urlCats = new LinkedList<>();
        while (start.isBefore(end)) {
            try (NitriteProtocolClient nitriteProtocolClient1 = new NitriteProtocolClient(
                    ClientSetting.builder()
                            .path(nitriteProtocolClient.getCurrentPath() + "/nitrite" + start.toString() + ".db")
                            .build())) {
                nitriteProtocolClient1.connect();
                nitriteProtocolClient1.registerEntityConverter(new UrlCat());
                NitriteRepository<UrlCat> client1Repository = nitriteProtocolClient1.createRepository(UrlCat.class);
                List<UrlCat> query = client1Repository.query(urlsQuery.getFilterQuery());
                if(null == query) {
                    continue;
                }
                urlCats.addAll(query);
            } catch (Exception ignored) {
            }
        }
        return Json.toJSONBytes(urlCats);
    }

    /**
     * 创建并配置URL过滤器的注册Bean
     *
     * @return FilterRegistrationBean<UrlFilter> 返回配置好的过滤器注册Bean
     * 示例：该Bean会注册一个UrlFilter，拦截所有请求路径("/*")，支持异步处理
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<UrlFilter> urlFilter() {
        FilterRegistrationBean<UrlFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new UrlFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setAsyncSupported(true);
        registrationBean.setName("urlFilter");
        return registrationBean;
    }


    /**
     * URL过滤器内部类，实现Filter接口
     * 用于对请求进行过滤处理
     */
    public class UrlFilter implements Filter {

        private static final ExecutorService PER_TASK_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
        /**
         * 过滤器核心方法，处理请求和响应
         *
         * @param request  ServletRequest对象，表示客户端的请求
         *                 示例：可以通过request.getParameter("name")获取请求参数
         * @param response ServletResponse对象，用于向客户端发送响应
         *                 示例：可以通过response.getWriter().write("Hello")向客户端输出内容
         * @param chain    FilterChain对象，表示过滤器链，用于调用下一个过滤器或目标资源
         *                 示例：chain.doFilter(request, response) 继续执行过滤器链
         * @throws IOException      当发生I/O错误时抛出
         * @throws ServletException 当发生Servlet相关的错误时抛出
         */
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            if (request instanceof HttpServletRequest httpServletRequest) {
                check();
                long startTime = System.currentTimeMillis();
                ContentCachingRequestWrapper contentCachingRequestWrapper = new ContentCachingRequestWrapper(httpServletRequest);
                chain.doFilter(contentCachingRequestWrapper, response);
                long endTime = System.currentTimeMillis();

                PER_TASK_EXECUTOR.execute(() -> {
                    String contentAsString = contentCachingRequestWrapper.getContentAsString();
                    repository.insert(new UrlCat(
                            httpServletRequest.getMethod(),
                            httpServletRequest.getRequestURI(),
                            httpServletRequest.getRemoteAddr(),
                            endTime - startTime, contentAsString));
                });
                return;
            }
            chain.doFilter(request, response);
        }

        /**
         * 检查当前请求是否需要过滤
         */
        private void check() {
            String file = "nitrite" + DateUtils.currentDateString() + ".db";
            String path = "./data/" + file;
            File file1 = new File(path);
            FileUtils.forceMkdirParent(file1);
            if (null == nitriteProtocolClient) {
                nitriteProtocolClient = new NitriteProtocolClient(
                        ClientSetting.builder()
                                .path(file1.getAbsolutePath())
                                .build()
                );
                nitriteProtocolClient.connect();
                nitriteProtocolClient.registerEntityConverter(new UrlCat());
                repository = nitriteProtocolClient.createRepository(UrlCat.class);
            }

            if (!nitriteProtocolClient.isCurrentFile(file)) {
                try {
                    nitriteProtocolClient.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                nitriteProtocolClient = null;
                check();
            }
        }
    }
}
