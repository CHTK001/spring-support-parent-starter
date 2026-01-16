package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.xml.Xml;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.OutputStream;

/**
 * xml
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
@Slf4j
public class XmlAdviceResolver implements AdviceResolver {


    @Override
    public String type() {
        return MediaType.TEXT_XML_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) {
        try (OutputStream writer = response.getOutputStream()) {
            writer.write(Xml.toXmlByte(JsonObject.create().fluent("code", ResultCode.transferForHttpCodeStatus(status))));
        } catch (Exception e) {
            log.error("", e);
        }

        return null;
    }
}
