package org.dromara.djs.warehouse.stock.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存总览当日明细视图对象（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>某自然日下钻得到的按产品库存情况（弹窗 el-table），仅含当日有库存的产品。
 * compute-on-read 回放 {@code t_warehouse_stock_flow}（Kevin 拍板）：</p>
 * <ul>
 *   <li>期初库存 = Σ(按 inout_type 带符号的 change_quantity) WHERE flow_date &lt; 当日 0 点（累计到昨日末）。</li>
 *   <li>入库量 = Σ change_quantity WHERE inout_type='IN' AND DATE(flow_date)=当日。</li>
 *   <li>出库量 = Σ change_quantity WHERE inout_type='OT' AND DATE(flow_date)=当日（不含 ship_out；
 *       row42「生产产品不入库」：{@code flow_type='ship_out'} 成品发货流水整体不参与回放）。</li>
 *   <li>损耗量 = Σ loss_flow.loss_weight WHERE DATE(loss_date)=当日（信息列，期末不二次扣，
 *       损耗已含在出库流水里）。</li>
 *   <li>饲料饲喂量 = Σ feed_log.feed_weight WHERE DATE(feed_date)=当日（仅果蔬原材料有值）。</li>
 *   <li>期末库存 = 期初 + 入库 − 出库。</li>
 * </ul>
 *
 * <p>产品维度回填来源：当日有库存的产品集合（location_stock）+ 当日有流水的产品集合（stock_flow）
 * 并集；产品图、规格、单位、编码取 {@code t_warehouse_product_info}（COALESCE(product_thumb, image_oss_id)）。</p>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
@Data
@ExcelIgnoreUnannotated
public class StockOverviewDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 id（String 防精度丢失）。 */
    private String productId;

    /** 产品图片 oss_id（COALESCE(product_thumb, image_oss_id)；可空，前端占位兜底）。 */
    private String imageOssId;

    /** 产品编码（业务码）。 */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /** 产品名称。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 规格（product_info.product_spec，如 500g/包；可空）。 */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /** 单位。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /** 库位 id（String 防精度丢失；按库位维度展示库存）。 */
    private String locationId;

    /** 库位名称。 */
    @ExcelProperty(value = "存储库位")
    private String locationName;

    /** 期初库存（= 昨日期末，回放 stock_flow 累计到昨日末）。 */
    @ExcelProperty(value = "期初库存")
    private BigDecimal beginStock;

    /** 入库量（当日 IN 流水绝对值合计）。 */
    @ExcelProperty(value = "入库量")
    private BigDecimal inboundQty;

    /** 出库量（当日 OT 流水绝对值合计，不含 ship_out；含损耗流水）。 */
    @ExcelProperty(value = "出库量")
    private BigDecimal outboundQty;

    /** 损耗量（当日 loss_flow 合计，信息列，期末不二次扣）。 */
    @ExcelProperty(value = "损耗量")
    private BigDecimal lossQty;

    /** 饲料饲喂量（当日 feed_log 合计，仅果蔬原材料有值）。 */
    @ExcelProperty(value = "饲料饲喂量")
    private BigDecimal feedQty;

    /** 期末库存（= 期初 + 入库 − 出库）。 */
    @ExcelProperty(value = "期末库存")
    private BigDecimal endStock;
}
