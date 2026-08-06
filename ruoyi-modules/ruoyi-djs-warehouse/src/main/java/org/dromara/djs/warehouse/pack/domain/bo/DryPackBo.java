package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 干货打包 BO（WMS-PACK-001）。
 *
 * <p>事务同 {@link VegPackBo}（无地块来源），但 produceNo 前缀 = H（干货）。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class DryPackBo {

    /**
     * 来源过程产品 ID（FK → {@code t_warehouse_product_inhouse.id}）。
     */
    @NotNull(message = "{pack.source_inhouse_id.required}")
    private Long sourceInhouseId;

    /**
     * 目标打包产品 ID（FK → {@code t_warehouse_product_info.id}，必须 is_delivery=1）。
     */
    @NotNull(message = "{pack.product_id.required}")
    private Long productId;

    /**
     * 打包重量 / 数量（&gt; 0）。
     */
    @NotNull(message = "{pack.product_weight.required}")
    @DecimalMin(value = "0.001", message = "{pack.product_weight.positive}")
    private BigDecimal productWeight;

    /** 实称超过产品打包规则 3% 时，操作员是否已明确确认继续。 */
    private Boolean allowOverMeasure;

    /**
     * 本次打包的<b>成品数量</b>（V6 row27/row34，单位 = 成品自身单位，如 4 枚 / 2 份）。
     *
     * <p>与 {@link #getProductWeight()} 的区别：{@code productWeight} 落库的是<b>原材料消耗量</b>
     * （份数模式下 = 打包量 × 每份计量规则，如 2 份 × 30 枚 = 60 枚），而门店需求是按<b>成品数量</b>
     * 下的单，两者量纲不同，不能混用。</p>
     *
     * <p>仅「其他产品打包」页在录入框显示「打包量」时（原材料按件计量，如鸡蛋=枚）回传；
     * 重量模式（原材料按 kg 计量，录入的是重量而非件数）与其余打包页均<b>不传</b>，
     * 服务端回落原口径「一次打包 = 扣 1 份」。</p>
     *
     * <p>甲方 2026-08-06（V6 row34）：「如果生产产品单位是非KG时，显示的是打包量，打包量是输入了多少
     * 减去对应的量」——即录入 4 枚就扣 4 枚需求，而非恒扣 1。</p>
     */
    @DecimalMin(value = "0", message = "打包量不能为负数")
    private BigDecimal packQuantity;

    /**
     * 计量单位 {@code kg / 个}。
     */
    @NotBlank(message = "{pack.product_unit.required}")
    @Size(max = 16, message = "{pack.product_unit.size}")
    private String productUnit;

    /**
     * 入库目标库位 FK → {@code t_warehouse_location_info.id}（干货库）。
     *
     * <p>FIX-WMS-PACK-CASHIER：前端打包收银台不再采集入库库位，可空；service 端默认取该产品配置的
     * 入库库位（{@code product_info.store_location_id} 首个），无配置则取首个可用库位兜底。</p>
     */
    private Long locationId;

    /**
     * 需求门店 ID（条件必选，需求 C）。
     *
     * <p>打包须选中对应门店：打包即按该门店最早未完成需求扣减 shipped_count（替代原发货扣减，防双扣），
     * 追溯码也按此门店归属（{@code trace_code.store_id}）。</p>
     *
     * <p>必选性按发送位置条件化（service 层 {@code fulfillDirectDemandOnPack} 校验）：发货月台=必选；
     * 礼盒（{@code deliver_dest='gift'}，成品作礼盒组件、门店在礼盒打包环节绑定）=可空。</p>
     */
    private Long storeId;

    /**
     * 发送位置字典 {@code djs_pack_send_dest}：platform 发货月台 / mail 邮寄 / gift 礼盒（可选）。
     */
    @Pattern(regexp = "^(platform|mail|gift)$", message = "{pack.deliver_dest.invalid}")
    private String deliverDest;

    /**
     * 规格（如 250g/包）。
     */
    @Size(max = 64, message = "{pack.product_spec.size}")
    private String productSpec;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{pack.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{pack.remark.size}")
    private String remark;

}
