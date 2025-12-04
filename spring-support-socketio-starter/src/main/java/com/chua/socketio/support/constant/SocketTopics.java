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

    /**
     * 监控模块主题 (monitor)
     * 用于监控相关功能
     */
    public static final class Monitor {

        private Monitor() {
        }

        /**
         * Docker/容器相关
         */
        public static final class Docker {
            public static final String IMAGE_PULL_PROGRESS = "monitor:docker:image_pull_progress";
            public static final String IMAGE_EXPORT_PROGRESS = "monitor:docker:image_export_progress";
            public static final String IMAGE_IMPORT_PROGRESS = "monitor:docker:image_import_progress";
            public static final String CONTAINER_STATUS = "monitor:docker:container_status";
            public static final String CONTAINER_LOG = "monitor:docker:container_log";
            public static final String CONTAINER_STATISTICS = "monitor:docker:container_statistics";
            public static final String CONTAINER_EVENTS = "monitor:docker:container_events";
            public static final String START = "monitor:docker:start";
            public static final String PROGRESS = "monitor:docker:progress";
            public static final String COMPLETE = "monitor:docker:complete";
            public static final String ERROR = "monitor:docker:error";

            private Docker() {
            }
        }

        /**
         * 服务器相关
         */
        public static final class Server {
            public static final String STATUS = "monitor:server:status";
            public static final String METRICS = "monitor:server:metrics";
            public static final String ALERT = "monitor:server:alert";
            public static final String TERMINAL = "monitor:server:terminal";
            public static final String SSH_CONNECT = "monitor:server:ssh_connect";
            public static final String SSH_DISCONNECT = "monitor:server:ssh_disconnect";
            public static final String RDP_CONNECT = "monitor:server:rdp_connect";
            public static final String VNC_CONNECT = "monitor:server:vnc_connect";
            public static final String CONNECTION_STATUS = "monitor:server:connection_status";
            public static final String CONNECTION_TEST = "monitor:server:connection_test";
            public static final String HEALTH = "monitor:server:health";

            private Server() {
            }
        }

        /**
         * 软件相关
         */
        public static final class Software {
            public static final String INSTALL_PROGRESS = "monitor:software:install_progress";
            public static final String SYNC_PROGRESS = "monitor:software:sync_progress";

            private Software() {
            }
        }


        /**
         * 操作进度
         */
        public static final class Operation {
            public static final String PROGRESS = "monitor:operation:progress";
            public static final String COMPLETE = "monitor:operation:complete";
            public static final String ERROR = "monitor:operation:error";
            public static final String UPDATE = "monitor:operation:update";

            private Operation() {
            }
        }

        /**
         * WebRTC相关
         */
        public static final class Webrtc {
            public static final String USER_JOINED = "monitor:webrtc:user_joined";
            public static final String USER_LEFT = "monitor:webrtc:user_left";
            public static final String OFFER = "monitor:webrtc:offer";
            public static final String ANSWER = "monitor:webrtc:answer";
            public static final String ICE_CANDIDATE = "monitor:webrtc:ice_candidate";
            public static final String AUDIO_TOGGLE = "monitor:webrtc:audio_toggle";
            public static final String VIDEO_TOGGLE = "monitor:webrtc:video_toggle";
            public static final String SCREEN_SHARE_START = "monitor:webrtc:screen_share_start";
            public static final String SCREEN_SHARE_STOP = "monitor:webrtc:screen_share_stop";

            private Webrtc() {
            }
        }
    }

    /**
     * 视频模块主题 (video)
     * 用于视频监控相关功能
     */
    public static final class Video {

        private Video() {
        }

        /**
         * 设备相关
         */
        public static final class Device {
            public static final String STATUS = "video:device:status";
            public static final String ONLINE = "video:device:online";
            public static final String OFFLINE = "video:device:offline";
            public static final String ALARM = "video:device:alarm";

            private Device() {
            }
        }

        /**
         * 录像相关
         */
        public static final class Record {
            public static final String START = "video:record:start";
            public static final String STOP = "video:record:stop";
            public static final String PROGRESS = "video:record:progress";

            private Record() {
            }
        }

        /**
         * 流媒体相关
         */
        public static final class Stream {
            public static final String START = "video:stream:start";
            public static final String STOP = "video:stream:stop";
            public static final String ERROR = "video:stream:error";

            private Stream() {
            }
        }
    }
}
