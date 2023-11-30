package com.chua.starter.unified.client.support.endpoint;

import com.chua.common.support.utils.ClassUtils;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

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
    public Collection<String> list() {
        if(null == method) {
            return Collections.emptyList();
        }

        try {
            return Collections.singletonList(method.invoke(null).toString());
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}
