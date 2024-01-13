package com.chua.starter.common.support.view;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.media.MediaType;
import com.chua.common.support.oss.entity.GetResult;
import com.chua.common.support.oss.view.ViewResult;
import com.chua.common.support.oss.view.Viewer;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.RequestUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Java语言剧本视图解析器
 *
 * @author CH
 */
@Spi({ "xml"})
public class XmlViewer implements Viewer {
    @Override
    public ViewResult resolve(GetResult getResult) {
        int port = SpringBeanUtils.getPort();
        String requestURI = RequestUtils.getRequest().getRequestURI();
        String contextPath = SpringBeanUtils.getContextPath();
        try {
            String html = IoUtils.toString(new URL("http://localhost:" + port  + contextPath +
                    "/xml.html").toURI());

            html = html.replace("./assets",  contextPath + "/storage");
            html += "<input style=\"display:none;\" id='fileId' value='"+ Base64.getEncoder().encodeToString(
                    requestURI.replace("/preview/", "/download/").getBytes(StandardCharsets.UTF_8))+ "' ></input>";
            return new ViewResult(
                    MediaType.create("text", "html"),
                    html.getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
