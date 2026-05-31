package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 育肥/仔猪详情「母系 / 父系谱系卡」出参（BRD-FIX-MP-DETAIL-SPLIT-001，原型 101 FATDET-02）。
 *
 * <p>从 {@code t_farm_pig_info} 本猪的 {@code mother_ear} / {@code father_ear} 反查父母猪只，
 * 拿到父母的耳号 / 品种 label / 日龄 / 胎次（母系有胎次，父系无）。关联缺失（耳号空 /
 * 查不到该父母猪）→ 对应字段返 {@code null}，mp 端谱系卡显「谱系信息暂缺」。</p>
 *
 * <p>品种 label 在 service 层翻好（djs_pig_breed 字典）；mp 端直接展示，不显原始 code。
 * 父/母 pigId 返 string（snowflake，全链路 string）给 mp 端点进父母详情。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Data
public class PedigreeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母系猪只 ID（snowflake；mp 端点进母详情；查不到 → null）。 */
    private Long damPigId;

    /** 母系耳号（t_farm_pig_info.mother_ear；缺 → null）。 */
    private String damEarNo;

    /** 母系品种 label（djs_pig_breed 翻译；缺 → null）。 */
    private String damBreed;

    /** 母系日龄（天，NOW − 母猪 birthDate；缺 → null）。 */
    private Integer damAgeDays;

    /** 母系胎次（缺 → null）。 */
    private Integer damParity;

    /** 父系猪只 ID（snowflake；查不到 → null）。 */
    private Long sirePigId;

    /** 父系耳号（t_farm_pig_info.father_ear；缺 → null）。 */
    private String sireEarNo;

    /** 父系品种 label（缺 → null）。 */
    private String sireBreed;

    /** 父系日龄（天；缺 → null）。 */
    private Integer sireAgeDays;
}
