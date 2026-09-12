package com.chua.socketio.support.constant;

/**
* 套接字.IO 主题常量
* <p>
* 命名格式: 项目:模块:功能
* 例如: 监控:Docker:拉手_进步, 系统:消息:push
*
* @版本 1.0.0
* @since 2024-12-04
* @author CH
 */
public final class SocketTopics {

    /** 创建 套接字topics 实例 */
    private SocketTopics() {
        // 私有构造函数，防止实例化
    }
    /**
    * 服务模块主题 (服务)
    * 用于服务级别的通用功能
     */
    public static final class Service {

        /**
        * 创建 服务 实例
        *
        * @return 服务的结果
         */
        private Service() {
        }

        /**
        * 消息相关
         */
        public static final class Message {
            /** Push */
            public static final String PUSH = "service:message:push";
            /** Notification */
            public static final String NOTIFICATION = "service:message:notification";
            /** 读取 */
            public static final String READ = "service:message:read";
            /** 删除 */
            public static final String DELETE = "service:message:delete";

            /**
            * 创建 消息 实例
            *
            * @return 消息的结果
             */
            private Message() {
            }
        }

        /**
        * 操作相关
         */
        public static final class Operation {
            /** 开始 */
            public static final String START = "service:operation:start";
            /** 停止 */
            public static final String STOP = "service:operation:stop";
            /** Restart */
            public static final String RESTART = "service:operation:restart";
            /** 状态 */
            public static final String STATUS = "service:operation:status";
            /** 日志 */
            public static final String LOG = "service:operation:log";
            /** 进步 */
            public static final String PROGRESS = "service:operation:progress";
            /** 完成 */
            public static final String COMPLETE = "service:operation:complete";
            /** 错误 */
            public static final String ERROR = "service:operation:error";

            /**
            * 创建 Operation 实例
            *
            * @return Operation的结果
             */
            private Operation() {
            }
        }
    }
    /**
    * 系统模块主题 (系统)
    * 用于系统级别的通用功能
     */
    public static final class System {

        /**
        * 创建 系统 实例
        *
        * @return 系统的结果
         */
        private System() {
        }

        /**
        * 消息相关
         */
        public static final class Message {
            /** Push */
            public static final String PUSH = "system:message:push";
            /** Notification */
            public static final String NOTIFICATION = "system:message:notification";
            /** 读取 */
            public static final String READ = "system:message:read";
            /** 删除 */
            public static final String DELETE = "system:message:delete";

            /**
            * 创建 消息 实例
            *
            * @return 消息的结果
             */
            private Message() {
            }
        }

        /**
        * 用户相关
         */
        public static final class User {
            /** Online */
            public static final String ONLINE = "system:user:online";
            /** Offline */
            public static final String OFFLINE = "system:user:offline";
            /** 状态 */
            public static final String STATUS = "system:user:status";
            /** Kick */
            public static final String KICK = "system:user:kick";

            /**
            * 创建 用户 实例
            *
            * @return 用户的结果
             */
            private User() {
            }
        }

        /**
        * 配置相关
         */
        public static final class Config {
            /** 更新 */
            public static final String UPDATE = "system:config:update";
            /** 版本 */
            public static final String VERSION = "system:config:version";

            /**
            * 创建 配置 实例
            *
            * @return 配置的结果
             */
            private Config() {
            }
        }

        /**
        * 权限相关
         */
        public static final class Auth {
            /** 令牌_expired */
            public static final String TOKEN_EXPIRED = "system:auth:token_expired";
            /** 权限_改变 */
            public static final String PERMISSION_CHANGE = "system:auth:permission_change";

            /**
            * 创建 认证 实例
            *
            * @return 认证的结果
             */
            private Auth() {
            }
        }
    }

}
