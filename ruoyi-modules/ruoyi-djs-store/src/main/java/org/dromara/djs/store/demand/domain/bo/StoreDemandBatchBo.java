package org.dromara.djs.store.demand.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 门店发起需求批量录入 BO（STORE-DEMAND-REALIGN-001，对齐原型「新增需求」购物车整页）。
 *
 * <p>一次提交某门店多产品需求（购物车里逐项）。service 循环建多条
 * {@link org.dromara.djs.warehouse.demand.domain.DemandManage}，每条复用仓库域
 * {@code insertByBo}（自动 demand_no + 校验）再立即推 {@code SUBMITTED}（门店发起跳过 DRAFT）。</p>
 *
 * <p>每行的 {@code productType} 是仓库域 4 业态码（{@code white_bar/vegetable/gift_box/other}）；
 * 创建页 7 业态 tab（白条/猪肉/果蔬/干货/鸡蛋/礼盒/其他）由前端按产品 {@code belongType} 分组，
 * 提交时映射回这 4 业态（薄封装铁律：不扩仓库域状态机/编码）。</p>
 *
 * @author djs
 * @since STORE-DEMAND-REALIGN-001
 */
@Data
public class StoreDemandBatchBo {

    /** 提单门店 ID，FK {@code t_md_store.id}（门店视角必填）。 */
    @NotNull(message = "门店不能为空")
    private Long storeId;

    /** 需求日期（不传则 service 默认今日）。 */
    private LocalDate demandDate;

    /** 期望到货日（可空）。 */
    private LocalDate expectedArriveDate;

    /** 整单备注（写到每条需求的 demand_remark）。 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String demandRemark;

    /** 需求明细行（购物车里至少 1 项）。 */
    @Valid
    @NotEmpty(message = "需求产品不能为空")
    private List<Item> items;

    /**
     * 单行需求：产品 + 业态 + 需求量 + 是否个人邮寄。
     *
     * @author djs
     * @since STORE-DEMAND-REALIGN-001
     */
    @Data
    public static class Item {

        /** 产品 FK → {@code t_warehouse_product_info.id}（snowflake）。 */
        @NotNull(message = "产品不能为空")
        private Long productId;

        /** 仓库域业态码（{@code white_bar/vegetable/gift_box/other}）。 */
        @NotNull(message = "产品业态不能为空")
        @Size(max = 32, message = "产品业态长度不能超过 32")
        private String productType;

        /** 需求量（必须正数）。 */
        @NotNull(message = "需求量不能为空")
        @Positive(message = "需求量必须大于 0")
        private BigDecimal demandQuantity;

        /** 是否个人邮寄（true=个人邮寄 mailing / false=门店 store）。 */
        private Boolean mailing;
    }
}
