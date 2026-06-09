package org.dromara.djs.warehouse.veg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜处理菜品列表 VO（WMS-VEG-001 mp /crops 列表）。
 *
 * <p>按 crop_id 聚合 t_warehouse_vegetable_handle 的 4 个重量，一行 = 一个菜品（该菜品下所有地块合计）。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code VegCropVo}。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class VegCropVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作物 ID（= t_plant_crop_info.id，mp 进地块明细时回传）。
     */
    private Long cropId;

    /**
     * 作物名称。
     */
    private String cropName;

    /**
     * 缩略图 URL（= t_plant_crop_info.crop_image_preview，可空）。
     */
    private String thumbUrl;

    /**
     * 采摘重量(kg) 合计。
     */
    private BigDecimal harvestWeight;

    /**
     * 饲喂重量(kg) 合计。
     */
    private BigDecimal feedWeight;

    /**
     * 处理后重量(kg) 合计。
     */
    private BigDecimal handledWeight;

    /**
     * 损耗重量(kg) 合计。
     */
    private BigDecimal lossWeight;

}
