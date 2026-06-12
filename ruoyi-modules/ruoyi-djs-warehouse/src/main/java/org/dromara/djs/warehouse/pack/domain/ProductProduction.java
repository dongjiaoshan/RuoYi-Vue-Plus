package org.dromara.djs.warehouse.pack.domain;

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
 * 发货产品生产信息实体（doc/11 §2.6）。
 *
 * <p>对应表 {@code t_warehouse_product_production}（V202606101100 WMS-PACK-001 新建）。</p>
 *
 * <p><b>语义边界</b>：本表是"发货链路"产品记录。与 {@code t_warehouse_product_inhouse}
 * （过程产品/非发货）区别——后者是分割/蔬菜处理产出的中间产品（进冻品库/鲜品库），
 * 本表是 mp 端打包后产出的"待发货商品"，{@code is_delivery_check} 推进由 WMS-SHIP-001 完成。</p>
 *
 * <p><b>4 业态打包入口</b>（{@code product_type / belong_type} 区分）：</p>
 * <ul>
 *   <li>蔬菜打包：plot_id 来源 + product_weight + material_consume</li>
 *   <li>礼盒打包：product_id 礼盒 SKU，组件清单查 {@code t_warehouse_gift_box}（D8 落表）</li>
 *   <li>干货打包：product_unit（个/kg）+ product_weight + product_spec</li>
 *   <li>芹菜打包：按重量 product_weight + product_spec='按重量'</li>
 * </ul>
 *
 * <p><b>追溯码 hook</b>：{@code trace_code} 字段 D11 TRC-CORE-001 回填，本 ticket 默认 NULL。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_product_production")
public class ProductProduction extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 生产日期。
     */
    private Date produceDate;

    /**
     * 业务码：{@code yyMMdd + 前缀 + 4 位序号}。
     *
     * <p>前缀按 product 归属：Z=猪肉 / G=果蔬 / B=白条 / H=干货 / D=鸡蛋 / L=礼盒。</p>
     */
    private String produceNo;

    /**
     * 产品 FK → {@code t_warehouse_product_info.id}。
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
     * 计量单位（kg / 个 / 盒）。
     */
    private String productUnit;

    /**
     * 规格（500g/包 / 按重量 / 等）。
     */
    private String productSpec;

    /**
     * 蔬菜来源地块 ID。
     */
    private Long plotId;

    /**
     * 猪肉来源耳号（冗余便于追溯）。
     */
    private String earNo;

    /**
     * 产品序号 1 开始自增。
     */
    private Integer productSort;

    /**
     * 产品重量 kg / 数量。
     */
    private BigDecimal productWeight;

    /**
     * 需求门店 FK → {@code t_md_store.id}（V1 可空）。
     */
    private Long storeId;

    /**
     * 生产时间到时分秒。
     */
    private Date produceTime;

    /**
     * 是否发货清点字典 {@code djs_yes_no}：1=是 / 0=否（WMS-SHIP-001 推进）。
     */
    private Integer isDeliveryCheck;

    /**
     * 发货清点时间（WMS-SHIP-001 推进时写）。
     */
    private Date deliveryCheckTime;

    /**
     * 是否到货确认字典 {@code djs_yes_no}：1=是 / 0=否（门店端确认）。
     */
    private Integer isArrivalConfirm;

    /**
     * 到货确认时间。
     */
    private Date arrivalConfirmTime;

    /**
     * 内部分割时关联白条 FK → {@code t_warehouse_bar_info.id}。
     */
    private Long whiteBarId;

    /**
     * 原材料 ID。
     */
    private Long materialId;

    /**
     * 原材料耗用 kg。
     */
    private BigDecimal materialConsume;

    /**
     * 外购供应商 ID。
     */
    private Long supplierId;

    /**
     * 生产位置 FK → {@code t_warehouse_location_info.id}。
     *
     * <p>D10 P0 hotfix：原 PACK DDL 定义为 {@code TINYINT 字典 1=仓库/2=门店}（doc/11 §2.6 R27），
     * 但 SHIP 端 service {@link org.dromara.djs.warehouse.shipment.service.impl.ShipmentServiceImpl} 用
     * 作 location_info FK（查 location_name）。本 hotfix 同步改 DDL 为 BIGINT FK 以保证两端 service 都
     * 能工作；字典语义降级为 follow-up（详 D10 _open-issues）。</p>
     */
    private Long produceLocation;

    /**
     * 发货关联需求 FK → {@code t_warehouse_demand_manage.id}（WMS-SHIP-001 用）。
     *
     * <p>D10 P0 hotfix：PACK DDL DROP+CREATE 时遗漏，但 SHIP service 通过它筛选可清点行；
     * hotfix DDL 同步 ADD COLUMN 回来。PACK service 不写本字段（值 NULL）。</p>
     */
    private Long demandId;

    /**
     * 生产数量（重量 kg / 件数 / 盒数；WMS-SHIP-001 出库流水 changeNum / changeQuantity 用）。
     *
     * <p>D10 P0 hotfix：PACK DDL DROP+CREATE 时仅保留 {@code productWeight}，SHIP 老逻辑用
     * {@code produceQuantity}；hotfix DDL 同步 ADD COLUMN 回来。PACK service 写 produceQuantity
     * = productWeight 同步双字段（向后兼容；follow-up 统一）。</p>
     */
    private BigDecimal produceQuantity;

    /**
     * 发货方式字典 {@code djs_deliver_type}：1=发货 / 2=邮寄 / 3=销售。
     */
    private Integer deliverType;

    /**
     * 发送位置字典 {@code djs_pack_send_dest}：platform 发货月台 / mail 邮寄 / gift 礼盒。
     *
     * <p>生产管理打包录入「发送位置」（肉品 / 果蔬三选；其他产品二选无 gift）。
     * 区别于 {@link #deliverType}「发货方式」，互不覆盖。</p>
     */
    private String deliverDest;

    /**
     * 打包状态字典 {@code djs_pack_status}：pending / packed / shipped_out。
     */
    private String packStatus;

    /**
     * 凭证图 OSS IDs CSV（biz_type=warehouse_pack_proof）。
     */
    private String proofOssIds;

    /**
     * 追溯码（{@code TraceService.genCode} 打包入库时回填，TRC-CORE-001）。
     */
    private String traceCode;

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
