package org.dromara.djs.breed.farm.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.farm.domain.Pen;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 栏位视图对象（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@AutoMapper(target = Pen.class)
public class PenVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 栏位 ID。
     */
    private Long id;

    /**
     * 所属栋舍 ID。
     */
    private Long barnId;

    /**
     * 栏位编码。
     */
    private String penCode;

    /**
     * 栏位名称。
     */
    private String penName;

    /**
     * 栏位类型（字典 {@code djs_pen_type}）。
     */
    private String penType;

    /**
     * 容量（由 {@code penType} 推导：产床 farrow=1 / 限位栏 stall=1 / 大栏 big=100 /
     * 散栏 scatter=100 / 保育栏 nursery_pen=100；非 DB capacity 列，row59）。
     */
    private Integer capacity;

    /**
     * 当前存栏（DB current_count 列，业务事件维护）。
     */
    private Integer currentCount;

    /**
     * 当前在栏头数（容量口径，row59）：未删 + 非终止态（current_status&lt;&gt;'END'）+ 排除仔猪（pig_type='piglet'）。
     * 用于超容判断（与按 pen_type 推导的 {@link #capacity} 对照），仔猪不计入。
     */
    private Integer usedCount;

    /**
     * 状态（1=启用 / 0=停用）。
     */
    private Integer penStatus;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private Date createTime;

}
