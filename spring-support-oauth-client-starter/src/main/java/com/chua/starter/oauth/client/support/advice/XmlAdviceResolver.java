package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.file.config.WriterSetting;
import com.chua.common.support.lang.file.impl.writer.XmlFileWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.OutputStream;
import java.util.Collections;

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

        try (OutputStream writer = response.getOutputStream();
             XmlFileWriter xmlFileWriter = new XmlFileWriter(
                     Collections.emptyList(),
                     writer, WriterSetting.newDefault()
             )) {
            xmlFileWriter.writeJson(Json.getJsonObject(Json.toJson(ReturnResult.newBuilder().code(ResultCode.transferForHttpCodeStatus(status)))));
        } catch (Exception e) {
            log.error("", e);
        }

        return null;
    }
}
