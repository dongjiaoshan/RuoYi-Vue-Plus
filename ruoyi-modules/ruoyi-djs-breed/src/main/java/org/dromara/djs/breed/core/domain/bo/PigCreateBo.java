package org.dromara.djs.breed.core.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * INTRO 路径创建猪只入参（BRD-CORE-001）。
 *
 * <p>BRD-EVENT-001 引种 ticket 调 {@code PigCoreService#createPig}，
 * Service 内部完成：</p>
 * <ol>
 *   <li>INSERT t_farm_pig_info（lifecycle：种母猪取 HB，非种母猪空 ''）</li>
 *   <li>INSERT t_farm_status_record（old_status=null, new_status=HB 或 ''，event_type=INTRO）</li>
 * </ol>
 *
 * <p>子 ticket 拿到 pig.id 后再 INSERT 自己的 t_farm_pig_introduce 业务表
 * （那一步业务表 INSERT <b>不再</b>调 fireEvent，避免重复写 status_record）。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigCreateBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 耳号简版（业务键）。 */
    @NotBlank(message = "{pig.ear_no.required}")
    @Size(max = 32, message = "{pig.ear_no.size}")
    private String earNo;

    /** 耳号全版（可选，引种时通常由 SYS-INFRA-004 编码生成器算）。 */
    @Size(max = 32, message = "{pig.ear_tag.size}")
    private String earTag;

    /** 生命周期 ID（耳号复用次数，默认 1）。 */
    private Integer lifecycleId;

    /** 是否可回收复用（默认 0）。 */
    private Integer recyclable;

    /** 性别（F 母 / M 公）。 */
    @NotBlank(message = "{pig.sex.required}")
    @Pattern(regexp = "^[FM]$", message = "{pig.sex.invalid}")
    private String pigSex;

    /** 猪只类型（字典 pig_type：sow/boar/piglet/fattening）。 */
    @NotBlank(message = "{pig.type.required}")
    private String pigType;

    /** 品种编码。 */
    private String pigBreedCode;

    /** 品系编码。 */
    private String pigStrainCode;

    /** 父猪耳号（仔猪用）。 */
    private String fatherEar;

    /** 母猪耳号（仔猪用）。 */
    private String motherEar;

    /** 出生日期。 */
    private LocalDate birthDate;

    /** 引种日期。 */
    private LocalDate introduceDate;

    /** 引种方式（字典 introduce_from：internal/external）。 */
    private String introduceType;

    /** 供应商 ID（外部引种）。 */
    private Long supplierId;

    /** 当前栋舍 ID。 */
    private Long barnId;

    /** 当前栏位 ID。 */
    private Long penId;

    /** 备注。 */
    @Size(max = 500, message = "{pig.remark.size}")
    private String remark;
}
