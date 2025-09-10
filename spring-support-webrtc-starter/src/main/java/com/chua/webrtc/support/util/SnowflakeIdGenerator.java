package com.chua.webrtc.support.util;

/**
 * 雪花算法ID生成器
 * 用于生成唯一的房间号
 *
 * @author CH
 * @since 4.1.0
 */
public class SnowflakeIdGenerator {

    // 起始时间戳 (2024-01-01 00:00:00)
    private static final long START_TIMESTAMP = 1704067200000L;

    // 序列号占用的位数
    private static final long SEQUENCE_BIT = 12;
    // 机器标识占用的位数
    private static final long MACHINE_BIT = 5;
    // 数据中心占用的位数
    private static final long DATACENTER_BIT = 5;

    // 最大值
    private static final long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BIT);
    private static final long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    // 向左的位移
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;
    private static volatile SnowflakeIdGenerator instance;
    private final long datacenterId;  // 数据中心
    private final long machineId;     // 机器标识
    private long sequence = 0L;       // 序列号
    private long lastTimestamp = -1L; // 上一次时间戳

    private SnowflakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    /**
     * 获取单例实例
     *
     * @return SnowflakeIdGenerator实例
     */
    public static SnowflakeIdGenerator getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdGenerator.class) {
                if (instance == null) {
                    // 默认使用数据中心ID=1，机器ID=1
                    instance = new SnowflakeIdGenerator(1, 1);
                }
            }
        }
        return instance;
    }

    /**
     * 获取自定义配置的实例
     *
     * @param datacenterId 数据中心ID
     * @param machineId    机器ID
     * @return SnowflakeIdGenerator实例
     */
    public static SnowflakeIdGenerator getInstance(long datacenterId, long machineId) {
        return new SnowflakeIdGenerator(datacenterId, machineId);
    }

    /**
     * 产生下一个ID
     *
     * @return 唯一ID
     */
    public synchronized long nextId() {
        long currTimestamp = getNewTimestamp();
        if (currTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }

        if (currTimestamp == lastTimestamp) {
            // 相同毫秒内，序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 同一毫秒的序列数已经达到最大
            if (sequence == 0L) {
                currTimestamp = getNextMill();
            }
        } else {
            // 不同毫秒内，序列号置为0
            sequence = 0L;
        }

        lastTimestamp = currTimestamp;

        return (currTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT // 时间戳部分
                | datacenterId << DATACENTER_LEFT                   // 数据中心部分
                | machineId << MACHINE_LEFT                         // 机器标识部分
                | sequence;                                         // 序列号部分
    }

    /**
     * 获取下一个毫秒
     *
     * @return 下一个毫秒的时间戳
     */
    private long getNextMill() {
        long mill = getNewTimestamp();
        while (mill <= lastTimestamp) {
            mill = getNewTimestamp();
        }
        return mill;
    }

    /**
     * 获取新的时间戳
     *
     * @return 当前时间戳
     */
    private long getNewTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 生成房间号（确保为正数且在合理范围内）
     *
     * @return 房间号
     */
    public long generateRoomNumber() {
        long id = nextId();
        // 确保房间号为正数，并且在合理范围内（9位数字）
        return Math.abs(id % 900000000L) + 100000000L;
    }
}