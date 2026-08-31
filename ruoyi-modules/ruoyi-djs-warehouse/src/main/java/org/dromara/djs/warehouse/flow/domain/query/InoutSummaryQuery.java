package org.dromara.djs.warehouse.flow.domain.query;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 出入库月汇总下钻查询参数（V6-R155 入库汇总 / V6-R156 出库汇总共用）。
 *
 * <p>🔴 导出端点必须把本类作为方法参数直接接（WebDataBinder 命令对象绑定），
 * 不能拆成 {@code @RequestParam List<String>}：前端 {@code proxy.download} 走
 * {@code tansParams} 把数组序列化成 {@code flowTypes[0]=a&flowTypes[1]=b} 的索引形式，
 * {@code @RequestParam} 收不到，会静默丢掉多选筛选让导出比列表多行。</p>
 *
 * @author djs
 * @since V6-R155
 */
@Data
public class InoutSummaryQuery {

    /**
     * 统计月份 yyyy-MM（必传，由月份列表行带入）。
     */
    @NotBlank
    private String statMonth;

    /**
     * 产品名称模糊（入库汇总 / 出库汇总共用）。
     */
    private String productName;

    /**
     * 产品类型多选（djs_product_type：1 自产 / 2 外购；入库汇总 / 出库汇总共用）。
     */
    private List<Integer> productTypes;

    /**
     * 入库方式多选（djs_flow_type 入库方向白名单；仅入库汇总使用）。
     */
    private List<String> flowTypes;

    /**
     * 供应商名称模糊（仅入库汇总使用）。
     */
    private String supplierName;

    /**
     * 出库去向多选（djs_stock_out_dest；仅出库汇总使用）。
     */
    private List<String> stockOutDests;
}
