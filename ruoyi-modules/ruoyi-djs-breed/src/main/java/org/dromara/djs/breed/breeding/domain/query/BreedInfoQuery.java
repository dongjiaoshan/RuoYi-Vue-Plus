package org.dromara.djs.breed.breeding.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 育种信息（品种/品系）列表查询入参（BRD-MD-001）。
 *
 * <p>{@code breedStrain} 必传（admin 前端按品种 tab=1 / 品系 tab=2 过滤）。</p>
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

    /**
     * 创建时间范围 - 开始（含）。
     */
    private Date createTimeBegin;

    /**
     * 创建时间范围 - 结束（含）。
     */
    private Date createTimeEnd;

}
