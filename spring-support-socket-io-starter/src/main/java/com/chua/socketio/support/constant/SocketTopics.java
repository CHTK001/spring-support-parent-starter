package com.chua.socketio.support.constant;

/**
 * Socket.IO 主题常量
 * <p>
 * 命名格式: 项目:模块:功能
 * 例如: monitor:docker:pull_progress, system:message:push
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public final class SocketTopics {

    private SocketTopics() {
        // 私有构造函数，防止实例化
    }
    /**
     * 服务模块主题 (service)
     * 用于服务级别的通用功能
     */
    public static final class Service {

        private Service() {
        }

        /**
         * 消息相关
         */
        public static final class Message {
            public static final String PUSH = "service:message:push";
            public static final String NOTIFICATION = "service:message:notification";
            public static final String READ = "service:message:read";
            public static final String DELETE = "service:message:delete";

            private Message() {
            }
        }

        /**
         * 操作相关
         */
        public static final class Operation {
            public static final String START = "service:operation:start";
            public static final String STOP = "service:operation:stop";
            public static final String RESTART = "service:operation:restart";
            public static final String STATUS = "service:operation:status";
            public static final String LOG = "service:operation:log";
            public static final String PROGRESS = "service:operation:progress";
            public static final String COMPLETE = "service:operation:complete";
            public static final String ERROR = "service:operation:error";

            private Operation() {
            }
        }
    }
    /**
     * 系统模块主题 (system)
     * 用于系统级别的通用功能
     */
    public static final class System {

        private System() {
        }

        /**
         * 消息相关
         */
        public static final class Message {
            public static final String PUSH = "system:message:push";
            public static final String NOTIFICATION = "system:message:notification";
            public static final String READ = "system:message:read";
            public static final String DELETE = "system:message:delete";

            private Message() {
            }
        }

        /**
         * 用户相关
         */
        public static final class User {
            public static final String ONLINE = "system:user:online";
            public static final String OFFLINE = "system:user:offline";
            public static final String STATUS = "system:user:status";
            public static final String KICK = "system:user:kick";

            private User() {
            }
        }

        /**
         * 配置相关
         */
        public static final class Config {
            public static final String UPDATE = "system:config:update";
            public static final String VERSION = "system:config:version";

            private Config() {
            }
        }

        /**
         * 权限相关
         */
        public static final class Auth {
            public static final String TOKEN_EXPIRED = "system:auth:token_expired";
            public static final String PERMISSION_CHANGE = "system:auth:permission_change";

            private Auth() {
            }
        }
    }

}
