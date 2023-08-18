package com.chua.starter.rpc.support.protocol.bus;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import com.boren.starter.common.support.application.Binder;
import com.boren.starter.common.support.crypto.decode.Decode;
import com.boren.starter.common.support.crypto.encode.Encode;
import com.boren.starter.common.support.utils.JsonUtils;
import com.boren.starter.common.support.utils.Md5Utils;
import com.boren.starter.common.support.utils.SpringBeanUtils;
import com.boren.starter.common.support.utils.SpringFactoriesLoaderUtils;
import com.chua.starter.remote.support.constant.Constant;
import com.chua.starter.remote.support.properties.RemoteProperties;
import com.chua.starter.remote.support.protocol.EnableStartProtocolServer;
import com.chua.starter.remote.support.secret.SecretResolver;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.RemotingServer;
import org.zbus.net.http.Message;
import org.zbus.rpc.RpcCodec;
import org.zbus.rpc.RpcException;
import org.zbus.rpc.RpcProcessor;
import org.zbus.rpc.direct.Service;
import org.zbus.rpc.direct.ServiceConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bus服务
 *
 * @author CH
 */
public class BusProtocolServer implements EnableStartProtocolServer {

    private RemotingServer server;
    private String address;
    private final RpcNewProcessor rpcProcessor = new RpcNewProcessor();
    private Service server1;
    private URL url;

    static class RpcNewProcessor extends RpcProcessor {


    }

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public BusProtocolServer() {
    }

    @Override
    public RemotingServer getRemotingServer() {
        return server;
    }

    @Override
    public String getAddress() {
        return StringUtils.isNotEmpty(address) ? address : server.getUrl().getAddress();
    }

    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public void reset(URL url) {
        if (null == server) {
            return;
        }
        server.reset(url);
    }

