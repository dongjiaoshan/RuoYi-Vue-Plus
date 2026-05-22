package org.dromara.djs.breed.breeding.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 育种信息（品种/品系）列表查询入参（BRD-MD-001）。
 *
 * <p>{@code breedStrain} 必传（admin 前端 4 tab 切换时按此过滤：tab1=1 / tab3=2）。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BreedInfoQuery extends BaseEntity {

    /**
     * 类型（1=品种 / 2=品系）。
     */
    private Integer breedStrain;

    /**
     * 品种/品系编码（精确匹配）。
     */
    private String breedStrainCode;

    /**
     * 品种/品系名称（模糊匹配）。
     */
    private String breedStrainName;

    /**
     * 父级编码（精确匹配）。
     */
    private String parentCode;

}
