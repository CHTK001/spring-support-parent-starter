package com.chua.socketio.support.resolver;

import com.chua.socketio.support.SocketIOListener;
import com.chua.socketio.support.annotations.OnConnect;
import com.chua.socketio.support.annotations.OnEvent;
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
    private List<SocketIOListener> listenerList;
    private List<SocketInfo> connect = new LinkedList<>();
    private List<SocketInfo> disconnect = new LinkedList<>();
    private List<SocketInfo> data = new LinkedList<>();

    private ObjectMapper objectMapper = new ObjectMapper();

    public DefaultSocketSessionResolver(List<SocketIOListener> listenerList) {
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.listenerList = listenerList;
        for (SocketIOListener socketIOListener : listenerList) {
            Class<? extends SocketIOListener> aClass = socketIOListener.getClass();
            ReflectionUtils.doWithMethods(aClass, method -> {
                OnConnect onConnect = method.getDeclaredAnnotation(OnConnect.class);
                if(null != onConnect) {
                    ReflectionUtils.makeAccessible(method);
                    connect.add(new SocketInfo(socketIOListener, method, null));
                    return;
                }

                OnDisconnect onDisconnect = method.getDeclaredAnnotation(OnDisconnect.class);
                if(null != onDisconnect) {
                    ReflectionUtils.makeAccessible(method);
                    disconnect.add(new SocketInfo(socketIOListener, method, null));
                    return;
                }

                OnEvent onEvent = method.getDeclaredAnnotation(OnEvent.class);
                if(null != onEvent) {
                    ReflectionUtils.makeAccessible(method);
                    disconnect.add(new SocketInfo(socketIOListener, method, onEvent.value()));
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
    private class SocketInfo {

        private Object bean;

        private Method method;

        private String name;


        public void invoke(SocketIOClient client) {
            int parameterCount = method.getParameterCount();
            if(parameterCount == 1) {
                Parameter parameter = method.getParameters()[0];
                Class<?> type = parameter.getType();
                try {
                    if(type == String.class) {
                        method.invoke(bean, client.getSessionId().toString());
                    }

                    if(type == SocketSession.class) {
                        method.invoke(bean, new SocketSession(client));
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
            if(parameterCount == 1) {
                Parameter parameter = method.getParameters()[0];
                Class<?> type = parameter.getType();
                try {
                    Object treeToValue = objectMapper.treeToValue(objectMapper.readTree(data), type);
                    method.invoke(bean, treeToValue);
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
    }
}
