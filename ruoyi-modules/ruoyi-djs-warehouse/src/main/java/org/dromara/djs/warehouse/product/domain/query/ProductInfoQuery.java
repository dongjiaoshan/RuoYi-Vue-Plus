package org.dromara.djs.warehouse.product.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;
import java.util.List;

/**
 * 产品列表查询入参（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductInfoQuery extends BaseEntity {

    /**
     * 产品编码（精确匹配）。
     */
    private String productId;

    /**
     * 产品名称（模糊匹配）。
     */
    private String productName;

    /**
     * 字典 djs_product_type：1=自产 / 2=外购（已废弃 3 礼盒；礼盒 = 自产 + belongType=gift_box）。
     */
    private Integer productType;

    /**
     * 产品类型集合（多入口预过滤用）：产品配置入口 {1}=自产（含礼盒），商品配置入口 {2}=外购。
     *
     * <p>非空时优先于 {@link #productType} 单值，落 {@code product_type IN (...)}。</p>
     */
    private List<Integer> productTypes;

    /**
     * 字典 djs_belong_type：自产归属类型。
     */
    private String belongType;

    /**
     * 归属类型集合（多入口预过滤用）：其他产品打包入口显示 {egg, dry_good, other}。
     *
     * <p>非空时叠加在 {@link #belongType} 单值之上，落 {@code belong_type IN (...)}。</p>
     */
    private List<String> belongTypes;

    /**
     * 字典 djs_buy_class：外购产品类。
     */
    private String buyClass;

    /**
     * 是否支持外购（字典 djs_yes_no：1=是 / 0=否；原型「是否支持外购」筛选项，精确匹配 is_buy_out）。
     */
    private Integer isBuyOut;

    /**
     * 字典 djs_product_workshop：生产车间单值筛选（原型筛选项）。
     *
     * <p>产品侧是 CSV 多归属，故此处落 {@code FIND_IN_SET(值, product_workshop) > 0}
     * ——「挂了该车间的产品」而非「只挂该车间的产品」。</p>
     */
    private String productWorkshop;

    /**
     * 生产车间集合（R70 多选）：非空时落 {@code OR} 连接的多个 {@code FIND_IN_SET}（命中任一即入选），
     * 叠加在 {@link #productWorkshop} 单值之上。
     */
    private List<String> productWorkshops;

    /**
     * 产品属性 djs_product_attr：1=生产产品（打包目标成品） / 2=原材料。
     *
     * <p>肉品/果蔬打包目标成品按 {@code product_attr=1} 过滤（取数逻辑 doc#13）。</p>
     */
    private Integer productAttr;

    /**
     * 关联原材料产品（自引用 FK → product_info.id 雪花 id）。
     *
     * <p>成品通过 {@code product_material} 指向它消耗的原材料；按此精确匹配可反查某原材料对应的成品。</p>
     */
    private Long productMaterial;

    /**
     * 关联原材料产品集合（findings 步12：果蔬打包页按「本次领用原料产品 ID」反查命中成品）。
     *
     * <p>非空时落 {@code product_material IN (...)}，叠加在 {@link #productMaterial} 单值之上。
     * 空 → 不过滤（保持向后兼容，不破坏 dry/gift/meat 入口的全量加载）。</p>
     */
    private List<Long> productMaterials;

    /**
     * 存储库位 ID（原型「存储仓库」筛选项；精确匹配 store_location_id）。
     */
    private String storeLocationId;

    /**
     * 存储库位集合（R70 多选）：非空时落 {@code store_location_id IN (...)}，叠加在 {@link #storeLocationId} 单值之上。
     */
    private List<String> storeLocationIds;

    /**
     * 字典 sys_normal_disable：0=正常 / 1=停用。
     */
    private Integer productStatus;

    /**
     * 更新人 ID（原型「更新人员」筛选项；精确匹配 update_by）。
     */
    private Long updateBy;

    /**
     * 更新时间区间起（含；原型「更新时间」筛选项）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date updateBeginTime;

    /**
     * 更新时间区间止（含；原型「更新时间」筛选项）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date updateEndTime;

    /**
     * 是否解析展示名（DENGBO-R16）：{@code true} 时列表结果按「果蔬产品原材料作物是否有有效有机证书」
     * 批量回填 {@link org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo#getDisplayName()}
     * （有证=产品名 / 无证=别名）。默认 {@code null/false}：不解析、不加查询，产品配置等原有列表零影响。
     *
     * <p>门店需求下单选择器传 {@code true}，使候选产品显示与下单后定格一致的展示名。</p>
     */
    private Boolean withDisplayName;

}
