package org.dromara.djs.plant.plan.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.djs.plant.plan.domain.PlantDetails;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 种植计划明细 VO（PLT-PLAN-001）。
 *
 * <p>{@code plotName / plotCode / cropName / plantTeamName / harvestTeamName} 由 service 批量 enrich。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlantDetails.class)
public class PlantDetailsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelIgnore
    private Long id;
    @ExcelIgnore
    private Long plantId;
    @ExcelIgnore
    private Long plotId;
    @ExcelIgnore
    private Long cropId;
    @ExcelIgnore
    private Integer plantMonth;
    @ExcelIgnore
    private String plantPeriod;
    @ExcelIgnore
    private LocalDate beginActualdate;
    @ExcelIgnore
    private LocalDate endActualdate;
    @ExcelProperty(value = "开始采摘日期")
    private LocalDate beginHarvestdate;
    @ExcelProperty(value = "结束采摘日期")
    private LocalDate endHarvestdate;
    @ExcelProperty(value = "计划最早采摘")
    private LocalDate earliestHarvestdate;
    @ExcelProperty(value = "计划最晚采摘")
    private LocalDate lastHarvestdate;
    @ExcelIgnore
    private String plantStatus;
    @ExcelProperty(value = "采摘状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_pick_status")
    private String harvestStatus;
    @ExcelProperty(value = "地块面积(亩)")
    private BigDecimal plotArea;
    @ExcelProperty(value = "标准产量(kg)")
    private BigDecimal expectedYield;
    @ExcelProperty(value = "损耗(kg)")
    private BigDecimal lossYield;

    /**
     * 预计净产量（kg）= max(0, 标准产量 expectedYield − 灾害损失 lossYield)。
     *
     * <p>row185：采摘计划调整弹窗「标准产量」需扣除本地块灾害损失（per-plot {@code loss_yield} 累计灾害损失），
     * 钳 0 防负。由 PickPlanServiceImpl.enrich 计算回填；原始 {@link #expectedYield} / {@link #lossYield} 仍保留。</p>
     */
    @ExcelProperty(value = "预计净产量(kg)")
    private BigDecimal netExpectedYield;
    @ExcelProperty(value = "实际产量(kg)")
    private BigDecimal actualYield;
    @ExcelIgnore
    private BigDecimal averageYield;
    @ExcelIgnore
    private Long plantBy;
    @ExcelIgnore
    private Long harvestBy;
    @ExcelProperty(value = "是否采摘活动", converter = DictOrRawConvert.class)
    @ExcelDictFormat(readConverterExp = "1=是,2=否")
    private Integer isPick;

    /** 是否移栽调整（PLT-TRANSPLANT-REDO-001）：1=该明细由育苗移栽落地生成（plot_id=目标地块）。 */
    @ExcelProperty(value = "是否移栽调整", converter = DictOrRawConvert.class)
    @ExcelDictFormat(readConverterExp = "1=是,0=否")
    private Integer transplantAdjusted;

    /**
     * 变更类型（字典 {@code djs_plant_change_type}，V6-R36）：
     * mp=小程序操作 / admin=后台调整 / admin_team=后台班组调整。
     */
    @ExcelProperty(value = "变更类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_plant_change_type")
    private String changeType;

    @ExcelIgnore
    private Date createTime;

    // enrich
    @ExcelProperty(value = "种植地块")
    private String plotName;
    @ExcelProperty(value = "地块编码")
    private String plotCode;
    @ExcelProperty(value = "作物名称")
    private String cropName;
    @ExcelProperty(value = "种植班组")
    private String plantTeamName;
    @ExcelProperty(value = "采摘班组")
    private String harvestTeamName;

    /** 种植班组全集 id（G1-TEAMS-MULTISELECT，row36；编辑回显用）。 */
    @ExcelIgnore
    private List<Long> plantByIds;
    /** 采摘班组全集 id（编辑回显用）。 */
    @ExcelIgnore
    private List<Long> harvestByIds;
    /** 种植班组全集名（多 tag / 逗号展示；空则回落 {@link #plantTeamName}）。 */
    @ExcelIgnore
    private List<String> plantTeamNames;
    /** 采摘班组全集名（多 tag / 逗号展示；空则回落 {@link #harvestTeamName}）。 */
    @ExcelIgnore
    private List<String> harvestTeamNames;

    /** 种植日期（取实际开始种植 begin_actualdate；采摘计划详情列展示用）。 */
    @ExcelProperty(value = "种植日期")
    private LocalDate plantDate;
}
