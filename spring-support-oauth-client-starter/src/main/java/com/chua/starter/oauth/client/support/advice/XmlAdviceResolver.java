package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.file.xml.XML;
import com.chua.starter.common.support.result.ReturnResult;
import org.springframework.http.MediaType;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * json
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
public class XmlAdviceResolver implements AdviceResolver {

    @Override
    public String type() {
        return MediaType.TEXT_XML_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) {

        try {
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(XML.toString(ReturnResult.newBuilder().code(ResultCode.transferHttpCode(status)).msg(message).build()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
