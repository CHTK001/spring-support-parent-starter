package com.chua.socketio.support.constant;

/**
 * Socket.IO 主题常量
 * <p>
 * 命名格式: 项目:模块:功能
 * 例如: monitor:docker:pull_progress, system:message:push
 *
 * @version 1.0.0
 * @since 2024-12-04
 */
public final class SocketTopics {

    /** 创建 SocketTopics 实例 */
    private SocketTopics() {
        // 私有构造函数，防止实例化
    }
    /**
     * 服务模块主题 (service)
     * 用于服务级别的通用功能
     */
    public static final class Service {

        /** 创建 Service 实例 */
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
            /** Read */
            public static final String READ = "service:message:read";
            /** Delete */
            public static final String DELETE = "service:message:delete";

            /** 创建 Message 实例 */
            private Message() {
            }
        }

        /**
         * 操作相关
         */
        public static final class Operation {
            /** 开始 */
            public static final String START = "service:operation:start";
            /** Stop */
            public static final String STOP = "service:operation:stop";
            /** Restart */
            public static final String RESTART = "service:operation:restart";
            /** 状态 */
            public static final String STATUS = "service:operation:status";
            /** 日志 */
            public static final String LOG = "service:operation:log";
            /** Progress */
            public static final String PROGRESS = "service:operation:progress";
            /** Complete */
            public static final String COMPLETE = "service:operation:complete";
            /** 错误 */
            public static final String ERROR = "service:operation:error";

            /** 创建 Operation 实例 */
            private Operation() {
            }
        }
    }
    /**
     * 系统模块主题 (system)
     * 用于系统级别的通用功能
     */
    public static final class System {

        /** 创建 System 实例 */
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
            /** Read */
            public static final String READ = "system:message:read";
            /** Delete */
            public static final String DELETE = "system:message:delete";

            /** 创建 Message 实例 */
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

            /** 创建 User 实例 */
            private User() {
            }
        }

        /**
         * 配置相关
         */
        public static final class Config {
            /** Update */
            public static final String UPDATE = "system:config:update";
            /** 版本 */
            public static final String VERSION = "system:config:version";

            /** 创建 Config 实例 */
            private Config() {
            }
        }

        /**
         * 权限相关
         */
        public static final class Auth {
            /** Token_expired */
            public static final String TOKEN_EXPIRED = "system:auth:token_expired";
            /** Permission_change */
            public static final String PERMISSION_CHANGE = "system:auth:permission_change";

            /** 创建 Auth 实例 */
            private Auth() {
            }
        }
    }

}
