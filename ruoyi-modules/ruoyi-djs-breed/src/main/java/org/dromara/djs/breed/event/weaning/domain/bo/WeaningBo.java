package org.dromara.djs.breed.event.weaning.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 断奶事件入参（BRD-EVENT-002 WEAN）。状态机 FM → DN。
 *
 * <p>BRD-FIX-MP-EVENT-BREED-IA-001（Kevin 2026-05-29 拍板）：原型 91 逐头录重——
 * mp 端断奶仔猪逐头录体重，FE 汇总 weanedWeight + avgWeanedWeight，并把每头明细放
 * {@link #details}。service 主记录 + 明细同事务批量 INSERT。details 缺省（admin 端 / 老调用方）
 * 时退化为仅汇总录入，向后兼容。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class WeaningBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /**
     * 关联分娩 ID（mp 端从近期分娩 picker 默认选最近一次，可空）。
     * <p>空时 service 按 {@code pigId} 自动取该母猪最近一次分娩兜底（FIX-BRD-MP-WEAN-FORM-001 K065/K079，
     * 决策 Y2(b)：去前端强制 + 后端自动匹配最近分娩），仍无分娩记录才抛明确异常。</p>
     */
    private Long farrowId;

    @NotNull(message = "weaning.date.required")
    private LocalDateTime weaningDate;

    /** 断奶时仔猪数（实际活仔数）。 */
    @NotNull(message = "weaning.count.required")
    @Min(value = 0, message = "weaning.count.invalid")
    private Integer weanedCount;

    /** 断奶总重 kg（OQ-11 fallback：母猪汇总，不逐头）。 */
    private BigDecimal weanedWeight;

    /** 断奶平均重 kg（若 weanedWeight + count 都给则 service 自动算）。 */
    private BigDecimal avgWeanedWeight;

    /**
     * 逐头录重明细（原型 91；mp 端每头一行序号 + 体重）。
     * 缺省 / 空 → 仅汇总录入（向后兼容）；非空时 service 同事务批量 INSERT t_farm_pig_weaning_detail。
     */
    @Valid
    private List<WeaningDetailBo> details;

    @Size(max = 500, message = "weaning.remark.size")
    private String remark;

    /** 录入人员 userId（mp EmployeePicker 选，支持替别人代录；admin / 老调用方缺省时 service fallback 登录态）。 */
    private Long operatorId;

    /**
     * 断奶后转移目标栋舍编码（mp 端工人选；与 {@link #transferBarnId} 二选一）。
     * <p>非空 → service 在断奶事务内联触发母猪转移（_open-issues #32a 决策 a：断奶时一步到位填转移）；
     * 空 → 不转移，仅断奶。</p>
     */
    private String transferBarnCode;

    /** 断奶后转移目标栋舍 ID（admin 端可直传；与 {@link #transferBarnCode} 二选一）。 */
    private Long transferBarnId;

    /** 断奶后转移目标栏位编码（可空，未细分到栏位时仅切栋舍；与 {@link #transferPenId} 二选一）。 */
    private String transferPenCode;

    /** 断奶后转移目标栏位 ID（admin 端可直传；与 {@link #transferPenCode} 二选一）。 */
    private Long transferPenId;

    /**
     * 仔猪转移目标栋舍编码（row22 客户 0618：母猪仔猪转移目标「独立」，可不同栋舍——反转决策 G4(a)）。
     * <p>非空 → service 把该分娩已贴标仔猪转到此目标；缺省 → 回退母猪目标 {@link #transferBarnCode}
     * （向后兼容老调用方 / admin 端）。与 {@link #pigletTransferBarnId} 二选一（mp 端传 code）。</p>
     */
    private String pigletTransferBarnCode;

    /** 仔猪转移目标栋舍 ID（admin 端可直传；与 {@link #pigletTransferBarnCode} 二选一；缺省回退母猪目标）。 */
    private Long pigletTransferBarnId;

    /** 仔猪转移目标栏位编码（可空，未细分到栏位时仅切栋舍；与 {@link #pigletTransferPenId} 二选一；缺省回退母猪目标）。 */
    private String pigletTransferPenCode;

    /** 仔猪转移目标栏位 ID（admin 端可直传；与 {@link #pigletTransferPenCode} 二选一；缺省回退母猪目标）。 */
    private Long pigletTransferPenId;
}
