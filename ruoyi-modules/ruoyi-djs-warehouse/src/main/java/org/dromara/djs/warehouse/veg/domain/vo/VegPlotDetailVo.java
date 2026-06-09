package org.dromara.djs.warehouse.veg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜处理某菜品地块明细 VO（WMS-VEG-001 mp /crops/{cropId}/plots 列表）。
 *
 * <p>某菜品下每个种植记录（地块批次）一行。weighStatus / processStatus 驱动 mp 行内操作链接的
 * 橙(待办) / 灰(已完成) 切换。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code VegPlotDetailVo}。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class VegPlotDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 种植记录 ID（= t_warehouse_planting_record.id，mp 提交录入时回传）。
     */
    private Long plantingRecordId;

    /**
     * 地块 ID（= t_plant_plot_info.id）。
     */
    private Long plotId;

    /**
     * 地块编号（= t_plant_plot_info.plot_code，如 A-D-001）。
     */
    private String plotCode;

    /**
     * 地块面积(亩)（= t_plant_plot_info.plot_area）。
     */
    private BigDecimal plotArea;

    /**
     * 入库重量(kg)（= vegetable_handle.stock_in_weight）。
     */
    private BigDecimal stockInWeight;

    /**
     * 出库重量(kg)（= vegetable_handle.send_platform_weight）。
     */
    private BigDecimal stockOutWeight;

    /**
     * 称重(采摘录入)状态：pending=待办 / done=已完成（由 vegetable_handle.is_weighed 派生）。
     */
    private String weighStatus;

    /**
     * 处理(果蔬处理录入)状态：pending=待办 / done=已完成（由 vegetable_handle.is_finish 派生）。
     */
    private String processStatus;

    /**
     * 关联汇总 ID（= vegetable_handle.id，如已部分录入则非空）。
     */
    private Long handleId;

}
