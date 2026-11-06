package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 汉字通假字（本字 → 借字，一对多）。
 *
 * <p>通假字是「本字 + 借字」的对应（如：说 通 悦、被 通 披、知 通 智）。
 * 一个字可以有多个借字，故独立成表而不是在 {@code edu_hanzi} 上加逗号串列：
 * 串列没法建索引，也没法反查「悦 是哪个字的通假」。</p>
 *
 * <p>检索口径：汉字库的关键字同时匹配本字、读音、释义与**借字**
 * —— 搜「悦」要能找到「说」这条记录。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("edu_hanzi_tongjia")
public class EduHanziTongjia {

    /**
     * 通假字记录标识
     */
    @TableId(type = IdType.AUTO)
    private Long tongjiaId;

    /**
     * 本字标识（edu_hanzi.hanzi_id）
     */
    private Long tongjiaHanziId;

    /**
     * 借字（如：悦）
     */
    private String tongjiaChar;

    /**
     * 说明，如：说，通「悦」，愉快
     */
    private String tongjiaNote;

    /**
     * 创建时间
     */
    private Date createTime;
}
