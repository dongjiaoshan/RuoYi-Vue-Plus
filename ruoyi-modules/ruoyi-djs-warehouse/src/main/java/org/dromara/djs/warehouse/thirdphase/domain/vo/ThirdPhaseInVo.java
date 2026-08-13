package org.dromara.djs.warehouse.thirdphase.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.thirdphase.domain.ThirdPhaseIn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 三期作物入库列表行 / 导出模型（V6 row88）。
 *
 * <p>列与 admin 列表表头逐列一致：日期 / 作物名称 / 产品名称 / 入库量(kg) / 入库库位 / 入库人。
 * {@link ExcelIgnoreUnannotated} 下只导出标了 {@link ExcelProperty} 的列，内部主键不进 xlsx。</p>
 *
 * <p>雪花 id 的 JS 精度问题由全局 {@code BigNumberSerializer}（ruoyi-common-json）统一处理，
 * 这里不逐字段标注。</p>
 *
 * @author djs
 * @since V6-R88
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ThirdPhaseIn.class)
public class ThirdPhaseInVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    private Long id;

    /**
     * 入库时间（列表 / 导出均显示到时分）。
     *
     * <p>{@link JsonFormat} 覆盖全局默认的 {@code yyyy-MM-dd HH:mm:ss} ——
     * 甲方明确要求「显示到时分」，格式化放后端做，前端不再各自截串。</p>
     */
    @ExcelProperty(value = "日期")
    @DateTimeFormat("yyyy-MM-dd HH:mm")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private Date inTime;

    /** 作物 id（内部主键，不导出）。 */
    private Long cropId;

    /** 作物名称（快照）。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 产品 id（内部主键，不导出）。 */
    private Long productId;

    /** 入库的产品名称（快照）。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 入库量（kg，3 位小数）。 */
    @ExcelProperty(value = "入库量(kg)")
    private BigDecimal stockNum;

    /** 库位 id（内部主键，不导出）。 */
    private Long locationId;

    /** 入库库位名称（快照，恒「毛菜鲜品库」）。 */
    @ExcelProperty(value = "入库库位")
    private String locationName;

    /** 入库人 id（内部主键，不导出）。 */
    private Long operatorId;

    /** 入库人姓名（service 反查 {@code sys_user.nick_name} 回填）。 */
    @ExcelProperty(value = "入库人")
    private String operatorName;

    /**
     * 本次打【三期】标识的地块名逗号串。
     *
     * <p>甲方列表 6 列里没有这一列，但接口返 —— 它是「标识到底打上没有」的唯一可见证据，
     * 空串就代表该作物当时没有在种地块、一块地都没打上。</p>
     */
    private String plotNames;

    /** 备注。 */
    private String remark;
}