    @Override
    public void close() {
        if (null == server) {
            return;
        }

        server.close();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void start(URL url) {
        this.url = url;
        ExtensionLoader<Serialization> extensionLoader = ExtensionLoader.getExtensionLoader(Serialization.class);
        Serialization serialization = extensionLoader.getExtension(url.getParameter("serialization", "hessian2"));
        rpcProcessor.codec(new BusRpcCodec(serialization));
        try {
            ServiceConfig config = new ServiceConfig();
            config.serverPort = url.getPort();
            config.serverHost = url.getHost();
            config.setMessageProcessor(rpcProcessor);
            this.server1 = new Service(config);
            try {
                server1.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void deploy(Class resourceDef, Object resourceInstance, URL contextPath) {
        String serviceInterface = contextPath.getServiceInterface();
        rpcProcessor.addModule(serviceInterface, resourceInstance);

    }

    @Override
    public void undeploy(Class resourceDef) {
        rpcProcessor.removeModule(resourceDef.getName());
    }

    @Override
    public void stop() {
        try {
            server1.close();
        } catch (IOException ignored) {
        }

    }

    public static final class BusRpcCodec implements RpcCodec {

        private final Serialization serialization;
        private final RemoteProperties remoteProperties;

        public BusRpcCodec(Serialization serialization) {
            this.serialization = serialization;
            this.remoteProperties = Binder.binder(RemoteProperties.PRE, RemoteProperties.class);
        }

        @Override
        public Message encodeRequest(Request request) {
            Message msg = new Message();
            String encoding = request.getEncoding();
            if (encoding == null) {
                encoding = "utf-8";
            }
            byte[] jsonBytes = toJsonBytes(request, encoding, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteClassName);

            Encode encode = SpringFactoriesLoaderUtils.loader(Encode.class).getOne(remoteProperties.getCrypto());
            try {
                String encode1 = encode.encode(new String(jsonBytes, encoding), Md5Utils.getInstance().getMd5String(remoteProperties.getAccessKey() + remoteProperties.getSecretKey()));
                Map<String, String> tmp = new LinkedHashMap<>(3);
                tmp.put(Constant.HEADER, encode1);
                tmp.put(Constant.ACCESS_KEY, remoteProperties.getAccessKey());

                msg.setBody(encode.encode(JsonUtils.toJson(tmp), remoteProperties.getServiceKey()).getBytes());
            } catch (IOException ignored) {
            }
            return msg;
        }

        @Override
        public Message encodeResponse(Response response) {
            Message msg = new Message();
            msg.setStatus("200");
            if (response.getError() != null) {
                Throwable error = response.getError();
                if (error instanceof IllegalArgumentException) {
                    msg.setStatus("400");
                } else {
                    msg.setStatus("500");
                }
            }
            String encoding = response.getEncoding();
            if (encoding == null) {
                encoding = "utf-8";
            }
            byte[] jsonBytes = toJsonBytes(response, encoding, SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteClassName);
            Encode encode = SpringFactoriesLoaderUtils.loader(Encode.class).getOne(remoteProperties.getCrypto());
            String md5String = Md5Utils.getInstance().getMd5String(UUID.randomUUID().toString() + System.nanoTime());
            try {
                String encode1 = encode.encode(new String(jsonBytes, encoding), md5String);
                Map<String, String> rs = new LinkedHashMap<>(2);
                rs.put(Constant.UID, md5String);
                rs.put(Constant.HEADER, encode1);
                msg.setBody(encode.encode(JsonUtils.toJson(rs), remoteProperties.getServiceKey()).getBytes());
            } catch (IOException ignored) {
            }
            return msg;
        }

        @Override
        public Request decodeRequest(Message msg) {
            String encoding = msg.getEncoding();
            if (encoding == null) {
                encoding = "utf-8";
            }
            byte[] body = msg.getBody();
            Decode decode = SpringFactoriesLoaderUtils.loader(Decode.class).getOne(remoteProperties.getCrypto());
            Request req = null;

            try {
                JSONObject jsonObject = JSON.parseObject(decode.decode(new String(body, encoding), remoteProperties.getServiceKey()));
                Map<String, SecretResolver> beansOfType = SpringBeanUtils.getApplicationContext().getBeansOfType(SecretResolver.class);
                String secretKey = null;
                String accessKey = jsonObject.getString(Constant.ACCESS_KEY);
//                for (Map.Entry<String, SecretResolver> entry : beansOfType.entrySet()) {
//                    String newSecretKey = entry.getValue().resolve(accessKey);
//                    if (null != newSecretKey) {
//                        secretKey = newSecretKey;
//                    }
//                }
                String encode1 = decode.decode(jsonObject.getString(Constant.HEADER), Md5Utils.getInstance().getMd5String(accessKey + secretKey));
                req = JSON.parseObject(encode1, Request.class);
                Request.normalize(req);
            } catch (IOException ignored) {
            }
            return req;
        }

        @Override
        public Response decodeResponse(Message msg) {
            String encoding = msg.getEncoding();
            if (encoding == null) {
                encoding = "utf-8";
            }
            String jsonString = msg.getBodyString(encoding);
            Decode decode = SpringFactoriesLoaderUtils.loader(Decode.class).getOne(remoteProperties.getCrypto());
            JSONObject jsonObject = JSON.parseObject(decode.decode(jsonString, remoteProperties.getServiceKey()));
            jsonString = decode.decode(jsonObject.getString(Constant.HEADER), jsonObject.getString(Constant.UID));
            Response res = null;
            try {
                res = JSON.parseObject(jsonString, Response.class);
            } catch (Exception e) { //probably error can not be instantiated
                res = new Response();
                JSONObject json = null;
                try {
                    jsonString = jsonString.replace("@type", "unknown-class"); //禁止掉实例化
                    json = JSON.parseObject(jsonString);
                } catch (Exception ex) {
                    String prefix = "";
                    if (msg.isStatus200()) {
                        prefix = "JSON format invalid: ";
                    }
                    throw new RpcException(prefix + jsonString);
                }
                if (json != null) {
                    final String stackTrace = Response.KEY_STACK_TRACE;
                    final String error = Response.KEY_ERROR;
                    if (json.containsKey(stackTrace) &&
                            json.get(stackTrace) != null) {
                        throw new RpcException(json.getString(stackTrace));
                    }
                    if (json.containsKey(error) &&
                            json.get(error) != null) {
                        throw new RpcException(json.getString(error));
                    }
                    res.setResult(json.get(Response.KEY_RESULT));
                }
            }
            return res;
        }

        @Override
        public Object convert(Object param, Class<?> targetType) throws ClassNotFoundException {
            if ("java.lang.Class".equals(targetType.getName())) {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                return classLoader.loadClass(param.toString());
            }
            return TypeUtils.castToJavaBean(param, targetType);
        }

        private byte[] toJsonBytes(Object object, String charsetName, SerializerFeature... features) {
            SerializeWriter out = new SerializeWriter();

            try {
                JSONSerializer serializer = new JSONSerializer(out);
                for (SerializerFeature feature : features) {
                    serializer.config(feature, true);
                }

                serializer.write(object);

                return out.toBytes(charsetName);
            } finally {
                out.close();
            }
        }
    }
}
