package org.dromara.djs.warehouse.product.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 产品 / 商品共表实体（WMS-MD-002）。
 *
 * <p>对应表 {@code t_warehouse_product_info}（V202605311100 建）。
 * 两态 {@code productType=1 自产 / 2 外购} 走单表共表 + 应用层校验（djs_product_type 已废弃 3 礼盒）：</p>
 * <ul>
 *   <li>{@code productType=1} 自产：{@code belongType} 必填、{@code productAttr} / {@code productWorkshop} 可填、
 *       {@code buyClass} / {@code supplierId} 应为 NULL；礼盒 = 自产 + {@code belongType='gift_box'}（独立成品、无组件清单/BOM）</li>
 *   <li>{@code productType=2} 外购：{@code buyClass} / {@code supplierId} 应填、{@code belongType} 应为 NULL</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@code DjsMetaObjectHandler} 在
 * {@code delFlag='1'} 时把 id 写入 {@code delUnique}，保证 UNIQUE(tenant_id, product_id, del_unique)
 * 不阻塞同业务码复用）。服务层走
 * {@link org.dromara.djs.common.base.DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_product_info")
public class ProductInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 业务码（用户手填；UNIQUE(tenant_id, product_id, del_unique)）。
     *
     * <p>注意：这里 {@code product_id} 是<b>业务码 VARCHAR(32)</b>（doc/11 §2.5 R6），
     * 不是 snowflake 主键。前端"产品编码"输入框绑这个字段。</p>
     */
    private String productId;

    /**
     * 产品 / 商品名称。
     */
    private String productName;

    /**
     * 字典 {@code djs_product_type}：1=自产 / 2=外购（已废弃 3 礼盒；礼盒 = 自产 + belongType=gift_box）。
     */
    private Integer productType;

    /**
     * 单位（kg / 个 / 盒 等）。
     */
    private String productUnit;

    /**
     * 规格（如 500g/包）。
     */
    private String productSpec;

    /**
     * 产品别名（非必填）。
     */
    private String productAlias;

    /**
     * 字典 {@code djs_belong_type}：自产归属类型
     * （pork / vegetable / white_bar / dry_good / egg / gift_box）。
     *
     * <p>{@code productType=1} 时必填；礼盒 = 自产 + {@code belongType='gift_box'}（用户选产品类别确定）。</p>
     */
    private String belongType;

    /**
     * 字典 {@code djs_buy_class}：外购产品类（V1 客户后填）。
     */
    private String buyClass;

    /**
     * 缩略图 OSS ID（单张 ossId）。
     */
    private String productThumb;

    /**
     * 原图 OSS IDs（逗号分隔，多张）。
     */
    private String productImg;

    /**
     * 主图 ossId（IMG-LIB-001 4 层 resolver L1；create 时按 productName 自动匹配存入）。
     */
    private String imageOssId;

    /**
     * 图来源（IMG-LIB-001：0 自动匹配 / 1 用户手改）。
     */
    private Integer imageSource;

    /**
     * 字典 {@code djs_product_attr}：1=生产产品 / 2=原材料。
     */
    private Integer productAttr;

    /**
     * 字典 {@code djs_product_workshop} 生产车间，<b>CSV 多值</b>（逗号分隔无空格，如 {@code "3,5"}）：
     * 1=燎毛间 / 2=分割间 / 3=肉品打包间 / 4=蔬菜打包间 / 5=门店打包间 / 6=其他产品打包间 / 7=礼盒打包间。
     *
     * <p>同一产品可归属多个车间（如猪肉成品「肉品打包间 + 门店打包间」两处都能生产）。
     * 过滤一律用 {@link org.dromara.djs.warehouse.product.util.WorkshopMatcher#findInSet}，
     * <b>禁止 {@code eq} / {@code in}</b>——CSV 下等值比较会漏掉多归属行。</p>
     */
    private String productWorkshop;

    /**
     * 存储库位 ID 列表（逗号分隔；V2 改关联表）。
     */
    private String storeLocationId;

    /**
     * 字典 {@code sys_normal_disable}：0=正常 / 1=停用。
     */
    private Integer productStatus;

    /**
     * FK → t_warehouse_product_info.id（生产产品关联原材料；同表自引用）。
     */
    private Long productMaterial;

    /**
     * 产品描述。
     */
    private String productDesc;

    /**
     * 原材料计算量（主要鸡蛋）。
     */
    private BigDecimal materialNum;

    /**
     * 销售价格（row191）：产品属性 = 原材料时可填，表示该原材料**对外出库**时的单价。
     *
     * <p>毛菜间出库新增页的「销售单价」默认取它，但用户可改；真正落库的是流水行上的
     * {@code out_unit_price} 快照，故这里改价不影响历史出库单金额。</p>
     */
    private BigDecimal salePrice;

    /**
     * 字典 {@code djs_yes_no}：是否发货产品 1=是 / 0=否。
     */
    private Integer isDelivery;

    /**
     * FK → t_md_supplier.id（外购产品填）。
     */
    private Long supplierId;

    /**
     * 字典 {@code djs_yes_no}：是否可外购 1=是 / 0=否。
     */
    private Integer isBuyOut;

    /**
     * 字典 {@code djs_yes_no}：是否原材料外售 1=是 / 0=否。
     * 为「是」时该生产产品到店后，门店盘点/退回按其关联原材料（{@link #productMaterial}）口径处理。
     */
    private Integer isMaterialSold;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列：未删时 0；软删时由 {@code DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, product_id, del_unique) 不阻塞重新启用同业务码。
     */
    private Long delUnique;

}
