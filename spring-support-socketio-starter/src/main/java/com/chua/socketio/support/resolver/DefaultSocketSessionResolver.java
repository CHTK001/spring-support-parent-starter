package com.chua.socketio.support.resolver;

import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.annotations.OnConnect;
import com.chua.socketio.support.annotations.OnEvent;
import com.chua.socketio.support.properties.SocketIoProperties;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.session.SocketSession;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.listener.DataListener;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.List;

/**
 * @author CH
 * @version 1.0.0
 * @since 2024/01/18
 */
public class DefaultSocketSessionResolver implements SocketSessionResolver{
    private final SocketIoProperties socketIoProperties;
    private List<SocketIOListener> listenerList;
    private final List<SocketInfo> connect = new LinkedList<>();
    private final List<SocketInfo> disconnect = new LinkedList<>();
    private final List<SocketInfo> data = new LinkedList<>();

    private static ObjectMapper objectMapper = new ObjectMapper();

    public DefaultSocketSessionResolver(List<SocketIOListener> listenerList, SocketIoProperties socketIoProperties) {
        this.socketIoProperties = socketIoProperties;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.listenerList = listenerList;
        for (SocketIOListener socketIOListener : listenerList) {
            Class<? extends SocketIOListener> aClass = socketIOListener.getClass();
            ReflectionUtils.doWithMethods(aClass, method -> {
                OnConnect onConnect = method.getDeclaredAnnotation(OnConnect.class);
                if(null != onConnect) {
                    ReflectionUtils.makeAccessible(method);
                    connect.add(new SocketInfo(socketIOListener, method, null, socketIoProperties));
                    return;
                }

                OnDisconnect onDisconnect = method.getDeclaredAnnotation(OnDisconnect.class);
                if(null != onDisconnect) {
                    ReflectionUtils.makeAccessible(method);
                    disconnect.add(new SocketInfo(socketIOListener, method, null, socketIoProperties));
                    return;
                }

                OnEvent onEvent = method.getDeclaredAnnotation(OnEvent.class);
                if(null != onEvent) {
                    ReflectionUtils.makeAccessible(method);
                    data.add(new SocketInfo(socketIOListener, method, onEvent.value(), socketIoProperties));
                    return;
                }
            });
        }
    }

    @Override
    public void doConnect(SocketIOClient client) {
        for (SocketInfo socketInfo : connect) {
            socketInfo.invoke(client);
        }
    }

    @Override
    public void disConnect(SocketIOClient client) {
        for (SocketInfo socketInfo : disconnect) {
            socketInfo.invoke(client);
        }
    }

    @Override
    public void registerEvent(DelegateSocketIOServer socketIOServer) {
        for (SocketInfo socketInfo : data) {
            String name = socketInfo.getName();
            if(!StringUtils.hasText(name)) {
                continue;
            }
            socketIOServer.addEventListener(name, String.class, new DataListener<String>() {
                @Override
                public void onData(SocketIOClient client, String data, AckRequest ackSender) throws Exception {
                    socketInfo.invoke(client, data);
                }
            });
        }
    }


    @Data
    @Slf4j
    @AllArgsConstructor
    private static class SocketInfo {

        private Object bean;

        private Method method;

        private String name;

        private SocketIoProperties socketIoProperties;


        public void invoke(SocketIOClient client) {
            int parameterCount = method.getParameterCount();
            if(parameterCount == 1) {
                Parameter parameter = method.getParameters()[0];
                Class<?> type = parameter.getType();
                try {
                    if(type == String.class) {
                        method.invoke(bean, client.getSessionId().toString());
                    }

                    if(type == SocketSession.class || type == Object.class) {
                        method.invoke(bean, new SocketSession(client, socketIoProperties));
                    }
                } catch (Exception e) {
                    log.error("{}", e.getMessage());
                }
                return;
            }

            if(parameterCount == 0) {
                try {
                    method.invoke(bean);
                } catch (Exception e) {
                    log.error("{}", e.getMessage());
                }
                return;
            }
        }

        public void invoke(SocketIOClient client, String data) {
            int parameterCount = method.getParameterCount();
            Object[] args = new Object[parameterCount];
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                args[i] = createParameter(parameter, client, data);
            }
            try {
                method.invoke(bean, args);
            } catch (Exception e) {
                log.error("{}", e.getMessage());
            }
        }

        private Object createParameter(Parameter parameter, SocketIOClient client, String data) {
            Class<?> type = parameter.getType();
            if(SocketIOClient.class.isAssignableFrom(type)) {
                return client;
            }

            if(SocketSession.class.isAssignableFrom(type)) {
                return new SocketSession(client, socketIoProperties);
            }

            if(String.class.isAssignableFrom(type)) {
                return data;
            }

            try {
                return objectMapper.treeToValue(objectMapper.readTree(data), type);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
