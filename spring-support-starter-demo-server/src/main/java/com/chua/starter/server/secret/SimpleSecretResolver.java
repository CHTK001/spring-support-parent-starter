package com.chua.starter.server.secret;//package com.chua.starter.server.secret;
//
//import com.chua.starter.common.support.application.Binder;
//import org.springframework.beans.BeansException;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.context.annotation.Configuration;
//
//import java.net.InetSocketAddress;
//
///**
// * sk
// *
// * @author CH
// */
//@Configuration
//public class SimpleSecretResolver implements SecretResolver, ApplicationContextAware {
//
//    private RemoteProperties remoteProperties;
//
//
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        this.remoteProperties = Binder.binder(applicationContext.getEnvironment(), RemoteProperties.PRE, RemoteProperties.class);
//    }
//
//    @Override
//    public boolean resolve(String accessKey, String secretKey, InetSocketAddress remoteAddress) {
//        return true;
//    }
//}
