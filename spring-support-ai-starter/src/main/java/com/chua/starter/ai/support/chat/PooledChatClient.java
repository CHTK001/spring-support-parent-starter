package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.concurrent.pool.GenericObjectPool;
import com.chua.common.support.concurrent.pool.ObjectPool;
import com.chua.common.support.concurrent.pool.ObjectPoolConfig;
import com.chua.common.support.concurrent.pool.ObjectFactory;
import com.chua.common.support.pool.PooledObjectClient;

import java.util.List;
import java.util.function.Supplier;

/**
* 池化的 {@link ChatClient} 包装器
*
* <p>持有一个底层 {@link ChatClient} 工厂, 通过对象池复用底层实例。
* 适合将任意 SPI 加载的 {@link ChatClient} 包装为池化客户端, 提升并发吞吐量。
*
* <p>使用示例:
* <pre>{@code
*   // 单例 (默认)
*   ChatClient client = new PooledChatClient(() ->
*       com.chua.common.support.ai.chat.ChatClient.create("openai", "sk-xxx")
*           .model("gpt-4"));
*
*   // 启用 4 个实例的对象池
*   client.pool(4);
*
*   // 调用方法, 内部会 borrow / return
*   String answer = client.chatSync("hello");
* }</pre>rrow / return
*   String answer = client.chatSync("hello");
* }</pre>
*
* @since 4.0.0.42
* @author CH
 */
public class PooledChatClient implements ChatClient {

    /**
    * 底层客户端工厂
     */
    private final Supplier<ChatClient> factory;

    /**
    * 单例实例 (单例模式下使用)
     */
    private volatile ChatClient singleton;

    /**
    * 对象池 (池化模式下使用)
     */
    private volatile ObjectPool<ChatClient> objectPool;

    /**
    * 当前模式: 0=无池, 1=单例, >1=池化
     */
    private volatile int poolMode = 1;


    /**
    * 构造方法
    *
    * @param factory 创建底层 对话客户端 的工厂
     */
    public PooledChatClient(Supplier<ChatClient> factory) {
        this.factory = factory;
    }


    /**
    * 包装已有的 对话客户端 为可池化版本
    *
    * @param client 已有的 对话客户端 实例 (作为模板)
    * @return 可池化的 对话客户端 包装
     */
    public static PooledChatClient wrap(ChatClient client) {
        return new PooledChatClient(() -> client);
    }


    @Override
    /** configure游泳池 */
    public void configurePool(Number size) {
        int target = size == null ? 1 : size.intValue();
        synchronized (this) {
            /**
            * 关闭游泳池。
             */
            closePool();
            if (target <= 0) {
                poolMode = 0;
            } else if (target == 1) {
                poolMode = 1;
            } else {
                poolMode = target;
                objectPool = createPool(target);
            }
        }
    }


    @Override
    public ObjectPool<?> getPool() {
        return objectPool;
    }


    /**
    * 借出底层客户端
    * @return borrow客户端的结果
     */
    private ChatClient borrowClient() {
        int mode = poolMode;
        if (mode <= 0) {
            return factory.get();
        }
        if (mode == 1) {
            ChatClient local = singleton;
            if (local == null) {
                synchronized (this) {
                    local = singleton;
                    if (local == null) {
                        local = factory.get();
                        singleton = local;
                    }
                }
            }
            return local;
        }
        ObjectPool<ChatClient> pool = objectPool;
        if (pool == null) {
            synchronized (this) {
                pool = objectPool;
                if (pool == null) {
                    pool = createPool(mode);
                    objectPool = pool;
                }
            }
            pool = objectPool;
        }
        try {
            return pool.borrow();
        } catch (Exception e) {
            /**
            * illegal状态异常。
            * @param "借出客户端实例失败" "借出客户端实例失败"
            * @param e e
            * @return illegal状态异常的结果
             */
            throw new IllegalStateException("借出客户端实例失败", e);
        }
    }


    /**
    * 归还底层客户端
    * @param client 客户端
     */
    private void returnClient(ChatClient client) {
        if (client == null) {
            return;
        }
        ObjectPool<ChatClient> pool = objectPool;
        if (pool != null) {
            pool.returnObject(client);
        }
    }


    /**
    * 关闭并清理池
     */
    private void closePool() {
        if (objectPool != null) {
            try {
                objectPool.close();
            } catch (Exception ignored) {
            }
            objectPool = null;
        }
        singleton = null;
    }


    /**
    * 创建对象池
    * @param maxTotal 最大total
    * @return 创建游泳池的结果
     */
    private ObjectPool<ChatClient> createPool(int maxTotal) {
        ObjectPoolConfig config = ObjectPoolConfig.builder()
                .maxTotal(maxTotal)
                .maxIdle(maxTotal)
                .minIdle(0)
                .build();
        ObjectFactory<ChatClient> objectFactory = new ObjectFactory<ChatClient>() {
            @Override
            /** 创建 */
            public ChatClient create() {
                return factory.get();
            }

            @Override
            /** 校验 */
            public boolean validate(ChatClient obj) {
                return obj != null;
            }

            @Override
            /** 销毁 */
            public void destroy(ChatClient obj) {
                if (obj instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) obj).close();
                    } catch (Exception ignored) {
                    }
                }
            }
        };
        return new GenericObjectPool<>(config, objectFactory);
    }


    @Override
    /** 对话 */
    public ChatResponse chat(ChatScope scope) {
        ChatClient inner = borrowClient();
        try {
            return inner.chat(scope);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 对话 */
    public void chat(ChatScope scope, java.util.function.Consumer<String> consumer, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        ChatClient inner = borrowClient();
        try {
            inner.chat(scope, consumer, onComplete, onError);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 列表tools */
    public List<AiTool> listTools(ChatScope scope) {
        ChatClient inner = borrowClient();
        try {
            return inner.listTools(scope);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 调用Tool */
    public AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        ChatClient inner = borrowClient();
        try {
            return inner.callTool(scope, toolCall);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 获取模型名称 */
    public String getModelName() {
        ChatClient inner = borrowClient();
        try {
            return inner.getModelName();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 获取工厂 */
    public String getFactory() {
        ChatClient inner = borrowClient();
        try {
            return inner.getFactory();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 获取提供者 */
    public String getProvider() {
        ChatClient inner = borrowClient();
        try {
            return inner.getProvider();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 获取默认模型 */
    public String getDefaultModel() {
        ChatClient inner = borrowClient();
        try {
            return inner.getDefaultModel();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 获取端点 */
    public String getEndpoint() {
        ChatClient inner = borrowClient();
        try {
            return inner.getEndpoint();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 列表模型definitions */
    public List<ModelDefinition> listModelDefinitions() {
        ChatClient inner = borrowClient();
        try {
            return inner.listModelDefinitions();
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 对话同步 */
    public String chatSync(String prompt) {
        ChatClient inner = borrowClient();
        try {
            return inner.chatSync(prompt);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 对话同步 */
    public String chatSync(String prompt, long timeoutMillis) {
        ChatClient inner = borrowClient();
        try {
            return inner.chatSync(prompt, timeoutMillis);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 对话同步with响应 */
    public com.chua.common.support.ai.chat.ChatSyncResponse chatSyncWithResponse(String prompt) {
        ChatClient inner = borrowClient();
        try {
            return inner.chatSyncWithResponse(prompt);
        } finally {
            /**
            * 申报客户端。
            * @param inner 内部
             */
            returnClient(inner);
        }
    }


    @Override
    /** 关闭 */
    public void close() {
        synchronized (this) {
            /**
            * 关闭游泳池。
             */
            closePool();
        }
    }
}
