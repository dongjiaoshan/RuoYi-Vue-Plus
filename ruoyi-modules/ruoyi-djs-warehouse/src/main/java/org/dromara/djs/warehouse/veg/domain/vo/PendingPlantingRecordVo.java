package org.dromara.djs.warehouse.veg.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mp 端待处理种植记录 VO（WMS-VEG-001 mp /pending 列表）。
 *
 * <p>左联 t_warehouse_planting_record 与 t_warehouse_vegetable_handle，展示工人需要处理的批次。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class PendingPlantingRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * planting_record.id（mp 提交 record 时回传）。
     */
    private Long plantingRecordId;

    private Long plotId;

    private String plotName;

    private Long cropId;

    private String cropName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.sql.Date harvestDate;

    /**
     * 上游采摘重量(kg)（= planting_record.harvest_weight）。
     */
    private BigDecimal harvestWeight;

    /**
     * 处理状态 djs_veg_handle_status pending / processing / done。
     */
    private String handleStatus;

    /**
     * 关联 vegetable_handle.id（如已有部分录入则非空）。
     */
    private Long handleId;

    /**
     * 已累计处理后重量(kg)。
     */
    private BigDecimal handledWeight;

    /**
     * 已累计饲喂重量(kg)。
     */
    private BigDecimal feedWeight;

    /**
     * 已累计入库重量(kg)。
     */
    private BigDecimal stockInWeight;

    /**
     * 数据生成时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dataDate;

}
