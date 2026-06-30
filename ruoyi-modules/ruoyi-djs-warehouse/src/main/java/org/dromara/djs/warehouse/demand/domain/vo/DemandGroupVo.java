package org.dromara.djs.warehouse.demand.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.demand.domain.DemandManage;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求汇总分组 VO（0613-10 需求管理列表重做）。
 *
 * <p>按「需求日期 + 需求产品(product_id)」分组聚合：同一日期同一产品 N 个门店需求合并成一行。
 * 列对齐原型 bc5e5339：需求日期/需求产品/产品规格/需求量/需求产品类型/原材料/原材料计算量/
 * 需求门店数量/需求状态(三态)/需求确认率/需求最终确认时间/操作(查看需求)。</p>
 *
 * <p>{@code productId} 序列化为 string 避免雪花 ID JS 精度截断
 * （参 .claude/skills/coder-djs-cross-layer-contract.md §契约 1）；前端「查看需求」下钻
 * 携 {@code demandDate + productId} 跳确认页。</p>
 *
 * @author djs
 * @since 0613-10
 */
@Data
@AutoMapper(target = DemandManage.class)
public class DemandGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate demandDate;

    /** 产品 ID（string 输出避雪花精度）。 */
    private Long productId;

    /** 需求产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 需求产品类型（内部业态 white_bar/vegetable/gift_box/other，驱动状态机分支；列表展示已改用 belongType）。 */
    private String productType;

    /** 产品类别（字典 djs_belong_type，取自产品主数据 belong_type；列表「需求产品类型」列 + 筛选统一按产品配置产品类别展示）。 */
    private String belongType;

    /** 原材料描述。 */
    private String rawMaterial;

    /** 需求量合计（组内 demand_quantity SUM）。 */
    private BigDecimal demandQuantity;

    /** 原材料计算量合计（组内 material_qty SUM）。 */
    private BigDecimal materialQty;

    /** 单位（组内取任一，冗余字段同组通常一致）。 */
    private String productUnit;

    /** 需求门店数量（组内去重 store_id 计数，非取消/删除单）。 */
    private Integer storeCount;

    /** 已确认门店数（demand_status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）。 */
    private Integer confirmedStoreCount;

    /**
     * 需求状态三态（前端 dict 文案；0613-10 点4）：
     * {@code PENDING 待确认（无一已确认）/ ALL_CONFIRMED 已全部确认 / PARTIAL 部分确认}。
     */
    private String demandStatus;

    /** 需求确认率（已确认门店 / 总门店，0~1 小数；前端 toFixed 转 %）。 */
    private BigDecimal confirmRate;

    /** 需求最终确认时间（组内 MAX confirmer_time）。 */
    private LocalDateTime lastConfirmTime;
}
