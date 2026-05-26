package org.dromara.djs.breed.med.record.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端"3 天内可用批次"下拉返回项（BRD-MED-003）。
 *
 * <p>查询条件：</p>
 * <ul>
 *   <li>{@code t_breed_medicine_usage.usage_type = 'use'}</li>
 *   <li>{@code use_date >= NOW() - INTERVAL 3 DAY}</li>
 *   <li>{@code t_breed_medicine_batch.quantity > 0}</li>
 * </ul>
 *
 * <p>每个批次返回 1 行（按 batchId DISTINCT，合并多次领用同批次）。
 * mp picker 显示：药品名 + 批次号 + 剩余量。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Data
public class UsableBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批次 ID（mp picker 选中后写入 record.batch_id）。
     */
    private Long batchId;

    /**
     * 药品 ID（联动写 record.medicine_id）。
     */
    private Long medicineId;

    /**
     * 药品名（picker 展示）。
     */
    private String medicineName;

    /**
     * 批次号（picker 展示）。
     */
    private String batchNo;

    /**
     * 当前剩余库存（picker 展示）。
     */
    private BigDecimal quantity;

    /**
     * 最近一次领用 ID（用于回填 record.usage_id 追溯领用源头）。
     */
    private Long usageId;

    /**
     * 最近一次领用日期（便于 mp 判断"还在 3 天窗口里"）。
     */
    private String lastUseDate;

}
