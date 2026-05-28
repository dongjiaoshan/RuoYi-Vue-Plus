package org.dromara.djs.breed.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 栏位 picker 轻量 VO（D9 closing Group D — PenPicker）。
 *
 * <p>专给 mp 端 {@code PenPicker} 用。工人选完栋舍后联动 → 拉该栋舍下启用的栏位 →
 * 选中 emit penCode（业务码 string，如 'P03'）。</p>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Data
public class PenPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 栏位主键（snowflake string，list key 用）。 */
    private Long id;

    /** 所属栋舍 ID（mp 不直接用，仅校验）。 */
    private Long barnId;

    /** 栏位编码（业务码，picker emit 值）。 */
    private String penCode;

    /** 栏位名称（picker 主展示）。 */
    private String penName;

    /** 栏位类型（字典 djs_pen_type，picker tag 展示）。 */
    private String penType;

    /** 容量。 */
    private Integer capacity;

    /** 当前存栏。 */
    private Integer usedCount;
}
