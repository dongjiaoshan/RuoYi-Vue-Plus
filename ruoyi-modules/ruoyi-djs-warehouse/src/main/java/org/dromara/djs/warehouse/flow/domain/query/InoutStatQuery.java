package org.dromara.djs.warehouse.flow.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 出入库统计查询参数（V6-R167 入库统计 / 出库统计共用）。
 *
 * <p>与兄弟页「出入库月汇总」的 {@code InoutSummaryQuery} 同一套聚合口径，唯一差别是筛选入口：
 * 那页先选月份再下钻（{@code statMonth} 必填），本页是<b>日期区间 + 顶部 Tab</b>，
 * 区间两端可空（= 不限），默认近一个月由前端给。</p>
 *
 * <p>🔴 导出端点必须把本类作为方法参数直接接（WebDataBinder 命令对象绑定），
 * 不能拆成 {@code @RequestParam List<String>}：前端 {@code proxy.download} 走
 * {@code tansParams} 把数组序列化成 {@code flowTypes[0]=a&flowTypes[1]=b} 的索引形式，
 * {@code @RequestParam} 收不到，会静默丢掉多选筛选让导出比列表多行。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Data
public class InoutStatQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 起始日期（含，可空 = 不限；yyyy-MM-dd，全局 String→Date 转换器解析）。
     */
    private Date dateFrom;

    /**
     * 截止日期（含，可空 = 不限）。SQL 里按 {@code < dateTo + 1 天} 比，
     * 因为 {@code flow_date} 是 DATETIME，写 {@code <=} 会漏掉当天带时分秒的流水。
     */
    private Date dateTo;

    /**
     * 产品名称模糊（入库统计 / 出库统计共用）。
     */
    private String productName;

    /**
     * 产品类型多选（djs_product_type：1 自产 / 2 外购；入库统计 / 出库统计共用）。
     */
    private List<Integer> productTypes;

    /**
     * 入库方式多选（djs_flow_type 入库方向白名单；仅入库统计使用）。
     */
    private List<String> flowTypes;

    /**
     * 供应商 ID 精确匹配（仅入库统计使用；前端按 string 传，Long 接收解析）。
     *
     * <p>下拉选项来自供应商主数据、打字过滤，选中即精确筛 —— 与「入库记录」「入库汇总」
     * 两页共用同一份选项来源与同一套 id 口径，同一个供应商不会出现「一页搜得到、一页搜不到」。</p>
     */
    private Long supplierId;

    /**
     * 只看「无供应商」那一桶（仅入库统计使用）。
     *
     * <p>甲方「供应商为空的统计到一起」要求空供应商能作为一个统计桶被单独筛出来，
     * 而空桶用 {@code supplierName} 表达不了（空串 = 不筛）。选中下拉里的「无供应商」时
     * 前端传 {@code true} 且<b>不传</b> {@code supplierName}，两者互斥。</p>
     */
    private Boolean noSupplier;

    /**
     * 出库去向多选（djs_stock_out_dest；仅出库统计使用）。
     */
    private List<String> stockOutDests;
}
