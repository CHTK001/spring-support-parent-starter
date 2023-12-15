package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.http.MediaType;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

/**
 * xml
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
public class XmlAdviceResolver implements AdviceResolver {

    static final XmlMapper XML_MAPPER = new XmlMapper();

    @Override
    public String type() {
        return MediaType.TEXT_XML_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) {

        try {
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(XML_MAPPER.writeValueAsBytes(ReturnResult.newBuilder().code(ResultCode.transferForHttpCodeStatus(status)).msg(message).build()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
