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
     * 字典 djs_product_type：1=自产 / 2=外购 / 3=礼盒。
     */
    private Integer productType;

    /**
     * 产品类型集合（多入口预过滤用）：产品配置入口同时显示自产+礼盒 {1,3}，商品配置入口 {2}。
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
     * 字典 djs_product_workshop：生产车间（原型筛选项）。
     */
    private Integer productWorkshop;

    /**
     * 存储库位 ID（原型「存储仓库」筛选项；精确匹配 store_location_id）。
     */
    private String storeLocationId;

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

}
