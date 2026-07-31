package org.dromara.djs.breed.event.transfer.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后备种母猪「转为育肥猪」入参（admin row162）。
 *
 * <p>业务：猪只主表里对「猪只类型=种母猪 且 当前状态=后备」的猪执行转育肥 —— 录转移日期 / 转移负责人 /
 * 转移栋舍 / 转移栏位，确认后猪只状态 HB → YF（育肥），猪只类型 sow → fattening，
 * 并在事件台账（{@code t_farm_status_record}）留一条 {@code TO_FATTEN} 记录。</p>
 *
 * <p>转移栋舍 / 栏位默认取猪只当前位置（前端预填），故这里可空 —— 空则沿用猪只现位置。</p>
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
