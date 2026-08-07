package org.dromara.djs.plant.crop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 作物关联产品配置实体（V6 row16）。
 *
 * <p>对应表 {@code t_plant_crop_product}（V202608290100）。一个作物可产出多个产品，
 * 每个产品带自己的绩效金额；原先挂在 {@code t_plant_crop_info} 上的
 * {@code related_product} / {@code pick_unit_price} 一对一数据已迁入本表作首行。</p>
 *
 * @author djs
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_crop_product")
public class CropProduct extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 作物 FK → t_plant_crop_info.id。 */
    private Long cropId;

    /** 关联产品 FK → t_warehouse_product_info.id。 */
    private Long productId;

    /** 作物绩效（元/公斤）—— 该产品的采摘绩效单价。 */
    private BigDecimal perfPrice;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
