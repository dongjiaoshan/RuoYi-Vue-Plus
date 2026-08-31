package org.dromara.djs.warehouse.location.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.location.domain.LocationInfo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库位视图对象（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = LocationInfo.class)
public class LocationInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 库位业务码。
     */
    @ExcelProperty(value = "库位编码")
    private String locationCode;

    /**
     * 库位名称。
     */
    @ExcelProperty(value = "库位名称")
    private String locationName;

    /**
     * 库位类型（字典 djs_location_type）。
     */
    @ExcelProperty(value = "类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_location_type")
    private String locationType;

    /**
     * 缩略图。
     */
    @ExcelProperty(value = "缩略图")
    private String locationThumb;

    /**
     * 原图。
     */
    @ExcelProperty(value = "原图")
    private String locationImg;

    /**
     * 状态。
     */
    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_common_status")
    private Integer locationStatus;

    /**
     * 库位排序（升序，越小越靠前）。
     */
    @ExcelProperty(value = "库位排序")
    private Integer locationSort;

    /**
     * 库位说明。
     */
    @ExcelProperty(value = "库位说明")
    private String locationDesc;

    /**
     * 容量。
     */
    @ExcelProperty(value = "容量")
    private BigDecimal capacity;

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

    /**
     * 更新时间。
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。
     */
    private Long updateBy;

    /**
     * 更新人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

}
