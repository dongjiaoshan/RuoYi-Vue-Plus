package org.dromara.djs.plant.pick.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘活动只读聚合报表查询条件。
 *
 * <p>2 项筛选：活动日期（单日）+ 作物名称（cropId）。</p>
 *
 * @author djs
 */
@Data
public class PickActivityQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id（作物名称下拉）。 */
    private Long cropId;

    /** 活动日期（单日，对齐原型）。 */
    private LocalDate activityDate;
}
