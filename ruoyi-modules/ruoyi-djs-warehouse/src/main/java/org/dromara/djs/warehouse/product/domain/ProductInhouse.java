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
     * 产品类型字典 {@code djs_product_type}：1=自产 / 2=外购（已废弃 3 礼盒；礼盒 = 自产 + belongType=gift_box）。
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
     * 白条流水号（半只/整只白条唯一标识，燎毛按白条生成 {@code BizCodeType.BAR_NO}）。
     *
     * <p>区分同一 ear_no 的两个半只（燎毛每白条一个号），贯穿库存 / 领用 / 分割 / 发货，
     * 使半只白条在数据维度成一条可单独处理 / 追溯。外购 / 旧数据可空。结算聚合仍按 white_bar_id(整猪)。</p>
     */
    private String whiteBarNo;

    /**
     * 所属燎毛记录 FK → {@code t_warehouse_pig_burn_record.id}（V6-R43）。
     *
     * <p>仅燎毛间入库产出行有值（{@code submitBurnRecord} 写入）。一条燎毛记录 = 一次提交，
     * 其 {@code burn_weight} = 本次提交各产出行 {@code product_weight} 之和；调整某行入库重量时
     * 据此把差额同步回燎毛记录。列表「入库人」也取 {@code burn_record.operator_id}
     *（由 EmployeePicker 指定，可与登录人 {@code create_by} 不同）。</p>
     */
    private Long burnRecordId;

    /**
     * 入库重量是否被调整过（字典 {@code djs_yes_no}：1=是 / 0=否，V6-R43）。
     */
    private Integer isAdjusted;

    /**
     * 最近一次入库重量调整时间（V6-R43）。
     */
    private Date adjustTime;

    /**
     * 最近一次入库重量调整人 FK → {@code sys_user.user_id}（V6-R43）。
     */
    private Long adjustBy;

    /**
     * 燎毛产出行白条领用状态（FIX-WMS-CUTPICKUP-SPLIT-001）：0=未领 / 1=已领。
     *
     * <p>仅 {@code white_bar_id} 非空的燎毛产出行（半只/半扇/整只）用：白条领用页按行出条，
     * 领用一行置 1；一头白条所有行领满 → 推 bar pending_cut + 建整猪 cut_record。
     * 门店拆单 / 蔬菜处理等其余写入路径不 set，靠 DB DEFAULT 0 兜底、恒无意义。</p>
     */
    private Integer pickupStatus;

    /**
     * 该产出行白条领用过磅重量 kg（FIX-WMS-CUTPICKUP-SPLIT-001）。
     *
     * <p>领用进分割车间时现场过磅；整猪 {@code cut_record.pickup_weight} = 该白条各产出行 pickup_weight 之和。</p>
     */
    private BigDecimal pickupWeight;

    /**
     * 分割部位字典 {@code djs_pig_cut_part}：lean/part/bone/skin/scrap。
     */
    private String cutPart;

    /**
     * 来源 {@code source}：warehouse=仓库分割（WMS-PIG-002 / WMS-VEG-001 产出）/ store=门店再分（STR-SPLIT-001）。
     *
     * <p>DB 列 DEFAULT 'warehouse'；仓库分割写入路径不显式 set，靠 DB DEFAULT 兜底；
     * 门店再分写入路径（{@code StoreSplitServiceImpl#addSplit}）显式 {@code setSource("store")}。</p>
     */
    private String source;

    /**
     * 门店 ID（STORE-PERM-001）：仅 {@code source='store'} 的门店拆单行有值，用于门店行级隔离
     * （{@code store.includes} 白名单含本表）；仓库分割行 / 历史行为 NULL。
     */
    private Long storeId;

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
