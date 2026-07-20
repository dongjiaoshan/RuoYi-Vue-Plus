package org.dromara.djs.store.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店现场生码「可追溯猪只」picker 出参（STORE-TRACE-ONSITE-001）。
 *
 * <p>映射养殖域 {@code PigAvailableVo}（earNo / pigSex / pigBreedLabel / ageDays），门店侧重命名
 * 字段为原型语义（猪只ID = 耳号 / 性别 / 品种 / 日龄）后回前端。所有 ID 走耳号业务键，不暴露 snowflake。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Data
public class TraceablePigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只ID（= 耳号简版，picker chip 主显 + 选中后 emit 值）。 */
    private String earNo;

    /**
     * 白条流水号（半只/整只白条唯一标识）。门店到货白条按半只一条（邓博 row13：门店按 white_bar_no
     * 识别哪个半只到货、现场分割）；耳号为空(外购)时以此作主标识。
     */
    private String whiteBarNo;

    /** 性别（字典 {@code djs_pig_sex}：F/M）。 */
    private String pigSex;

    /** 品种品系 label（格式 {品种}/{品系}）。 */
    private String pigBreedLabel;

    /** 日龄（天；空生日返 null）。 */
    private Integer ageDays;

    /** 白条到货重量 kg（= 该耳号已发货到店的白条 production.produce_quantity 合计）。 */
    private BigDecimal arrivedWeight;

    /** 剩余可打包重量 kg（= 到货重量 − 已现场打包重量；≤0 时前端禁选）。 */
    private BigDecimal remainingWeight;
}
