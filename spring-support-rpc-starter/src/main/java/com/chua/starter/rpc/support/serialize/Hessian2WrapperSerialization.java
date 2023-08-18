//package com.chua.starter.rpc.support.serialize;
//
//import com.alibaba.com.caucho.hessian.io.Hessian2Input;
//import com.alibaba.com.caucho.hessian.io.Hessian2Output;
//import com.alibaba.fastjson2.JSON;
//import com.alibaba.fastjson2.JSONObject;
//import com.boren.starter.common.support.application.Binder;
//import com.boren.starter.common.support.crypto.decode.Decode;
//import com.boren.starter.common.support.crypto.encode.Encode;
//import com.boren.starter.common.support.utils.JsonUtils;
//import com.boren.starter.common.support.utils.Md5Utils;
//import com.boren.starter.common.support.utils.SpringFactoriesLoaderUtils;
//import com.chua.common.support.crypto.Codec;
//import com.chua.common.support.json.Json;
//import com.chua.common.support.spi.ServiceProvider;
//import com.chua.common.support.utils.Md5Utils;
//import com.chua.starter.common.support.configuration.SpringBeanUtils;
//import com.chua.starter.remote.support.constant.Constant;
//import com.chua.starter.remote.support.properties.RemoteProperties;
//import com.chua.starter.rpc.support.properties.RpcProperties;
//import org.apache.dubbo.common.URL;
//import org.apache.dubbo.common.serialize.Cleanable;
//import org.apache.dubbo.common.serialize.ObjectInput;
//import org.apache.dubbo.common.serialize.ObjectOutput;
//import org.apache.dubbo.common.serialize.hessian2.Hessian2Serialization;
//import org.apache.dubbo.common.serialize.hessian2.dubbo.Hessian2FactoryInitializer;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.lang.reflect.Type;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.UUID;
//
//import static org.apache.dubbo.common.serialize.Constants.KRYO_SERIALIZATION2_ID;
//
///**
// * Hessian2 wrapper
// *
// * @author CH
// */
//public class Hessian2WrapperSerialization extends Hessian2Serialization {
//
//    private final RpcProperties rpcProperties;
//
//    public Hessian2WrapperSerialization() {
//        this.rpcProperties = SpringBeanUtils.bindOrCreate(RpcProperties.PRE, RpcProperties.class);
//    }
//
//    @Override
//    public byte getContentTypeId() {
//        return KRYO_SERIALIZATION2_ID;
//    }
//
//    @Override
//    public String getContentType() {
//        return "x-application/hessian2";
//    }
//
//    @Override
//    public ObjectOutput serialize(URL url, OutputStream out) throws IOException {
//        return new CustomObjectOutput(out);
//    }
//
//    @Override
//    public ObjectInput deserialize(URL url, InputStream is) throws IOException {
//        return new CustomObjectInput(url, is);
//    }
//
//    final class CustomObjectOutput implements ObjectOutput, Cleanable {
//
//        private final Hessian2Output mH2o;
//
//        public CustomObjectOutput(OutputStream os) {
//            mH2o = new Hessian2Output(os);
//            mH2o.setSerializerFactory(Hessian2FactoryInitializer.getInstance().getSerializerFactory());
//        }
//
//        @Override
//        public void writeBool(boolean v) throws IOException {
//            mH2o.writeBoolean(v);
//        }
//
//        @Override
//        public void writeByte(byte v) throws IOException {
//            mH2o.writeInt(v);
//        }
//
//        @Override
//        public void writeShort(short v) throws IOException {
//            mH2o.writeInt(v);
//        }
//
//        @Override
//        public void writeInt(int v) throws IOException {
//            mH2o.writeInt(v);
//        }
//
//        @Override
//        public void writeLong(long v) throws IOException {
//            mH2o.writeLong(v);
//        }
//
//        @Override
//        public void writeFloat(float v) throws IOException {
//            mH2o.writeDouble(v);
//        }
//
//        @Override
//        public void writeDouble(double v) throws IOException {
//            mH2o.writeDouble(v);
//        }
//
//        @Override
//        public void writeBytes(byte[] b) throws IOException {
//            mH2o.writeBytes(b);
//        }
//
//        @Override
//        public void writeBytes(byte[] b, int off, int len) throws IOException {
//            mH2o.writeBytes(b, off, len);
//        }
//
//        @Override
//        public void writeUTF(String v) throws IOException {
//            mH2o.writeString(v);
//        }
//
//        @Override
//        public void writeObject(Object obj) throws IOException {
//            String json = Json.toJson(obj);
//
//            String key = UUID.randomUUID().toString();
//            Codec codec = ServiceProvider.of(Codec.class).getExtension(rpcProperties.getCrypto());
//            String string = Md5Utils.getInstance().getMd5String(key);
//            String encode1 = codec.encodeHex(json, string);
//            Map<String, String> tmp = new LinkedHashMap<>(2);
//            tmp.put(Constant.HEADER, encode1);
//            tmp.put(Constant.UID, key);
//
//            String encode2 = codec.encode(JsonUtils.toJson(tmp), rpcProperties.getServiceKey());
//            Map<String, String> map = new HashMap<>(1);
//            map.put(Constant.HEADER, encode2);
//            mH2o.writeObject(map);
//        }
//
//        @Override
//        public void flushBuffer() throws IOException {
//            mH2o.flushBuffer();
//        }
//
//        public OutputStream getOutputStream() throws IOException {
//            return mH2o.getBytesOutputStream();
//        }
//
//        @Override
//        public void cleanup() {
//            if (mH2o != null) {
//                mH2o.reset();
//            }
//        }
//    }
//
//    final class CustomObjectInput implements ObjectInput, Cleanable {
//        private final URL url;
//        private final Hessian2Input mH2i;
//        private final Hessian2FactoryInitializer hessian2FactoryInitializer;
//
//        public CustomObjectInput(URL url, InputStream is) {
//            this.url = url;
//            mH2i = new Hessian2Input(is);
//            hessian2FactoryInitializer = Hessian2FactoryInitializer.getInstance();
//            mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
//        }
//
//        @Override
//        public boolean readBool() throws IOException {
//            return mH2i.readBoolean();
//        }
//
//        @Override
//        public byte readByte() throws IOException {
//            return (byte) mH2i.readInt();
//        }
//
//        @Override
//        public short readShort() throws IOException {
//            return (short) mH2i.readInt();
//        }
//
//        @Override
//        public int readInt() throws IOException {
//            return mH2i.readInt();
//        }
//
//        @Override
//        public long readLong() throws IOException {
//            return mH2i.readLong();
//        }
//
//        @Override
//        public float readFloat() throws IOException {
//            return (float) mH2i.readDouble();
//        }
//
//        @Override
//        public double readDouble() throws IOException {
//            return mH2i.readDouble();
//        }
//
//        @Override
//        public byte[] readBytes() throws IOException {
//            return mH2i.readBytes();
//        }
//
//        @Override
//        public String readUTF() throws IOException {
//            return mH2i.readString();
//        }
//
//        @Override
//        public Object readObject() throws IOException {
//            return mH2i.readObject();
//        }
//
//        @Override
//        @SuppressWarnings("unchecked")
//        public <T> T readObject(Class<T> cls) throws IOException {
//            if (!mH2i.getSerializerFactory().getClassLoader().equals(Thread.currentThread().getContextClassLoader())) {
//                mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
//            }
//            String header = new JSONObject((Map) mH2i.readObject(Object.class)).getString(Constant.HEADER);
//            Decode decode = SpringFactoriesLoaderUtils.loader(Decode.class).getOne(rpcProperties.getCrypto());
//            JSONObject jsonObject = JSON.parseObject(decode.decode(header, rpcProperties.getServiceKey()));
//            String accessKey = jsonObject.getString(Constant.UID);
//            String encode1 = decode.decode(jsonObject.getString(Constant.HEADER), Md5Utils.getInstance().getMd5String(accessKey));
//            return JSON.parseObject(encode1, cls);
//        }
//
//        @Override
//        public <T> T readObject(Class<T> cls, Type type) throws IOException, ClassNotFoundException {
//            return readObject(cls);
//        }
//
//        @Override
//        public void cleanup() {
//            if (mH2i != null) {
//                mH2i.reset();
//            }
//        }
//    }
//}
