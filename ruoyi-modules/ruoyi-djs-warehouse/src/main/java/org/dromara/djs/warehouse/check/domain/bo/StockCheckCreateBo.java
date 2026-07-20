package org.dromara.djs.warehouse.check.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 新建盘点单入参 BO（WMS-STOCK-001 admin 端）。
 *
 * <p>admin 仓库管理员选库位 + 录入盘点日期 → 后端 INSERT header（{@code isHeader=1 / status='in_progress'}）
 * 一开始就锁库位（doc/10 §2.F-WMS-05：新建即进行中，简化）。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
public class StockCheckCreateBo {

    /**
     * 盘点库位 ID（FK → {@code t_warehouse_location_info.id}）。
     */
    @NotNull(message = "盘点库位不能为空")
    private Long locationId;

    /**
     * 盘点日期。
     */
    @NotNull(message = "盘点日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkDate;

    /**
     * 备注（可选）。
     */
    private String remark;

}
