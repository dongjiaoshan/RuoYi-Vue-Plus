package org.dromara.djs.plant.crop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 作物详情·农事信息子表行（FIX-PLT-AD-DETAIL-001）。
 *
 * <p>按 {@code cropId} 关联 {@code t_plant_farm_records} JOIN 地块 / 班组，
 * 对齐原型「作物详情 → 农事信息」tab（PLT-WORK-001 农事录入字段）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-DETAIL-001
 */
@Data
public class CropFarmworkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 农事记录 ID。 */
    private Long id;

    /** 农事日期。 */
    private Date farmDate;

    /** 农事类型（字典 djs_farm_work_type 值，前端 dict-tag 渲染）。 */
    private String farmType;

    /** 作业地块名。 */
    private String plotName;

    /** 作业人（处理班组名）。 */
    private String teamName;

    /** 说明（备注）。 */
    private String remark;
}
