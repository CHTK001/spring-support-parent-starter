package com.chua.starter.oauth.client.support.advice;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.result.ReturnXmlResult;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

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
        OutputStream os = null;
        if (null == response) {
            os = new ByteArrayOutputStream();
        } else {
            response.setStatus(status);
            response.setHeader("Content-Type", type());
            try {
                os = response.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ReturnXmlResult.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(status.equals(403) ? ReturnResult.noAuth().toString() : ReturnResult.newBuilder().code(status).msg(message).build(), os);
        } catch (JAXBException e) {
            e.printStackTrace();
        }

        return null;
    }
}
