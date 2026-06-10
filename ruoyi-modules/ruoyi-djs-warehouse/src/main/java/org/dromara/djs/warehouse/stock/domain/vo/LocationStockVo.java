package org.dromara.djs.warehouse.stock.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
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
     * 地块 ID。
     */
    @ExcelProperty(value = "地块ID")
    private Long plotId;

    /**
     * 产品名称。
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

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
