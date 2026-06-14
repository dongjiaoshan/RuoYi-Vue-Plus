package org.dromara.djs.breed.farm.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.farm.domain.Barn;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 栋舍视图对象（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@AutoMapper(target = Barn.class)
public class BarnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 栋舍 ID。
     */
    private Long id;

    /**
     * 栋舍编码。
     */
    private String barnCode;

    /**
     * 栋舍名称。
     */
    private String barnName;

    /**
     * 栋舍类型（字典 {@code djs_barn_type}）。
     */
    private String barnType;

    /**
     * 容量。
     */
    private Integer capacity;

    /**
     * 当前存栏（维护列；列表展示用实时计算的 {@link #liveCount}）。
     */
    private Integer currentCount;

    /**
     * 状态（1=启用 / 0=停用）。
     */
    private Integer barnStatus;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间（列表「更新时间」列）。
     */
    private Date updateTime;

    /**
     * 更新人 ID（翻译成昵称用）。
     */
    private Long updateBy;

    /**
     * 更新人昵称（列表「更新人员」列）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

    /**
     * 大栏数量（列表列；Service 按 pen_type=big 实时统计，非表字段）。
     */
    private Integer bigPenCount;

    /**
     * 限位栏数量（列表列；Service 按 pen_type=stall 实时统计，非表字段）。
     */
    private Integer limitPenCount;

    /**
     * 产床数量（列表列；Service 按 pen_type=farrow 实时统计，非表字段）。
     */
    private Integer bedCount;

    /**
     * 散栏数量（列表列；Service 按 pen_type=scatter 实时统计，非表字段）。
     */
    private Integer scatterPenCount;

    /**
     * 保育栏数量（列表列；Service 按 pen_type=nursery_pen 实时统计，非表字段）。
     */
    private Integer nurseryPenCount;

    /**
     * 当前存栏（列表「当前猪只存栏数量」列；Service 实时统计存活猪只，非表字段）。
     */
    private Integer liveCount;

}
