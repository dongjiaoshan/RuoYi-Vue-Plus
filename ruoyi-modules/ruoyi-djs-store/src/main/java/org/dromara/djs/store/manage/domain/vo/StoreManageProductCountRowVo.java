package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店管理月度看板 - mapper 原始行：某业态当月到店产品去重数。
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageProductCountRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品归属业态（字典 djs_belong_type）。 */
    private String belongType;

    /** 当月到店产品去重数（COUNT(DISTINCT product_id)）。 */
    private Integer productCount;

}
