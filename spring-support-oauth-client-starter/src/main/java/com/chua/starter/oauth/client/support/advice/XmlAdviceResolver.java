package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.starter.common.support.xml.Xml;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.OutputStream;

/**
* xml
*
* @since 2022/7/29 10:24
* @author CH
 */
@Slf4j
public class XmlAdviceResolver implements AdviceResolver {


    @Override
    /** 类型 */
    public String type() {
        return MediaType.TEXT_XML_VALUE;
    }

    @Override
    /** 解析 */
    public Object resolve(HttpServletResponse response, Integer status, String message) {
        /**
        * 尝试。
        * @param response.getOutputStream( 响应.获取输出流(
         */
        try (OutputStream writer = response.getOutputStream()) {
            writer.write(Xml.toXmlByte(JsonObject.create().fluent("code", ResultCode.transferForHttpCodeStatus(status))));
        } catch (Exception e) {
            log.error("", e);
        }

        return null;
    }
}