package org.dromara.djs.plant.crop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 作物关联产品配置行 VO（V6 row16 「产品配置」页签列表 / 各处产品下拉数据源）。
 *
 * @author djs
 */
@Data
public class CropProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long cropId;

    private Long productId;

    /** 产品名称（service 批量反查 t_warehouse_product_info.product_name）。 */
    private String productName;

    /** 产品单位（展示用）。 */
    private String productUnit;

    /** 作物绩效（元/公斤）。 */
    private BigDecimal perfPrice;
}
