package org.dromara.djs.breed.med.record.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端"N 天内已领用药品"下拉返回项（BRD-MED-003 · 药品维度，废弃批次）。
 *
 * <p>查询条件：{@code t_breed_medicine_usage.usage_type='use'} 且 {@code use_date >= NOW()-3 DAY}，
 * 按 {@code medicine_id} DISTINCT（合并同药品多次领用）。药品详情由仓库 provider 解析。
 * mp picker 显示：药品名 + 规格 + 剩余库存 + 单位。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
public class UsableBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次 ID（药品废弃批次后恒 null；保留字段兼容旧数据 record.batch_id）。
     */
    private Long batchId;

    /**
     * 药品 ID（选中后写 record.medicine_id）。
     */
    private Long medicineId;

    /**
     * 药品名（picker 展示）。
     */
    private String medicineName;

    /**
     * 规格（picker 展示，取商品 product_spec）。
     */
    private String spec;

    /**
     * 单位（取商品 product_unit）。
     */
    private String unit;

    /**
     * 当前剩余库存（picker 展示）。
     */
    private BigDecimal quantity;

    /**
     * 批次号（药品废弃批次后恒 null；保留兼容旧数据展示）。
     */
    private String batchNo;

    /**
     * 最近一次领用 ID（药品维度不再回填，恒 null）。
     */
    private Long usageId;

    /**
     * 最近一次领用日期（药品维度不再回填，恒 null）。
     */
    private String lastUseDate;

}
