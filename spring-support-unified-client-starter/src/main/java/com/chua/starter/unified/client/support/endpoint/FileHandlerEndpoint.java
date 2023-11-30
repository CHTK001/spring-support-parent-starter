package com.chua.starter.unified.client.support.endpoint;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.chua.common.support.utils.ClassUtils;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.lang.reflect.Method;

import static com.chua.common.support.constant.CommonConstant.EMPTY_JSON;

/**
 * 文件处理程序终结点
 *
 * @author CH
 */
@WebEndpoint(id = "file-handler")
public class FileHandlerEndpoint {

    Class<?> type = ClassUtils.forName("com.chua.attach.hotspot.core.support.plugin.handler.Listener");
    Method method;


    public FileHandlerEndpoint() {
        if(null == type) {
            return;
        }
        try {
            Method typeMethod = type.getMethod("dump");
            typeMethod.setAccessible(true);
            method = typeMethod;
        } catch (NoSuchMethodException ignored) {
        }
    }

    /**
     * 热加载
     *
     * @return 结果
     */
    @ReadOperation
    public String list() {
        if(null == method) {
            return EMPTY_JSON;
        }

        try {
            return new JSONObject().fluentPut("data", method.invoke(null).toString()).toJSONString(JSONWriter.Feature.LargeObject);
        } catch (Exception ignored) {
        }
        return EMPTY_JSON;
    }
}
