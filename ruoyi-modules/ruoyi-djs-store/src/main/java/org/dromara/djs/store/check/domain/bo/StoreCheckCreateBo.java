package org.dromara.djs.store.check.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 新建门店盘点单入参 BO（STR-STOCK-001 admin 端）。
 *
 * <p>admin 门店管理员选门店 + 录入盘点日期 → 后端 INSERT header（{@code isHeader=1 / status='in_progress'}）
 * 一开始就锁门店（doc/10 §4.F-STR-07：新建即进行中，简化无 draft 中间态）。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Data
public class StoreCheckCreateBo {

    /**
     * 盘点门店 ID（FK → {@code t_md_store.id}）。
     */
    @NotNull(message = "盘点门店不能为空")
    private Long storeId;

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
