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
 * 产品 / 商品 / 礼盒共表实体（WMS-MD-002）。
 *
 * <p>对应表 {@code t_warehouse_product_info}（V202605311100 建）。
 * 三态 {@code productType=1 自产 / 2 外购 / 3 礼盒} 走单表共表 + 应用层校验：</p>
 * <ul>
 *   <li>{@code productType=1} 自产：{@code belongType} 必填、{@code productAttr} / {@code productWorkshop} 可填、
 *       {@code buyClass} / {@code supplierId} 应为 NULL</li>
 *   <li>{@code productType=2} 外购：{@code buyClass} / {@code supplierId} 应填、{@code belongType} 应为 NULL</li>
 *   <li>{@code productType=3} 礼盒：{@code belongType='gift_box'} 自动设置、组件清单走独立表
 *       {@link GiftBox}（外键 {@code box_product_id → product_info.id}）</li>
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
     * 字典 {@code djs_product_type}：1=自产 / 2=外购 / 3=礼盒。
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
     * 字典 {@code djs_belong_type}：自产归属类型
     * （pork / vegetable / white_bar / dry_good / egg / gift_box）。
     *
     * <p>{@code productType=1} 时必填；{@code productType=3} 时由 service 自动设为 {@code gift_box}。</p>
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
     * 字典 {@code djs_product_workshop}：1=燎毛间 / 2=分割间 / 3=肉品打包 / 4=蔬菜打包。
     */
    private Integer productWorkshop;

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
