package org.dromara.djs.warehouse.stock.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.djs.common.excel.DictOrRawConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.stock.domain.LocationStock;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import org.dromara.djs.warehouse.stock.domain.PlotLabel;

/**
 * 库存明细视图对象（WMS-MD-001）。
 *
 * <p>{@code locationName} 通过 ruoyi {@link Translation} 翻译（联表 {@code t_warehouse_location_info.location_name}）—
 * 当前 ruoyi 翻译注册表暂无 location 类型，admin 端 list 接口走 JOIN 已在
 * {@link org.dromara.djs.warehouse.stock.service.impl.LocationStockServiceImpl} 处理；
 * 此处仅暴露 locationId + locationName（service 层 JOIN 时回填）。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = LocationStock.class)
public class LocationStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库存 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 产品代码（业务码 {@code ProductInfo.productId}，service 层按 {@code product_id} FK 回填；如 P10002）。
     */
    @ExcelProperty(value = "产品代码")
    private String productCode;

    /**
     * 库位 ID。
     */
    @ExcelProperty(value = "库位ID")
    private Long locationId;

    /**
     * 库位名称（由 service JOIN 回填）。
     */
    @ExcelProperty(value = "库位名称")
    private String locationName;

    /**
     * 产品 ID。
     */
    @ExcelProperty(value = "产品ID")
    private Long productId;

    /**
     * 耳号。
     */
    @ExcelProperty(value = "耳号")
    private String earNo;

    /**
     * 白条流水号（半只/整只白条唯一标识）。白条库存按 white_bar_no 半只展示；耳号为空(外购)时以此作白条标识列。
     */
    @ExcelProperty(value = "白条流水号")
    private String whiteBarNo;

    /**
     * 地块 ID。
     */
    @ExcelProperty(value = "地块ID")
    private Long plotId;

    /**
     * 地块编号（service JOIN t_plant_plot_info.plot_code 回填）。
     */
    @ExcelProperty(value = "地块编号")
    private String blockNo;

    /**
     * 地块名称（service JOIN t_plant_plot_info.plot_name 回填；库存行 {@code plotId} 为空 → null）。
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
     * {@code plus-ui/src/utils/plotTag.ts#formatPlotLabel} 完全相同：
     * 三期 → 「三期」/ 有真实地块 → 地块名 / 都没有 → {@code -}。</p>
     */
    @ExcelProperty(value = "地块")
    private String plotLabel;

    /**
     * 药品 ID（FK → t_breed_medicine_info.id；ADR-0012 药品归仓库库位统一）。
     */
    @ExcelProperty(value = "药品ID")
    private Long medicineId;

    /**
     * 业态归属（字典 {@code djs_belong_type}；service JOIN product_info 回填）。
     * 库存详情「饲料饲喂记录」tab 仅当 {@code belongType='vegetable' && productAttr==2} 显示。
     */
    @ExcelProperty(value = "产品类别", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_belong_type")
    private String belongType;

    /**
     * 产品属性（{@code 1 生产产品 / 2 原材料}；service JOIN product_info 回填）。
     */
    @ExcelProperty(value = "产品属性", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_product_attr")
    private Integer productAttr;

    /**
     * 产品名称。
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 产品规格（service JOIN product_info 回填；库存查询列表产品名称右侧展示）。
     */
    @ExcelProperty(value = "产品规格")
    private String productSpec;

    /**
     * 当前库存。
     */
    @ExcelProperty(value = "当前库存")
    private BigDecimal productStock;

    /**
     * 产品单位。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /**
     * 是否完成。
     */
    @ExcelProperty(value = "是否完成", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isEnd;

    /**
     * 最新盘点时间。
     */
    @ExcelProperty(value = "最新盘点时间")
    private Date latestCheckTime;

    /**
     * 盘点结果。
     */
    @ExcelProperty(value = "盘点结果", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_check_result")
    private Integer checkResult;

    /**
     * 最后操作人 ID。
     */
    @ExcelProperty(value = "操作人ID")
    private Long operatorId;

    /**
     * 最后操作人姓名（ruoyi {@link Translation} USER_ID_TO_NICKNAME 翻译 sys_user.nick_name 中文名）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    @ExcelProperty(value = "操作人")
    private String operatorName;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
