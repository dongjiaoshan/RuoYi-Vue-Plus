package org.dromara.djs.breed.breeding.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 育种配置（配种关系）列表查询入参（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BreedConfigQuery extends BaseEntity {

    /**
     * 类型（1=品种配种 / 2=品系配种）。
     */
    private Integer breedStrain;

    /**
     * 母本编码（精确匹配）。
     */
    private String motherCode;

    /**
     * 父本编码（精确匹配）。
     */
    private String fatherCode;

    /**
     * 仔代编码（精确匹配）。
     */
    private String cubCode;

    /**
     * 创建时间范围 - 开始（含）。
     */
    private Date createTimeBegin;

    /**
     * 创建时间范围 - 结束（含）。
     */
    private Date createTimeEnd;

}
