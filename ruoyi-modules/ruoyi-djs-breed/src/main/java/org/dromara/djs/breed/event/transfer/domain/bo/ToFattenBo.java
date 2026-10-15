package org.dromara.djs.breed.event.transfer.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 种母猪「转育肥猪」入参（D-0150；复用 admin row162 端点）。
 *
 * <p>业务：繁殖已结束且没有未断奶仔猪的种母猪可永久转育肥，确认后
 * 猪只状态 HB/DN/LC/KH/FQ → YF（育肥），猪只类型 sow → fattening，
 * 并在事件台账（{@code t_farm_status_record}）留 {@code TRANSFER} + {@code TO_FATTEN} 两条记录
 * —— 转育肥本身就是一次转移，位置没变也记，否则按「转移」查不到（admin row163）。</p>
 *
 * <p>新确认框仅提交猪只与当日日期；操作人默认登录用户，位置默认当前栋舍/栏位。
 * 转移字段保留兼容既有调用，空则沿用猪只现位置。</p>
 *
 * @author djs
 * @since admin row162
 */
@Data
public class ToFattenBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID（admin 端列表行直传）。 */
    @NotNull(message = "{pig.id_required}")
    private Long pigId;

    /** 转移日期（前端默认当日）。 */
    @NotNull(message = "{transfer.date.required}")
    private LocalDateTime transferDate;

    /** 转移栋舍 ID（可空 → 沿用猪只当前栋舍）。 */
    private Long newBarnId;

    /** 转移栏位 ID（可空 → 沿用猪只当前栏位）。 */
    private Long newPenId;

    /** 转移负责人 userId（养殖部人员下拉选中，snowflake 走 string；可空则回落登录用户）。 */
    private String operator;

    @Size(max = 500, message = "{transfer.remark.size}")
    private String remark;
}
