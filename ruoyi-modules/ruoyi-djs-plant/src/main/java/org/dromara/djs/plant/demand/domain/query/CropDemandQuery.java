package org.dromara.djs.plant.demand.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 作物需求查询参数（V6-R152 / V6-R153 两个页面共用同一套搜索条件）。
 *
 * @author djs
 * @since V6-R152
 */
@Data
public class CropDemandQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求内容（模糊）。 */
    private String demandContent;

    /** 需求分类（字典 code，精确）。 */
    private String demandCategory;

    /** 需求状态（pending / replied；不传 = 全部）。 */
    private String demandStatus;

    /** 需求日期范围起（含）。 */
    private LocalDate beginDate;

    /** 需求日期范围止（含）。 */
    private LocalDate endDate;
}
