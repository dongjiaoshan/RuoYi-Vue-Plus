package org.dromara.djs.breed.event.farrow.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分娩事件录入入参（BRD-EVENT-002 FARROW）。
 *
 * <p>触发状态机 transition PH → FM；分娩只记窝产仔数，仔猪个体由 BRD-EVENT-003
 * 耳标流程创建（by-design 无 farrow→eartag 事件联动）。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
public class FarrowBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（必填，pig_info.id）。 */
    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    /** 分娩日期（含时间，工人 mp 端默认 now）。 */
    @NotNull(message = "farrow.date.required")
    private LocalDateTime farrowDate;

    /**
     * 关联配种记录 ID（FK → t_farm_pig_breeding.id）。
     * 默认 service 端按 pig.matingId 自动取（最近一次配种）；如指定则以此为准。
     */
    private Long breedingId;

    /** 总仔猪数（活产 + 死胎 + 木乃伊 + 弱仔）。 */
    @NotNull(message = "farrow.total_born.required")
    @Min(value = 0, message = "farrow.total_born.invalid")
    private Integer totalBorn;

    /** 活产仔数（≤ totalBorn）。 */
    @NotNull(message = "farrow.live_born.required")
    @Min(value = 0, message = "farrow.live_born.invalid")
    private Integer liveBorn;

    /** 死胎数。 */
    @Min(value = 0, message = "farrow.dead_born.invalid")
    private Integer deadBorn;

    /** 木乃伊数。 */
    @Min(value = 0, message = "farrow.mummy_born.invalid")
    private Integer mummyBorn;

    /** 弱仔数。 */
    @Min(value = 0, message = "farrow.weak_born.invalid")
    private Integer weakBorn;

    /** 仔猪公数。 */
    @Min(value = 0, message = "farrow.male_count.invalid")
    private Integer maleCount;

    /** 仔猪母数。 */
    @Min(value = 0, message = "farrow.female_count.invalid")
    private Integer femaleCount;

    /** 出生总重 kg。 */
    private BigDecimal totalWeight;

    /** 平均出生重 kg。 */
    private BigDecimal avgWeight;

    /** 凭证图片 OSS IDs 逗号分隔（mp 端分娩现场照片，与 PigIntroBo 对齐）。 */
    @Size(max = 1024, message = "farrow.proof.size")
    private String proofOssIds;

    /** 备注。 */
    @Size(max = 500, message = "farrow.remark.size")
    private String remark;
}
