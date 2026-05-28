package org.dromara.djs.warehouse.product.domain;

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
 * 过程产品（非发货产品生产信息）实体（doc/11 §2.7）。
 *
 * <p>对应表 {@code t_warehouse_product_inhouse}（V202606071100 WMS-PIG-002 新建）。</p>
 *
 * <p>用途：</p>
 * <ul>
 *   <li>WMS-PIG-002 分割工序产出：一头白条 1:N 部位（lean/part/bone/skin/scrap）入冻品库</li>
 *   <li>WMS-VEG-001 蔬菜处理产出（D9 同日落地）</li>
 * </ul>
 *
 * <p>与 {@code t_warehouse_product_production}（发货产品）区别：无 produce_no / store_id /
 * is_delivery_check / arrival_confirm_time 等发货链路字段；多 location_id（入冻品库位）。</p>
 *
 * <p>D9 closing Group B 已从 {@code cut/domain} 挪到 {@code product/domain}，
 * 与 {@code ProductInfo} 同一包域（产品主数据 + 产品过程实体）。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_product_inhouse")
public class ProductInhouse extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 生产日期。
     */
    private Date produceDate;

    /**
     * 产品 FK → {@code t_warehouse_product_info.id}。
     *
     * <p>分割品标准 SKU 由本 ticket V202606071100 seed（{@code PROD-PIG-LEAN-01} 等 5 行，business_code 维度）。</p>
     */
    private Long productId;

    /**
     * 产品名称（冗余）。
     */
    private String productName;

    /**
     * 产品类型字典 {@code djs_product_type}：1=自产 / 2=外购 / 3=礼盒。
     */
    private Integer productType;

    /**
     * 计量单位。
     */
    private String productUnit;

    /**
     * 规格。
     */
    private String productSpec;

    /**
     * 蔬菜来源地块（WMS-VEG-001 用）。
     */
    private Long plotId;

    /**
     * 猪肉来源耳号（冗余）。
     */
    private String earNo;

    /**
     * 产品序号。
     */
    private Integer productSort;

    /**
     * 产品重量 kg。
     */
    private BigDecimal productWeight;

    /**
     * 生产时间。
     */
    private Date produceTime;

    /**
     * 分割时关联白条 FK → {@code t_warehouse_bar_info.id}。
     */
    private Long whiteBarId;

    /**
     * 分割部位字典 {@code djs_pig_cut_part}：lean/part/bone/skin/scrap。
     */
    private String cutPart;

    /**
     * 原材料 ID。
     */
    private Long materialId;

    /**
     * 原材料耗用 kg。
     */
    private BigDecimal materialConsume;

    /**
     * 入库库位 FK → {@code t_warehouse_location_info.id}（冻品库 / 蔬菜鲜品库）。
     */
    private Long locationId;

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
