package com.chua.starter.common.support.view;

import com.chua.common.support.view.ViewConfig;
import com.chua.starter.common.support.properties.ViewProperties;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * 预览
 *
 * @author CH
 * @since 2022/8/1 17:01
 */
@Slf4j
@ResponseBody
@Controller
public class ViewProvider implements InitializingBean, DisposableBean {

    @Resource
    private ViewProperties viewProperties;
    private static final String DOWNLOAD = "download";

    /**
     * 文件保存
     *
     * @param bucket bucket
     * @param file   文件
     * @return -1: bucket不存在
     */
    @GetMapping("upload")
    public Throwable saveOss(String bucket, MultipartFile file) {
        ViewConfig configuration = viewProperties.getConfig().get(bucket);
        if (null == configuration) {
            return new IllegalArgumentException();
        }

        ViewFactory viewFactory = new ViewFactory(viewProperties, bucket);
        try {
            viewFactory.save(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            return e;
        }

        return null;
    }


    /**
     * 文件预览
     *
     * @param path    文件路径
     * @param mode    预览模式
     * @param request 响应
     */
    @GetMapping(value = "/preview")
    public void previewView(@RequestParam("bucket") String bucket,
                            @RequestParam("path") String path,
                            @RequestParam(value = "mode", required = false) String mode,
                            HttpServletRequest request) throws IOException {
        preview(bucket, path, mode, request);
    }

    /**
     * 文件预览
     *
     * @param path    文件路径
     * @param mode    预览模式
     * @param request 响应
     */
    @GetMapping(value = "/preview/{bucket}/{path}/**")
    public void preview(@PathVariable("bucket") String bucket,
                        @PathVariable("path") String path,
                        @RequestParam(value = "mode", required = false) String mode,
                        HttpServletRequest request) throws IOException {

        if (Strings.isNullOrEmpty(mode) && DOWNLOAD.equalsIgnoreCase(request.getQueryString())) {
            mode = DOWNLOAD;
        }

        String uri = request.getRequestURI();
        path = uri.substring(uri.indexOf(bucket) + bucket.length());
        path = URLDecoder.decode(path, "UTF-8");

        ViewFactory viewFactory = null;
        try {
            viewFactory = new ViewFactory(viewProperties, bucket);
        } catch (Exception e) {
            ResponseHandler.notFound().build();
            return;
        }
        ViewFactory.ViewMeta preview = viewFactory.preview(path, mode);

        if (preview.isFailure()) {
            ResponseHandler.notFound().build();
            return;
        }

        ResponseHandler<byte[]> handler = ResponseHandler.ok();
        if (preview.isDownload()) {
            handler.header("Content-Disposition", "attachment;filename=" + URLEncoder.encode(path, "UTF-8"));
        }

        handler.contentLength(preview.length())
                .contentType(MediaType.parseMediaType(preview.mediaType()))
                .body(preview.getToByteArray()).build();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("开启文件预览服务");
    }

    @Override
    public void destroy() throws Exception {

    }
}
