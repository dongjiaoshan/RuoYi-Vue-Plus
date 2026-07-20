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
     * 主图 ossId（= t_plant_crop_info.image_oss_id，IMG-LIB-001 L1，内部解析用）。
     */
    private String imageOssId;

    /**
     * 缩略图 URL（IMG-LIB-001 4 层 resolver 回填，L2 兜底蔬菜默认图；前端 image 直接绑定，保证非空兜底）。
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
     * 处理后重量(kg) 合计（= 入库 + 月台；保留兼容旧消费方，列表卡已拆显两项）。
     */
    private BigDecimal handledWeight;

    /**
     * 毛菜保鲜室重量(kg) 合计（= 入库量 SUM(stock_in_weight)，毛菜列表「毛菜保鲜室重量」）。
     */
    private BigDecimal stockInWeight;

    /**
     * 送往月台重量(kg) 合计（= SUM(send_platform_weight)，毛菜列表「送往月台重量」）。
     */
    private BigDecimal sendPlatformWeight;

    /**
     * 损耗重量(kg) 合计。
     */
    private BigDecimal lossWeight;

    /**
     * 预计产量(kg) 合计（= SUM(地块面积 plot_area × 作物预计亩产 crop.predicted_per) − 灾害损失 disasterLoss；
     * 可处理地块预计产量之和扣减灾害损失后的净预计产量）。
     */
    private BigDecimal expectedYield;

    /**
     * 灾害损失(kg) 合计（= 灾害农事记录 t_plant_farm_records(farm_type='disaster') 按 plot_id SUM(loss_yield)，
     * 已从 expectedYield 中扣除；单列透出供 mp 展示扣减明细）。
     */
    private BigDecimal disasterLoss;

    /**
     * 待处理地块数（= 该作物下处理未完成的地块数量，处理未完成 = 汇总行 is_finish != 1；
     * 与地块明细页 processStatus!='done' 口径一致，mp 列表卡右上角展示）。
     */
    private Integer pendingPlotCount;

}
