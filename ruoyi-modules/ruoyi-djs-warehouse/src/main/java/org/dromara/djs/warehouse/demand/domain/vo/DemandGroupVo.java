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

    /**
     * 原材料名称（需求产品 {@code product_info.product_material} 自引用到原材料产品的 {@code product_name}；
     * 未配原材料时为 null）。row127。
     */
    private String rawMaterialName;

    /**
     * 原材料计算量 = 需求量 × 单份用量（{@code product_info.material_num}）。row127。
     *
     * <p>产品未配 {@code material_num}（果蔬 / 猪肉当前多为 NULL）时为 null，前端显空不误显 0。</p>
     */
    private BigDecimal materialCalcQty;

    /**
     * 原材料单位（原材料产品 {@code product_info.product_unit}；未配原材料时为 null）。row127。
     */
    private String materialUnit;

    /** 需求量合计（组内 demand_quantity SUM）。 */
    private BigDecimal demandQuantity;

    /** 产品单位（组内取任一，冗余字段同组通常一致；非原材料单位，原材料单位见 {@link #materialUnit}）。 */
    private String productUnit;

    /**
     * 原材料名称别名（前端旧列 {@code rawMaterial} 绑定，透出 {@link #rawMaterialName}）。row127。
     */
    public String getRawMaterial() {
        return rawMaterialName;
    }

    /**
     * 原材料计算量别名（前端旧列 {@code materialQty} 绑定，透出 {@link #materialCalcQty}）。row127。
     */
    public BigDecimal getMaterialQty() {
        return materialCalcQty;
    }

    /** 需求门店数量（组内去重 store_id 计数，非取消/删除单）。 */
    private Integer storeCount;

    /** 已确认门店数（demand_status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）。 */
    private Integer confirmedStoreCount;

    /** 组内需求单数（非取消/删除单；含待确认 SUBMITTED / 草稿 DRAFT）。row166 状态口径用。 */
    private Integer demandCount;

    /** 组内已确认需求单数（demand_status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）。row166。 */
    private Integer confirmedDemandCount;

    /**
     * 需求状态三态（前端 dict 文案）：
     * {@code PENDING 待确认（无一已确认）/ ALL_CONFIRMED 已全部确认 / PARTIAL 部分确认}。
     *
     * <p>row166 口径修正：按【需求单】而非【门店】聚合——同一门店可有多条需求（部分确认 + 部分待确认），
     * 按门店会把「该店有任一已确认」误判成 ALL_CONFIRMED。改按 confirmedDemandCount / demandCount 判定：
     * 只要有一条未确认（SUBMITTED/DRAFT）就是 PARTIAL（已部分确认）。</p>
     */
    private String demandStatus;

    /**
     * 需求确认率（{@link #confirmedDemandCount} / {@link #demandCount}，即已确认<b>需求单</b>数 / 组内需求单数，
     * 0~1 小数；前端 toFixed 转 %）。口径与 {@link #demandStatus} 三态同源（row166 按需求单而非门店）。
     *
     * <p>row32：列表默认按本字段<b>升序</b>排——排序在 mapper 的 ORDER BY 里用同一算式做，
     * 不在 service / 前端重排（列表是聚合全量后内存分页，只排一页会跨页串序）。</p>
     */
    private BigDecimal confirmRate;

    /** 需求最终确认时间（组内 MAX confirmer_time）。 */
    private LocalDateTime lastConfirmTime;

    /**
     * 下单时间 = 组内<b>最早</b>一单的 {@code create_time}（row181）。
     *
     * <p>一行是同日同产品的 N 家门店合并，下单时间不是单值；取最早那一单，与 {@link #ordererName}
     * 指向同一条需求单。</p>
     */
    private LocalDateTime orderTime;

    /**
     * 下单人（row181）：组内最早一单的下单人昵称；组内下单人多于一个时形如 {@code 张三 等 3 人}
     * （N = 组内 distinct {@code create_by}）。文本在 SQL 里拼好，与 mp 门店需求日卡同形态。
     */
    private String ordererName;
}
