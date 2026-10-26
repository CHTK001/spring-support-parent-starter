package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸重识别底库条目，对应表 {@code sys_ai_reid_library}。
 *
 * <p><b>重识别底库与人脸底库的区别。</b>人脸底库是「<b>已登记的人</b>」——
 * 谁是谁，用于 1:N 认人；重识别底库是「<b>观测过的人</b>」——同一个人跨摄像头
 * 走过哪些地方，用于把散落的画面观测汇聚成一条轨迹。两者可以互相引用
 * （{@link #faceId} 指向人脸底库行，表示"这次观测认出是张三"），但定位不同：
 * 人脸底库以人为单位，重识别底库以<b>观测</b>为单位。</p>
 *
 * <p><b>向量不在本表</b>，与 {@link SysAiFaceLibrary} 同口径：特征存 jvector，
 * 本表用 {@link #vectorId} 关联。本表只存标量元数据 ——
 * 因此经纬度这类「描述这条观测在哪」的字段放这里，<b>不会改变向量存储</b>。</p>
 *
 * <p><b>轨迹是汇聚结果，不是另一份存储。</b>一条 track 由若干条本表记录
 * （同 {@link #trackId}、按时间排序）汇聚而来；删除底库行即等于从轨迹中移除该次观测。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Data
@TableName("sys_ai_reid_library")
public class SysAiReidLibrary {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_reid_library_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 关联的人脸底库行 ID（可空）。
     *
     * <p>有值表示这次观测在人脸底库中认出了某人；为空表示只是「见过这张脸」，
     * 尚未与人脸底库中的档案关联。</p>
     */
    @TableField("sys_ai_reid_library_face_id")
    private Integer faceId;

    /**
     * 展示名。
     */
    @TableField("sys_ai_reid_library_name")
    private String name;

    /**
     * 人名（与人脸底库对齐的展示字段，可空）。
     */
    @TableField("sys_ai_reid_library_face_name")
    private String faceName;

    /**
     * 向量库中的条目标识，与 jvector 里的 key 同名 —— 两者的关联键。
     */
    @TableField("sys_ai_reid_library_vector_id")
    private String vectorId;

    /**
     * 缩略图（base64）。
     */
    @TableField("sys_ai_reid_library_thumbnail")
    private String thumbnail;

    /**
     * 所属分组。
     *
     * <p>属性名不能叫 {@code group}（SQL 保留字），列名保持
     * {@code sys_ai_reid_library_group} 不变 —— 与 {@link SysAiFaceLibrary#getGroupName()}
     * 同一个坑。</p>
     */
    @TableField("sys_ai_reid_library_group")
    private String groupName;

    /**
     * 其余元数据（JSON 字符串）：摄像头、来源帧、时间戳等观测上下文。
     */
    @TableField("sys_ai_reid_library_meta")
    private String meta;

    /**
     * 归属账号（多账号隔离的根；NULL 表示为无主历史行）。
     */
    @TableField("sys_ai_reid_library_user_id")
    private String userId;

    /**
     * 跨帧轨迹 ID：同一条轨迹的多次观测共享它。
     *
     * <p><b>轨迹不是另一份存储，而是本表的汇聚结果</b>：按 {@code track_id} 分组、
     * 按时间排序，就得到「这个人被哪些摄像头在何时拍到」。这样只有一份真相 ——
     * 若另建轨迹表，删观测/迁轨迹要维护两处一致，迟早分叉。</p>
     */
    @TableField("sys_ai_reid_library_track_id")
    private String trackId;

    /**
     * 摄像头标识（跨镜判定依据）。
     */
    @TableField("sys_ai_reid_library_camera_id")
    private String cameraId;

    /**
     * 纬度（非必填，WGS84）。
     *
     * <p>非必填：很多摄像头没有定位能力，强行必填只会让人乱填。
     * 用 {@link BigDecimal} 而不是 {@code double}，理由同
     * {@link SysAiFaceLibrary#getLat()}：经纬度要参与等值/范围比较，
     * 浮点误差会让「相同坐标」查不出来。</p>
     */
    @TableField("sys_ai_reid_library_lat")
    private BigDecimal lat;

    /**
     * 经度（非必填，语义同 {@link #lat}）。
     */
    @TableField("sys_ai_reid_library_lng")
    private BigDecimal lng;

    /**
     * 状态：1 启用，0 停用。
     */
    @TableField("sys_ai_reid_library_status")
    private Integer status;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
