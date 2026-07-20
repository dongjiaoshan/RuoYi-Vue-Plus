package org.dromara.djs.breed.production.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 精液 / 公猪配置列表查询入参（BRD-MD-003 Tab2）。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BoarConfigQuery extends BaseEntity {

    /**
     * 关联公猪 ID（V1 null 表示通用配置）。
     */
    private Long boarId;

}
