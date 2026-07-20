package org.dromara.djs.warehouse.shipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 待清点产品 VO（{@code GET /applet/warehouse/ship/productions}）。
 *
 * <p>mp 工人按 demand_id 拉本需求所有 {@code is_delivery_check=0} 的产品。展示
 * produceNo / productName / produceQuantity / produceLocation 等关键字段。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
public class AvailableProductionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String produceNo;

    private Date produceDate;

    private Long productId;

    /** 产品名称（service 层 JOIN product_info 填充）。 */
    private String productName;

    /**
     * 产品业态（service 层 JOIN product_info.belong_type 填充）。
     * mp 发货清单据此定数量单位：white_bar=头（整只/半扇按头计），其余（pork 分割品/vegetable/gift_box/other）=份。
     */
    private String belongType;

    private BigDecimal produceQuantity;

    /** 生产产品规格（打包时快照 product_production.product_spec）：mp 发货清单「规格」行展示此值，非库位名。 */
    private String productSpec;

    /**
     * 产品计量单位（service 层 JOIN product_info.product_unit 填充）：kg / 份 / 个 / 盒 等。
     * mp 发货清单据此渲染数量单位与重量行——非 kg 单位产品（如鸡蛋 unit='份'）不显「210kg」。
     */
    private String productUnit;

    /**
     * 原材料计量量（service 层 JOIN product_info.material_num 填充）：如鸡蛋一份=42 枚则 material_num=42。
     * material_num>0 的产品 produceQuantity 存的是「份数×material_num」的重量，mp 还原份数 = produceQuantity ÷ material_num。
     */
    private BigDecimal materialNum;

    private Long produceLocation;

    /** 库位名称（service 层 JOIN location_info 填充）。 */
    private String produceLocationName;

    private String earNo;

    private Long whiteBarId;

    private Long demandId;
}
