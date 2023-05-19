package com.boren.biz.entity.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;

@Data
public class EleDict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String dictName;

    private String dictCode;

    private Integer sort;

    private String description;

    private Integer status;

    private Integer deleted;

}
