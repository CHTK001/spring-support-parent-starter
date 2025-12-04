package com.chua.socketio.support.constant;

/**
 * Socket.IO 主题命名规范常量
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
     * 系统模块主题 (system)
     * 用于系统级别的通用功能
     */
    public static final class System {

        private System() {
        }

        /**
         * 消息相关主题
         */
        public static final class Message {
            /**
             * 消息推送
             */
            public static final String PUSH = "system:message:push";
            /**
             * 通知推送
             */
            public static final String NOTIFICATION = "system:message:notification";
            /**
             * 消息已读
             */
            public static final String READ = "system:message:read";
            /**
             * 消息删除
             */
            public static final String DELETE = "system:message:delete";

            private Message() {
            }
        }

        /**
         * 用户相关主题
         */
        public static final class User {
            /**
             * 用户上线
             */
            public static final String ONLINE = "system:user:online";
            /**
             * 用户下线
             */
            public static final String OFFLINE = "system:user:offline";
            /**
             * 用户状态
             */
            public static final String STATUS = "system:user:status";
            /**
             * 踢出用户
             */
            public static final String KICK = "system:user:kick";

            private User() {
            }
        }

        /**
         * 配置相关主题
         */
        public static final class Config {
            /**
             * 配置更新
             */
            public static final String UPDATE = "system:config:update";
            /**
             * 版本更新
             */
            public static final String VERSION = "system:config:version";

            private Config() {
            }
        }

        /**
         * 权限相关主题
         */
        public static final class Auth {
            /**
             * Token过期
             */
            public static final String TOKEN_EXPIRED = "system:auth:token_expired";
            /**
             * 权限变更
             */
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
         * Docker/容器相关主题
         */
        public static final class Docker {
            /**
             * 镜像拉取进度
             */
            public static final String IMAGE_PULL_PROGRESS = "monitor:docker:image_pull_progress";
            /**
             * 镜像导出进度
             */
            public static final String IMAGE_EXPORT_PROGRESS = "monitor:docker:image_export_progress";
            /**
             * 镜像导入进度
             */
            public static final String IMAGE_IMPORT_PROGRESS = "monitor:docker:image_import_progress";
            /**
             * 容器状态
             */
            public static final String CONTAINER_STATUS = "monitor:docker:container_status";
            /**
             * 容器日志
             */
            public static final String CONTAINER_LOG = "monitor:docker:container_log";
            /**
             * 容器统计
             */
            public static final String CONTAINER_STATISTICS = "monitor:docker:container_statistics";
            /**
             * 容器事件
             */
            public static final String CONTAINER_EVENTS = "monitor:docker:container_events";
            /**
             * Docker操作开始
             */
            public static final String START = "monitor:docker:start";
            /**
             * Docker操作进度
             */
            public static final String PROGRESS = "monitor:docker:progress";
            /**
             * Docker操作完成
             */
            public static final String COMPLETE = "monitor:docker:complete";
            /**
             * Docker操作错误
             */
            public static final String ERROR = "monitor:docker:error";

            private Docker() {
            }
        }

        /**
         * 服务器相关主题
         */
        public static final class Server {
            /**
             * 服务器状态
             */
            public static final String STATUS = "monitor:server:status";
            /**
             * 服务器指标
             */
            public static final String METRICS = "monitor:server:metrics";
            /**
             * 服务器告警
             */
            public static final String ALERT = "monitor:server:alert";
            /**
             * 终端
             */
            public static final String TERMINAL = "monitor:server:terminal";
            /**
             * SSH连接
             */
            public static final String SSH_CONNECT = "monitor:server:ssh_connect";
            /**
             * SSH断开
             */
            public static final String SSH_DISCONNECT = "monitor:server:ssh_disconnect";
            /**
             * RDP连接
             */
            public static final String RDP_CONNECT = "monitor:server:rdp_connect";
            /**
             * VNC连接
             */
            public static final String VNC_CONNECT = "monitor:server:vnc_connect";
            /**
             * 连接状态变更
             */
            public static final String CONNECTION_STATUS = "monitor:server:connection_status";
            /**
             * 连接测试结果
             */
            public static final String CONNECTION_TEST = "monitor:server:connection_test";
            /**
             * 健康状态
             */
            public static final String HEALTH = "monitor:server:health";

            private Server() {
            }
        }

        /**
         * 软件相关主题
         */
        public static final class Software {
            /**
             * 安装进度
             */
            public static final String INSTALL_PROGRESS = "monitor:software:install_progress";
            /**
             * 同步进度
             */
            public static final String SYNC_PROGRESS = "monitor:software:sync_progress";

            private Software() {
            }
        }

        /**
         * 操作进度主题
         */
        public static final class Operation {
            /**
             * 操作进度
             */
            public static final String PROGRESS = "monitor:operation:progress";
            /**
             * 操作完成
             */
            public static final String COMPLETE = "monitor:operation:complete";
            /**
             * 操作错误
             */
            public static final String ERROR = "monitor:operation:error";

            private Operation() {
            }
        }

        /**
         * WebRTC相关主题
         */
        public static final class WebRTC {
            /**
             * 用户加入
             */
            public static final String USER_JOINED = "monitor:webrtc:user_joined";
            /**
             * 用户离开
             */
            public static final String USER_LEFT = "monitor:webrtc:user_left";
            /**
             * Offer
             */
            public static final String OFFER = "monitor:webrtc:offer";
            /**
             * Answer
             */
            public static final String ANSWER = "monitor:webrtc:answer";
            /**
             * ICE候选
             */
            public static final String ICE_CANDIDATE = "monitor:webrtc:ice_candidate";
            /**
             * 音频切换
             */
            public static final String AUDIO_TOGGLE = "monitor:webrtc:audio_toggle";
            /**
             * 视频切换
             */
            public static final String VIDEO_TOGGLE = "monitor:webrtc:video_toggle";
            /**
             * 屏幕共享开始
             */
            public static final String SCREEN_SHARE_START = "monitor:webrtc:screen_share_start";
            /**
             * 屏幕共享停止
             */
            public static final String SCREEN_SHARE_STOP = "monitor:webrtc:screen_share_stop";

            private WebRTC() {
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
         * 设备相关主题
         */
        public static final class Device {
            /**
             * 设备状态
             */
            public static final String STATUS = "video:device:status";
            /**
             * 设备上线
             */
            public static final String ONLINE = "video:device:online";
            /**
             * 设备下线
             */
            public static final String OFFLINE = "video:device:offline";
            /**
             * 设备告警
             */
            public static final String ALARM = "video:device:alarm";

            private Device() {
            }
        }

        /**
         * 录像相关主题
         */
        public static final class Record {
            /**
             * 开始录像
             */
            public static final String START = "video:record:start";
            /**
             * 停止录像
             */
            public static final String STOP = "video:record:stop";
            /**
             * 录像进度
             */
            public static final String PROGRESS = "video:record:progress";

            private Record() {
            }
        }

        /**
         * 流媒体相关主题
         */
        public static final class Stream {
            /**
             * 开始推流
             */
            public static final String START = "video:stream:start";
            /**
             * 停止推流
             */
            public static final String STOP = "video:stream:stop";
            /**
             * 推流错误
             */
            public static final String ERROR = "video:stream:error";

            private Stream() {
            }
        }
    }

    /**
     * 服务模块主题 (service)
     * 用于通用服务相关功能
     */
    public static final class Service {

        private Service() {
        }

        /**
         * 消息相关主题
         */
        public static final class Message {
            /**
             * 消息推送
             */
            public static final String PUSH = "service:message:push";
            /**
             * 消息通知
             */
            public static final String NOTIFICATION = "service:message:notification";
            /**
             * 消息已读
             */
            public static final String READ = "service:message:read";
            /**
             * 消息删除
             */
            public static final String DELETE = "service:message:delete";

            private Message() {
            }
        }

        /**
         * 短信相关主题
         */
        public static final class Sms {
            /**
             * 发送结果
             */
            public static final String RESULT = "service:sms:result";

            private Sms() {
            }
        }

        /**
         * 邮件相关主题
         */
        public static final class Email {
            /**
             * 发送结果
             */
            public static final String RESULT = "service:email:result";

            private Email() {
            }
        }
    }
}
