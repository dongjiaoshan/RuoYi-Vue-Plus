package org.dromara.djs.warehouse.product.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.product.domain.ProductInfo;

import java.math.BigDecimal;

/**
 * 产品 / 商品入参 BO（WMS-MD-002）。
 *
 * <p>共表 2 形态：{@code productType=1 自产 / 2 外购}（djs_product_type 已废弃 3 礼盒）。差异字段由 service 端按
 * {@code productType} 做应用层校验（DDL 不写 CHECK 约束）：</p>
 * <ul>
 *   <li>{@code productType=1}：{@code belongType} 必填；礼盒 = 自产 + {@code belongType=gift_box}（独立成品、跳过规格必填）</li>
 *   <li>{@code productType=2}：{@code supplierId} 必填（{@code buyClass} 客户字典未给可空）</li>
 * </ul>
 *
 * <p>{@code productId} 由前端用户手填（doc/11 §2.5 R6 业务码）；编辑时后端强制锁回旧值。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProductInfo.class, reverseConvertGenerate = false)
public class ProductInfoBo extends BaseEntity {

    /**
     * 产品 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 业务码（新增时必填，编辑时后端强制锁回旧值）。
     */
    @NotBlank(message = "{product.code.required}", groups = OnCreate.class)
    @Size(max = 32, message = "{product.code.size}")
    private String productId;

    /**
     * 产品名称。
     */
    @NotBlank(message = "{product.name.required}")
    @Size(max = 128, message = "{product.name.size}")
    private String productName;

    /**
     * 字典 {@code djs_product_type}：1=自产 / 2=外购（已废弃 3 礼盒；礼盒 = 自产 + belongType=gift_box）。
     */
    @NotNull(message = "{product.type.required}")
    private Integer productType;

    /**
     * 单位（kg / 个 / 盒 等）。
     */
    @NotBlank(message = "{product.unit.required}")
    @Size(max = 16, message = "{product.unit.size}")
    private String productUnit;

    /**
     * 规格（500g/包 等）。
     */
    @Size(max = 64, message = "{product.spec.size}")
    private String productSpec;

    /**
     * 产品别名（非必填）。
     */
    @Size(max = 128, message = "{product.alias.size}")
    private String productAlias;

    /**
     * 字典 {@code djs_belong_type}：自产归属类型。
     * <p>productType=1 时 service 强校验必填；礼盒 = 自产 + belongType='gift_box'（用户选产品类别确定）。</p>
     */
    @Size(max = 32, message = "{product.belong_type.size}")
    private String belongType;

    /**
     * 字典 {@code djs_buy_class}：外购产品类（V1 客户字典空则可不填）。
     */
    @Size(max = 32, message = "{product.buy_class.size}")
    private String buyClass;

    /**
     * 缩略图 OSS ID（单张）。
     */
    @Size(max = 512, message = "{product.thumb.size}")
    private String productThumb;

    /**
     * 原图 OSS IDs（逗号分隔，多张）。
     */
    @Size(max = 2048, message = "{product.img.size}")
    private String productImg;

    /**
     * 主图 ossId（IMG-LIB-001；用户手选则随提交带 imageSource=1）。
     */
    @Size(max = 32, message = "{product.thumb.size}")
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
     * 字典 {@code djs_product_workshop} 生产车间，CSV 多值（如 {@code "3,5"}）；admin 表单多选后 join 提交。
     */
    @Size(max = 32, message = "{product.workshop.size}")
    private String productWorkshop;

    /**
     * 存储库位 ID 列表（逗号分隔）。
     */
    @Size(max = 255, message = "{product.store_location.size}")
    private String storeLocationId;

    /**
     * 字典 {@code sys_normal_disable}：0=正常 / 1=停用（不传默认 0）。
     */
    private Integer productStatus;

    /**
     * FK 产品原材料 ID（生产产品关联原材料；同表自引用）。
     */
    private Long productMaterial;

    /**
     * 产品描述。
     */
    @Size(max = 500, message = "{product.desc.size}")
    private String productDesc;

    /**
     * 原材料计算量。
     */
    @PositiveOrZero(message = "{product.material_num.positive}")
    private BigDecimal materialNum;

    /**
     * 销售价格（row191）：产品属性 = 原材料（{@code product_attr=2}）时可填，
     * 表示该原材料对外出库时的单价；毛菜间出库新增页的「销售单价」默认取它。
     *
     * <p>不在这里做「非原材料必须为空」的强校验 —— 前端切换产品属性时会清值，
     * 后端多一道会把历史数据的编辑一起拦死。落库列是 DECIMAL(10,2)，
     * 故限 8 位整数 2 位小数，超了在 JDBC 层溢出会裸奔成 500。</p>
     */
    @PositiveOrZero(message = "销售价格不能为负数")
    @Digits(integer = 8, fraction = 2, message = "销售价格最多 8 位整数、2 位小数")
    private BigDecimal salePrice;

    /**
     * 字典 {@code djs_yes_no}：是否发货产品 1/0（不传默认 1）。
     */
    private Integer isDelivery;

    /**
     * FK → t_md_supplier.id（外购产品必填，service 端校验）。
     */
    private Long supplierId;

    /**
     * 字典 {@code djs_yes_no}：是否可外购 1/0（不传默认 0）。
     */
    private Integer isBuyOut;

    /**
     * 字典 {@code djs_yes_no}：是否原材料外售 1/0（不传默认 0）。
     */
    private Integer isMaterialSold;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{product.remark.size}")
    private String remark;

    /**
     * 新增校验组（仅 productId 等创建时必填字段使用；编辑时忽略）。
     */
    public interface OnCreate {
    }

}
