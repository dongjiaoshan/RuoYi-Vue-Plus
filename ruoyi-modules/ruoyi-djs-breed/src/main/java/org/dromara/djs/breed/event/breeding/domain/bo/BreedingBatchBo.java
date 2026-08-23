package org.dromara.djs.breed.event.breeding.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量配种录入入参（mp 端「批量配种」）。
 *
 * <p>入口：配种录入页「猪只列表」右上「批量配种」→ 批量选择页多选母猪 → 回到配种页弹同一个录入框
 * （不显示单头猪只信息），填一次「配种公猪 / 配种日期 / 配种人员」提交本 BO。</p>
 *
 * <p>后端逐头复用单只 {@code recordBreeding}（状态机 HB/DN/LC/KH/FQ → PZ + counters 全部照跑），
 * 配种记录里落 N 条独立单头记录；整批同一事务，任一头失败全回滚（不做部分成功）。</p>
 *
 * @author djs
 */
@Data
public class BreedingBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待配种母猪 ID 列表（mp 批量选择页回传 pigId，雪花主键；至少 1 头）。 */
    @NotEmpty(message = "{breeding.batch.empty}")
    private List<Long> pigIds;

    /** 配种日期 + 时间（整批共用）。 */
    @NotNull(message = "{breeding.date.required}")
    private LocalDateTime breedingDate;

    /** 配种方式（字典 djs_mating_method；mp 端业务定死本场公猪 '1'）。 */
    @NotBlank(message = "{breeding.type.required}")
    @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "{breeding.type.invalid}")
    private String breedingType;

    /** 公猪耳号（breedingType=1 本场公猪时必填，整批共用）。 */
    @Size(max = 32, message = "{breeding.boar_ear.size}")
    private String boarEarNo;

    /** 配种精液字典 code（djs_semen；breedingType≠1 精液类时必填）。 */
    @Size(max = 64, message = "{breeding.semen_code.size}")
    private String semenCode;

    /** 凭证图片 OSS IDs 逗号分隔（整批共用）。 */
    @Size(max = 1024, message = "{breeding.proof.size}")
    private String proofOssIds;

    /** 配种人员 userId（整批共用；空则 service 回落当前登录态）。 */
    private Long operatorId;

    /** 备注（整批共用）。 */
    @Size(max = 500, message = "{breeding.remark.size}")
    private String remark;
}
