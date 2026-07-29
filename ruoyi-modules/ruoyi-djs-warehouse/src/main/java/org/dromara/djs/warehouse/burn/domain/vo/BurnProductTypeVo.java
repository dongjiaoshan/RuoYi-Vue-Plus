package org.dromara.djs.warehouse.burn.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 燎毛入库产品类型 VO（D12X-MP-BURN-IA-001 mp 入库子页用）。
 *
 * <p>白条入库按产品分别入库（客户 §2.3）。本 VO = {@code t_warehouse_product_info} 中
 * 「生产车间=燎毛间 + 产品属性=原材料 + 状态=正常」的产品（含白条本体「半扇」与猪头 / 猪脚等
 * 燎毛间原材料），由 admin 产品配置驱动、不绑固定业务码。
 * mp 入库子页每产品一行，录入该产品重量 → 提交时 productTypeItems.productId 指向所选产品。</p>
 *
 * <p>{@code productId}（snowflake 主键）经 ruoyi JacksonConfig 自动序列化为 string。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Data
public class BurnProductTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake，序列化为 string；提交时作为 productTypeItems.productId）。
     */
    private Long productId;

    /**
     * 产品业务码（{@code t_warehouse_product_info.product_id}，如 Y00142）。
     */
    private String productCode;

    /**
     * 产品名称（如 半扇 / 猪头 / 猪脚）。
     */
    private String productName;

    /**
     * 产品图 public URL（IMG-LIB-001 4 层 resolver 回填，L2 兜底 white_bar 默认图）。
     */
    private String imageUrl;

    /**
     * 结构化产品类别（FIX-WMS-MP-BURN-001 录入约束用）：{@code half}=白条半只。
     *
     * <p>按产品主数据 {@code belong_type} 判定 —— {@code white_bar}（产品类别=白条产品）即白条本体，
     * 一头猪出左右两扇 → mp 限录 2 次、后端 finishBurn 须集齐 2 扇才放行。判据不绑业务码，
     * 甲方在 admin 增删白条产品无需改代码。</p>
     *
     * <p>其余燎毛间原材料（猪头 / 猪脚 等 {@code belong_type='pork'}）返回 {@code null}，
     * 不限次；mp 端按 productName 回落判是否隐藏白条编码。</p>
     */
    private String productType;

    /**
     * 该产品在当前白条已入库份数（row171）。
     *
     * <p>仅当 {@code productTypes} 端点带 {@code barInfoId} 入参时回填 = 该白条 {@code product_inhouse}
     * 中该 productId 的行数（半只两扇分两次录 → 2；未录 → 0）。用于 mp 卡片「已入库 x/2」计数在
     * 页面重进 / 热重载后仍准确（不再仅靠前端 session Map，重进 singing 白条时 session 为空会丢计数）。
     * 不带 barInfoId 时恒 0。</p>
     */
    private Integer recordedCount;

}
