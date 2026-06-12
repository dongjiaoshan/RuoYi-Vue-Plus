package org.dromara.djs.plant.crop.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.crop.domain.CropInfo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 作物视图对象（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CropInfo.class)
public class CropInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "作物编码")
    private String cropCode;

    @ExcelProperty(value = "作物名称")
    private String cropName;

    private String cropImagePreview;
    private String cropImageUrl;

    @ExcelProperty(value = "品种名")
    private String varietyName;

    @ExcelProperty(value = "品种来源")
    private String varietyOrigin;

    @ExcelProperty(value = "科属", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_crop_family")
    private String cropFamily;

    @ExcelProperty(value = "关联产品ID")
    private Long relatedProduct;

    @ExcelProperty(value = "种植季节")
    private String plantingSeason;

    @ExcelProperty(value = "播种期")
    private String sowingPeriod;

    @ExcelProperty(value = "最大周期(天)")
    private Integer maxCycle;

    @ExcelProperty(value = "最小周期(天)")
    private Integer minCycle;

    @ExcelProperty(value = "施肥间隔(天)")
    private Integer fertilizationInterval;

    @ExcelProperty(value = "浇灌间隔(天)")
    private Integer irrigationInterval;

    @ExcelProperty(value = "预计亩产(kg)")
    private BigDecimal predictedPer;

    @ExcelProperty(value = "品质描述")
    private String qualityDesc;

    @ExcelProperty(value = "采摘单价(元/斤)")
    private BigDecimal pickUnitPrice;

    /** 主图 ossId（IMG-LIB-001 L1）。 */
    private String imageOssId;

    /** 图来源（IMG-LIB-001：0 自动 / 1 手动）。 */
    private Integer imageSource;

    /** 主图 public URL（IMG-LIB-001 resolver 4 层兜底回填）。 */
    private String imageUrl;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 更新时间（BaseEntity，AutoMapper 自动回填；列表时间列对齐原型「更新时间」）。 */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /** 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射翻译成 updateByName。 */
    private Long updateBy;

    /** 更新人姓名（注解翻译，VO 序列化时填）。 */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

    /**
     * 历史种植次数（派生统计，按 crop_id 聚合 {@code t_plant_plant_details} COUNT）。
     *
     * <p>service 层 {@code queryPageList} 批量 enrich 回填，避免 N+1；无种植记录返 0。</p>
     */
    private Long historyPlantCount;

    /**
     * 平均亩产 kg/亩（派生统计，AVG({@code t_plant_plant_details.average_yield})，仅纳入有实际产量行）。
     *
     * <p>service 层批量 enrich 回填；无数据返 null。决策#7 明细/卡片用 kg。</p>
     */
    private BigDecimal avgYield;

    /**
     * 最大亩产 kg/亩（派生统计，MAX({@code t_plant_plant_details.average_yield})）。
     *
     * <p>service 层批量 enrich 回填；无数据返 null。</p>
     */
    private BigDecimal maxYield;
}
