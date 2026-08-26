package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.flow.domain.StockFlow;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import org.dromara.djs.warehouse.stock.domain.PlotLabel;

/**
 * 出入库流水 VO（WMS-MAT-001）。
 *
 * <p>覆盖 admin 流水查询页 + mp 流水回显两端用：</p>
 * <ul>
 *   <li>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译</li>
 *   <li>{@code productName} / {@code locationName} service 层 JOIN 回填（避免 N+1，参 LocationStockServiceImpl.fillLocationNames 模式）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StockFlow.class)
public class StockFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "流水号")
    private String flowNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "业务时间")
    private Date flowDate;

    private Long productId;

    /**
     * 产品类型 djs_product_type（1 自产 / 2 外购，已废弃 3 礼盒；service JOIN 回填，
     * 与「归属/产品类别」belongType 是两个不同维度，礼盒 = 自产 + belongType=gift_box）。
     */
    @ExcelProperty(value = "产品类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_product_type")
    private Integer productType;

    /**
     * 产品名（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "产品")
    private String productName;

    /**
     * 产品业务码 PROD-xxx（service JOIN 回填）。
     */
    @ExcelProperty(value = "产品码")
    private String productCode;

    /**
     * 产品归属 djs_belong_type（service JOIN 回填，便于 admin 流水页按 matType 过滤）。
     */
    @ExcelProperty(value = "归属", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String belongType;

    /**
     * 单位（service JOIN 回填）。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /**
     * 商品分类 djs_buy_class（service JOIN 回填，mp 领用记录卡展示「商品分类」+ 商品分类筛选）。
     */
    @ExcelProperty(value = "商品分类", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_buy_class")
    private String buyClass;

    /**
     * 库位 ID（DDL 列名 warehouse_id，实为 location FK；详 {@link StockFlow#warehouseId}）。
     */
    private Long warehouseId;

    /**
     * 库位名（service 回填）。
     */
    @ExcelProperty(value = "库位")
    private String locationName;

    /**
     * 关联需求单 ID（仅 {@code flow_type=ship_out} 写入；D14 CROSS-FLOW-003 聚合 shipped_count）。
     */
    private Long demandId;

    @ExcelProperty(value = "出入", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_inout_type")
    private String inoutType;

    @ExcelProperty(value = "类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_flow_type")
    private String flowType;

    private String stockInType;

    private String stockOutType;

    @ExcelProperty(value = "出库去向", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_stock_out_dest")
    private String stockOutDest;

    @ExcelProperty(value = "变动数量(±)")
    private BigDecimal changeNum;

    @ExcelProperty(value = "变动绝对值")
    private BigDecimal changeQuantity;

    private Long supplierId;

    @ExcelProperty(value = "耳号")
    private String earNo;

    private Long plotId;

    /**
     * 地块编号（= {@code t_plant_plot_info.plot_code}，service 层 JOIN 回填；
     * 流水行 {@code plotId} 为空 → blockNo 保持 null）。
     */
    @ExcelProperty(value = "地块编号")
    private String blockNo;

    /**
     * 地块名称（= {@code t_plant_plot_info.plot_name}，service 层 JOIN 回填；
     * 流水行 {@code plotId} 为空 → 保持 null）。
     *
     * <p>接口出参用它，导出件不用它 —— 导出的「地块」列走 {@link #getPlotLabel()}。</p>
     */
    private String plotName;

    /**
     * 【三期】标识（0=否 / 1=是；V6 row92）。
     *
     * <p>接口出参，前端 {@code formatPlotLabel} 用它决定「地块」列渲染成什么。
     * 导出件不单列它 —— 裸 0/1 对甲方无意义，语义已经并进 {@link #getPlotLabel()}。</p>
     */
    private Integer thirdPhase;

    /**
     * 导出件的「地块」列（V6 row92 甲方原话「地块记录为三期两个字」）。
     *
     * <p>由 service 用 {@link PlotLabel#of} 回填，规则与页面
     * {@code plus-ui/src/utils/plotTag.ts#formatPlotLabel} <b>完全相同</b>：
     * 三期 → 「三期」/ 有真实地块 → 地块名 / 都没有 → {@code -}。
     * 导出与页面必须同口径，否则甲方拿导出对账会重报同一条。</p>
     *
     * <p>做成回填字段而不是派生 getter：本项目 Excel 走 FastExcel 的<b>字段</b>扫描，
     * 方法级 {@code @ExcelProperty} 无先例、行为不确定。</p>
     */
    @ExcelProperty(value = "地块")
    private String plotLabel;

    private Long operatorId;

    /**
     * 操作人姓名（注解翻译）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
