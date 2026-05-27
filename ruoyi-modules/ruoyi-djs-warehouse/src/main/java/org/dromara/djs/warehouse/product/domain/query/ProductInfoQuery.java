package org.dromara.djs.warehouse.product.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 产品列表查询入参（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductInfoQuery extends BaseEntity {

    /**
     * 产品编码（精确匹配）。
     */
    private String productId;

    /**
     * 产品名称（模糊匹配）。
     */
    private String productName;

    /**
     * 字典 djs_product_type：1=自产 / 2=外购 / 3=礼盒。
     */
    private Integer productType;

    /**
     * 字典 djs_belong_type：自产归属类型。
     */
    private String belongType;

    /**
     * 字典 djs_buy_class：外购产品类。
     */
    private String buyClass;

    /**
     * 字典 sys_normal_disable：0=正常 / 1=停用。
     */
    private Integer productStatus;

}
