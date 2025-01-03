package com.chua.starter.pay.support.configuration;

import com.chua.starter.pay.support.annotations.OnPayListener;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import io.micrometer.common.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听工厂
 * @author CH
 * @since 2024/12/31
 */
public class PayListenerService {

    private final Map<String, ListenerBean> originListener = new ConcurrentHashMap<>();
    public void addListener(OnPayListener onPayListener, Object bean, Method method) {
        ReflectionUtils.makeAccessible(method);
        originListener.put(onPayListener.value(), new ListenerBean(bean, onPayListener, method));
    }

    public void listen(PayMerchantOrder order) {
        String payMerchantOrderOrigin = order.getPayMerchantOrderOrigin();
        if(StringUtils.isBlank(payMerchantOrderOrigin)) {
            return;
        }

        ListenerBean listenerBean = originListener.get(payMerchantOrderOrigin);
        if(null == listenerBean) {
            return;
        }

        listenerBean.notifyOrder(order);
    }

    @Data
    @AllArgsConstructor
    static class ListenerBean {
        private Object bean;
        private OnPayListener onPayListener;
        private Method method;

        /**
         * 通知
         * @param order 订单
         */
        public void notifyOrder(PayMerchantOrder order) {
            try {
                method.invoke(bean, order);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}
