package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公猪配种次数聚合 VO（公猪列表 matingCount 真实统计，批量聚合避免 N+1）。
 *
 * <p>仅内部使用：{@code PigMapper.countBreedingByBoarEarNos} 按 {@code boar_ear_no}
 * {@code COUNT(*) GROUP BY boar_ear_no} 一次性回填公猪列表行的 {@code matingCount}。
 * 配种表关联公猪用耳号（{@code t_farm_pig_breeding.boar_ear_no}，本场公猪配种时填），
 * 故按 {@code boarEarNo} 而非 pigId 聚合。不直接出参给前端。</p>
 *
 * @author djs
 */
@Data
public class BoarMatingCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公猪耳号简版（t_farm_pig_breeding.boar_ear_no）。 */
    private String boarEarNo;

    /** 该公猪累计配种次数（COUNT 未软删配种记录）。 */
    private Integer total;
}
