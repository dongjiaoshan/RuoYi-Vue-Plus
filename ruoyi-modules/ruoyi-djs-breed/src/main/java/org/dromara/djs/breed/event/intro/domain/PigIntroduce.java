package org.dromara.djs.breed.event.intro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 引种业务表实体（BRD-EVENT-001）。
 *
 * <p>对应 DDL {@code t_farm_pig_introduce}（SYS-INIT-001 V202605200901 L266-293）。
 * 一行 = 一次引种动作（可能批量），每头猪在 {@code t_farm_pig_info} 仍单独建一行，
 * 通过 {@code t_farm_status_record.related_event_id} 反查回本表。</p>
 *
 * <p>外部引种（{@code introduce_type='external'}）：{@link #supplierId} +
 * {@link #proofOssIds} 强制必填，业务层校验。内部引种 / 调拨可缺省。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_introduce")
public class PigIntroduce extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 引种单号（{@code IBizCodeGenerator#INTRO_NO}）。 */
    private String introduceNo;

    /** 引种方式（字典 {@code introduce_from}：external 外部 / internal 内部）。 */
    private String introduceType;

    /** 引种日期。 */
    private LocalDate introduceDate;

    /** 供应商 ID（外部引种必填，关联 {@code t_md_supplier.id}）。 */
    private Long supplierId;

    /** 引入头数（≥ 1，批量引种时 = N）。 */
    private Integer pigCount;

    /** 起始耳号（批量引种时记录，便于查询；每头猪在 t_farm_pig_info 仍各自有 ear_no）。 */
    private String startEarNo;

    /** 品种编码（关联 t_farm_breed_info.breed_strain_code，第一层）。 */
    private String pigBreedCode;

    /** 品系编码（关联 t_farm_breed_info.breed_strain_code，第二层）。 */
    private String pigStrainCode;

    /** 性别（统一性别批量 F/M，混批 NULL）。 */
    private String pigSex;

    /** 凭证图 OSS ID 逗号分隔（外部引种必填）。 */
    private String proofOssIds;

    /** 目标栋舍 ID。 */
    private Long barnId;

    /** 目标栏位 ID。 */
    private Long penId;

    /** 内部引种关联的已存在猪只 ID（外部引种为 NULL，BRD-FIX-MP-INTRO-001）。 */
    private Long pigId;

    /** 引种人员（原型 86，V1 自由文本，BRD-FIX-MP-INTRO-001）。 */
    private String operator;

    /** 引种体重 kg（内部引种登记，原型 86，BRD-FIX-MP-INTRO-001）。 */
    private java.math.BigDecimal introduceWeight;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列。 */
    private Long delUnique;
}
