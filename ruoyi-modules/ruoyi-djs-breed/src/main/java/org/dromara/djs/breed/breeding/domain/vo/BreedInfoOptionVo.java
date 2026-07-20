package org.dromara.djs.breed.breeding.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 品种/品系下拉选项轻量出参（FIX-INTRO-001 #2）。
 *
 * <p>专供 mp 端外部引种「品种」/「品系」下拉——数据源 = {@code t_farm_breed_info}
 * 按 {@code breed_strain} 过滤（1=品种 / 2=品系），只需 {@code code + name} 两字段，
 * 不需要完整 {@link BreedInfoVo}。提交时 mp 存 {@code code}（写 pig.pig_breed_code /
 * pig_strain_code）。</p>
 *
 * @author djs
 * @since FIX-INTRO-001
 */
@Data
public class BreedInfoOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 品种/品系编码（业务码，提交存值）。 */
    private String code;

    /** 品种/品系名称（下拉显示）。 */
    private String name;
}
