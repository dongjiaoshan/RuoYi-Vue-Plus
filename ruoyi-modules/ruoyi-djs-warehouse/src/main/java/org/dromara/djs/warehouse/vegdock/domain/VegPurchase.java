package org.dromara.djs.warehouse.vegdock.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购果蔬到货实体（FIX-WMS-MP-VEGDOCK-001，原型图 42 外购产品收货 / 图 43 果蔬间入库）。
 *
 * <p>对应表 {@code t_warehouse_veg_purchase}（V202606200910 建）。</p>
 *
 * <p>口径（D-FIX-7 #4.2 默认）：外购果蔬 = 采购到货的一类。月台收货登记一笔「到货重量」→ 行进入
 * {@code pending}（待入库）；果蔬间入库按「待入库量 vs 实际入库量」确认 → 写库存（UPSERT location_stock +
 * INSERT stock_flow，flow_type={@code veg_purchase_in}）→ 行置 {@code done}。</p>
 *
 * <p>与 {@code t_warehouse_vegetable_handle}（自产毛菜处理）独立：自产收货只读复用 vegHandle/pending，
 * 本表只承载「外购」一类，避免和自产链路混算（DoD：入库后库存增、与处理链不重复计）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_veg_purchase")
public class VegPurchase extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 到货来源说明（外购果蔬来源，自由文本，如「本地菜市场」「XX 配送」）。
     */
    private String source;

    /**
     * 供应商业务编码（mp SupplierPicker 选中值，可选；业务短码 String）。
     */
    private String supplierCode;

    /**
     * 供应商名称（冗余，回显用）。
     */
    private String supplierName;

    /**
     * 作物 ID（FK → {@code t_plant_crop_info.id}，外购果蔬品种）。
     */
    private Long cropId;

    /**
     * 作物名称（冗余，列表 / 入库页 header 显示）。
     */
    private String cropName;

    /**
     * 关联产品 ID（FK → {@code t_warehouse_product_info.id}，belong_type=vegetable；入库写库存用）。
     */
    private Long productId;

    /**
     * 入库库位 ID（FK → {@code t_warehouse_location_info.id}，入库确认时指定）。
     */
    private Long locationId;

    /**
     * 到货重量(kg)（收货登记的总到货量）。
     */
    private BigDecimal arrivalWeight;

    /**
     * 待入库量(kg)（= 到货重量 - 实际入库量；收货时 = 到货重量，入库后递减）。
     */
    private BigDecimal pendingWeight;

    /**
     * 实际入库量(kg)（入库确认时累加；图 43「实际入库量」）。
     */
    private BigDecimal actualWeight;

    /**
     * 到货时间。
     */
    private Date arrivalTime;

    /**
     * 状态 {@code djs_veg_purchase_status}：pending=待入库 / processing=入库中 / done=已入库。
     */
    private String status;

    /**
     * 凭证图 OSS IDs CSV（{@code CameraUploadWithWatermark biz_type=warehouse_veg_purchase}）。
     */
    private String proofOssIds;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
