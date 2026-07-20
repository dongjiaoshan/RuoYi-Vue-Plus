package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 「可出栏」育肥猪 VO（DJS-FIX-ADMIN-W22-001）。
 *
 * <p>仅服务 admin 端「指定猪只」对话框：列出 {@code pig_type='fattening'} 且未被其它非取消态需求
 * 占用的活体猪，admin 用户勾选后调 {@code assignPigs(earNos)}。</p>
 *
 * <p>所有 ID 字段不暴露（业务键走 {@link #earNo}），符合跨层契约 §2。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-001
 */
@Data
public class PigAvailableVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 耳号简版（业务键）。 */
    private String earNo;

    /** 性别（字典 {@code djs_pig_sex}）。 */
    private String pigSex;

    /** 品种品系 label，格式：{品种名}/{品系名}（若无品系仅品种名；皆缺则返耳号自带的品种品系编码兜底）。 */
    private String pigBreedLabel;

    /** 日龄（按 {@code birth_date} 与当前时间 DATEDIFF；空生日返 null）。 */
    private Integer ageDays;

    /** 最新一次背膘 mm（{@code t_farm_pig_growth.backfat_thickness}，measure_date DESC LIMIT 1；可空）。 */
    private BigDecimal lastBackfat;
}
