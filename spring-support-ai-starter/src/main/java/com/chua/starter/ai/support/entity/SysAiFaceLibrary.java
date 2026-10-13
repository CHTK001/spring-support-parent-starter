package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人脸底库条目，对应表 {@code sys_ai_face_library}。
 *
 * <p><b>向量不在本表。</b>人脸特征存在 jvector 里，本表只存元数据，靠
 * {@link #vectorId} 与向量库中同名的条目关联。因此删除本行时<b>必须</b>同步删除向量，
 * 反之亦然 —— 两者不在一个事务里，调用方（{@code FaceStores}）负责保证一致。</p>
 *
 * <p><b>归属账号必填。</b>{@code user_id} 是多用户隔离的根，没有它所有账号共用一个池子。
 * 写入方统一走 {@code AiUserContext} 取当前账号。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Data
@TableName("sys_ai_face_library")
public class SysAiFaceLibrary {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_face_library_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 展示名（人脸姓名）
     */
    @TableField("sys_ai_face_library_name")
    private String name;

    /**
     * 全名
     */
    @TableField("sys_ai_face_library_full_name")
    private String fullName;

    /**
     * 身份证号
     */
    @TableField("sys_ai_face_library_id_card")
    private String idCard;

    /**
     * 向量库中的条目标识，与 jvector 里的 key 同名 —— 这是两者的关联键。
     *
     * <p>注意不是本表主键：向量库按字符串 key 存取，而本表主键自增，
     * 两者只有在写入时显式对齐才能关联。删除/更新都必须以本字段为准。</p>
     */
    @TableField("sys_ai_face_library_vector_id")
    private String vectorId;

    /**
     * 缩略图（base64）
     */
    @TableField("sys_ai_face_library_thumbnail")
    private String thumbnail;

    /**
     * 所属分组。
     *
     * <p><b>属性名不能叫 {@code group}</b>：{@code group} 是 SQL 保留字，
     * MyBatis-Plus 在属性名与列名下划线转驼峰不一致时会生成
     * {@code ... AS <属性名>} 的别名，写成 {@code AS group} 就是语法错误 ——
     * 整条 {@code SELECT} 在 MariaDB 上直接报
     * {@code SQLSyntaxErrorException ... near 'group,...'}，
     * 于是「查一行人脸」必然抛异常，改档案/登记全部连带失败。
     * 列名保持 {@code sys_ai_face_library_group} 不变，只换属性名。</p>
     */
    @TableField("sys_ai_face_library_group")
    private String groupName;

    /**
     * 其余元数据（JSON 字符串）。
     *
     * <p>刻意做成「其余全放这里」而不是给每个属性加一列：登记表单的字段会随
     * 识别能力增强不断增加（年龄/性别/发型/颜值/备注…），每加一个就改表结构，
     * 而这些字段只有展示与检索价值、没有跨表运算需求。</p>
     */
    @TableField("sys_ai_face_library_meta")
    private String meta;

    /**
     * 归属账号。
     *
     * <p>不设默认值：宁可让历史无主行（NULL）显式可见，也不能让它悄悄落到某个账号名下。</p>
     */
    @TableField("sys_ai_face_library_user_id")
    private String userId;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_face_library_status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
