package org.dromara.djs.breed.pig.domain.query;

import lombok.Data;

/**
 * 猪只基础信息查询条件。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
public class PigInfoQuery {

    /**
     * 耳号（精确匹配）。
     */
    private String earTag;

    /**
     * 猪只类型（字典 djs_pig_type）。
     */
    private String pigType;

}
