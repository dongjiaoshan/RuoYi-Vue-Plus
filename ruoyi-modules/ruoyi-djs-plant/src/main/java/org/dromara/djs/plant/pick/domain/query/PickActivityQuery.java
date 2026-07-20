package org.dromara.djs.plant.pick.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采摘活动只读聚合报表查询条件。
 *
 * <p>2 项筛选：活动日期范围（beginDate ~ endDate）+ 作物名称（cropName 模糊）。</p>
 *
 * @author djs
 */
@Data
public class PickActivityQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物名称（模糊匹配 crop.crop_name）。 */
    private String cropName;

    /** 活动日期范围起（含）。 */
    private LocalDate beginDate;

    /** 活动日期范围止（含）。 */
    private LocalDate endDate;
}
