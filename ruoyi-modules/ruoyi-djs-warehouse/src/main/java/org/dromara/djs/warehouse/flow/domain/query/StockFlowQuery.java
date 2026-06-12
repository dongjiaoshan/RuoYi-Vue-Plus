package org.dromara.djs.warehouse.flow.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 出入库流水查询参数（admin 流水查询页 + admin 包材领用页隐式过滤 + mp 我的领用查询）。
 *
 * <p>多维度筛选：</p>
 * <ul>
 *   <li>{@code flowType} 业务语义（如 pick_out / return_in / loss）</li>
 *   <li>{@code inoutType} 出入（IN / OT）</li>
 *   <li>{@code matType} 物资类型（前端语义；service 内部 JOIN product_info.belong_type 过滤）</li>
 *   <li>{@code productId} / {@code productCode} 产品筛选</li>
 *   <li>{@code earNo} 猪只耳号 / {@code plotId} 地块（与产品三选一场景）</li>
 *   <li>{@code dateFrom} / {@code dateTo} 业务时间区间</li>
 *   <li>{@code operatorId} 操作人（mp 端"我的"查询自动填）</li>
 *   <li>{@code locationId} 库位筛选</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
public class StockFlowQuery {

    /**
     * 流水号精确匹配。
     */
    private String flowNo;

    /**
     * 流水类型 djs_flow_type 字典 value（如 pick_out / return_in / loss）。
     */
    private String flowType;

    /**
     * 出入类型（IN 入 / OT 出）。
     */
    private String inoutType;

    /**
     * 物资类型（djs_mat_type 字典，service 内部映射到 product.belong_type）。
     */
    private String matType;

    /**
     * 产品 ID 精确匹配。
     */
    private Long productId;

    /**
     * 产品业务码（PROD-xxx）模糊匹配。
     */
    private String productCode;

    /**
     * 产品名称模糊匹配（service 内部反查 product.id 集合后作为 productId IN 下推）。
     */
    private String productName;

    /**
     * 耳号精确匹配。
     */
    private String earNo;

    /**
     * 地块 ID 精确匹配。
     */
    private Long plotId;

    /**
     * 地块编号（= {@code t_plant_plot_info.plot_code}）模糊匹配，
     * service 内部反查 plot.id 集合后作为 plotId IN 下推。
     */
    private String blockNo;

    /**
     * 库位 ID（stockFlow.warehouse_id 物理列名，实为 location FK）精确匹配。
     */
    private Long warehouseId;

    /**
     * 关联需求单 ID 精确匹配（D14 CROSS-FLOW-003 按本字段聚合 ship_out 流水）。
     */
    private Long demandId;

    /**
     * 出库去向（djs_stock_out_dest）。
     */
    private String stockOutDest;

    /**
     * 操作人（mp 我的查询自动填）。
     */
    private Long operatorId;

    /**
     * 入/出库人姓名模糊匹配（admin 流水页按昵称过滤；service 内部反查 user.id 集合后作为 operatorId IN 下推）。
     */
    private String operatorName;

    /**
     * 业务时间起。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateFrom;

    /**
     * 业务时间止。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateTo;

}
