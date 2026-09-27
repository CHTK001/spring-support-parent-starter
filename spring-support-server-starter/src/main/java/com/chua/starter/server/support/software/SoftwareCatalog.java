package com.chua.starter.server.support.software;

import java.util.ArrayList;
import java.util.List;

/**
 * 软件识别目录：把进程名 / 命令行特征映射为软件类型。
 *
 * <p>两段匹配：①命令行关键字优先（JVM 托管的 Elasticsearch / Kafka / ZooKeeper / Tomcat 等，
 * 进程名都是 java，需靠命令行区分）；②进程名精确匹配（Redis / MySQL / Nginx ...）。
 * 通用 java 放最后，避免把上述中间件误标成普通 Java 应用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class SoftwareCatalog {

    private SoftwareCatalog() {
    }

    /**
     * 软件定义
     *
     * @param type        类型标识
     * @param displayName 展示名称
     * @param names       进程名候选（小写精确），可为空
     * @param cmdKeywords 命令行关键字候选（小写包含），可为空
     */
    private record Def(String type, String displayName, String[] names, String[] cmdKeywords) {

        /**
         * 规范构造器：数组组件做防御性拷贝。
         *
         * <p>value class 前置条件——数组组件必须深不可变。两个数组按契约均可为空
         * （纯进程名匹配项的 {@code cmdKeywords} 传 null），故保留 null 语义。</p>
         *
         * @param type        类型标识
         * @param displayName 展示名称
         * @param names       进程名候选（小写精确），可为空
         * @param cmdKeywords 命令行关键字候选（小写包含），可为空
         */
        Def {
            names = names == null ? null : names.clone();
            cmdKeywords = cmdKeywords == null ? null : cmdKeywords.clone();
        }

        /**
         * 访问器覆写：返回内部数组的副本。
         *
         * @return 进程名候选副本，可为 null
         */
        @Override
        public String[] names() {
            return names == null ? null : names.clone();
        }

        /**
         * 访问器覆写：返回内部数组的副本。
         *
         * @return 命令行关键字候选副本，可为 null
         */
        @Override
        public String[] cmdKeywords() {
            return cmdKeywords == null ? null : cmdKeywords.clone();
        }

        /**
         * 进程名是否命中
         * @param name 进程名小写
         * @return 命中
         */
        boolean nameMatches(String name) {
            if (names == null) {
                return false;
            }
            for (String n : names) {
                if (n.equals(name)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 命令行是否命中（要求进程名也匹配，若定义了 names）
         * @param name 进程名小写
         * @param cmd  命令行小写
         * @return 命中
         */
        boolean cmdMatches(String name, String cmd) {
            if (cmdKeywords == null || cmd == null || cmd.isBlank()) {
                return false;
            }
            if (names != null && names.length > 0 && !nameMatches(name)) {
                return false;
            }
            for (String k : cmdKeywords) {
                if (cmd.contains(k)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 命令行关键字优先的 JVM 托管产品（顺序即优先级） */
    private static final Def[] CMD_FIRST = {
        new Def("elasticsearch", "Elasticsearch", new String[]{"java", "javaw"},
                new String[]{"org.elasticsearch.bootstrap", "elasticsearch"}),
        new Def("kafka", "Apache Kafka", new String[]{"java", "javaw"},
                new String[]{"kafka.kafka", "/kafka/", "kafka_"}),
        new Def("zookeeper", "Apache ZooKeeper", new String[]{"java", "javaw"},
                new String[]{"org.apache.zookeeper", "zookeeper"}),
        new Def("skywalking", "SkyWalking OAP", new String[]{"java", "javaw"},
                new String[]{"oap-server", "skywalking"}),
        new Def("tomcat", "Apache Tomcat", new String[]{"java", "javaw"},
                new String[]{"catalina", "bootstrap.jar", "tomcat"}),
        new Def("arthas", "Arthas", new String[]{"java", "javaw"},
                new String[]{"arthas-boot", "arthas"}),
        new Def("rabbitmq", "RabbitMQ", new String[]{"beam.smp", "erl"},
                new String[]{"rabbitmq"}),
    };

    /** 进程名精确匹配（通用 java 放最后） */
    private static final Def[] BY_NAME = {
        new Def("redis-sentinel", "Redis Sentinel", new String[]{"redis-sentinel"}, null),
        new Def("redis", "Redis", new String[]{"redis-server"}, null),
        new Def("mysql", "MySQL", new String[]{"mysqld"}, null),
        new Def("mariadb", "MariaDB", new String[]{"mariadbd"}, null),
        new Def("postgres", "PostgreSQL", new String[]{"postgres"}, null),
        new Def("mongodb", "MongoDB", new String[]{"mongod"}, null),
        new Def("nginx", "Nginx", new String[]{"nginx"}, null),
        new Def("node", "Node.js", new String[]{"node"}, null),
        new Def("docker", "Docker", new String[]{"dockerd"}, null),
        new Def("containerd", "containerd", new String[]{"containerd"}, null),
        new Def("memcached", "Memcached", new String[]{"memcached"}, null),
        new Def("etcd", "etcd", new String[]{"etcd"}, null),
        new Def("minio", "MinIO", new String[]{"minio"}, null),
        new Def("redis-exporter", "Redis Exporter", new String[]{"redis_exporter"}, null),
        new Def("node-exporter", "Node Exporter", new String[]{"node_exporter"}, null),
        new Def("java", "Java 应用", new String[]{"java", "javaw"}, null),
    };

    /**
     * 识别单个进程。
     *
     * @param rawName 进程名（任意大小写）
     * @param rawCmd  命令行 / 路径，可为空
     * @return 命中的软件类型标识；非已知软件返回 null
     */
    public static String match(String rawName, String rawCmd) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String name = rawName.toLowerCase(java.util.Locale.ROOT).trim();
        String cmd = rawCmd == null ? "" : rawCmd.toLowerCase(java.util.Locale.ROOT);
        for (Def d : CMD_FIRST) {
            if (d.cmdMatches(name, cmd)) {
                return d.type();
            }
        }
        for (Def d : BY_NAME) {
            if (d.nameMatches(name)) {
                return d.type();
            }
        }
        return null;
    }

    /**
     * 仅按进程名判断是否为「可能命中」的候选（不读命令行）。
     *
     * <p>Windows 上 OSHI 读取单个进程命令行约 30+ms，不能对全部进程读取。
     * 先用本方法筛出进程名出现在任一软件定义（含 CMD_FIRST 的 java / erl 等）中的候选，
     * 再仅对候选读取命令行做 CMD_FIRST 细分。</p>
     *
     * @param rawName 进程名（任意大小写）
     * @return 进程名命中任一软件定义返回 true
     */
    public static boolean nameKnown(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return false;
        }
        String name = rawName.toLowerCase(java.util.Locale.ROOT).trim();
        for (Def d : CMD_FIRST) {
            if (d.nameMatches(name)) {
                return true;
            }
        }
        for (Def d : BY_NAME) {
            if (d.nameMatches(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取软件类型对应的展示名称。
     *
     * @param type 类型标识
     * @return 展示名称，未知返回类型本身
     */
    public static String displayName(String type) {
        for (Def d : CMD_FIRST) {
            if (d.type().equals(type)) {
                return d.displayName();
            }
        }
        for (Def d : BY_NAME) {
            if (d.type().equals(type)) {
                return d.displayName();
            }
        }
        return type;
    }

    /**
     * 便捷构造一个软件实例。
     *
     * @param type      类型标识（由 {@link #match} 得到）
     * @param pid       进程号
     * @param cpu       CPU 使用率
     * @param memMb     常驻内存 MB
     * @param uptimeSec 运行时长秒
     * @param cmd       命令行
     * @return 软件实例
     */
    public static SoftwareInstanceVO instance(String type, int pid, double cpu, double memMb,
                                              long uptimeSec, String cmd) {
        SoftwareInstanceVO vo = new SoftwareInstanceVO();
        vo.setType(type);
        vo.setDisplayName(displayName(type));
        vo.setPid(pid);
        vo.setCpu(round1(cpu));
        vo.setMemMb(round1(memMb));
        vo.setUptimeSec(Math.max(0, uptimeSec));
        vo.setCmd(cmd == null ? "" : cmd);
        return vo;
    }

    /**
     * 保留一位小数。
     * @param v 原值
     * @return 结果
     */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /**
     * 收集当前所有已识别的软件类型（供前端图例 / 测试）。
     * @return 类型列表
     */
    public static List<String> types() {
        List<String> all = new ArrayList<>();
        for (Def d : CMD_FIRST) {
            if (!all.contains(d.type())) {
                all.add(d.type());
            }
        }
        for (Def d : BY_NAME) {
            if (!all.contains(d.type())) {
                all.add(d.type());
            }
        }
        return all;
    }
}
