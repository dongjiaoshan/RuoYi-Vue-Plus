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
     * 单份规格 / 原材料计量量（service 层 JOIN product_info.material_num 填充）：如鸡蛋一份=42 枚则 material_num=42、
     * 果蔬 0.25kg 一包则 material_num=0.25。仅作规格展示与打包称重容差校验的基准。
     *
     * <p>不参与「生产量」计算——生产量按 {@link #productUnit} 定：kg 单位取 produceQuantity 公斤数，
     * 其余单位每条打包记录计 1 件（Kevin 2026-07-29）。</p>
     */
    private BigDecimal materialNum;

    /**
     * 原材料计量单位（service 层按 product_info.product_material 自引用 FK 查关联原材料的 product_unit 填充）：
     * kg / 份 / 枚 等。product_material 为 NULL（自身即原材料 / 无关联）时回落自身 product_unit。
     *
     * <p>mp 发货清单「产品总重」是否展示以此判定——如干羊肚菌礼盒（product_unit='盒'、原材料=干货 unit='kg'）
     * 材料单位为 kg，鸡蛋礼盒（原材料 unit='枚'）不显。区别于 {@link #productUnit}（产品自身单位，礼盒='盒'）。</p>
     */
    private String materialUnit;

    private Long produceLocation;

    /** 库位名称（service 层 JOIN location_info 填充）。 */
    private String produceLocationName;

    private String earNo;

    private Long whiteBarId;

    private Long demandId;
}
