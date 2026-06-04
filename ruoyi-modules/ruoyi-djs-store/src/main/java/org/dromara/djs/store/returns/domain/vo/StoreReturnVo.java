package org.dromara.djs.store.returns.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.returns.domain.StoreReturn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店退回管理 VO（admin 列表 / 详情 / 导出共用，STR-RETURN-001）。
 *
 * <p>{@code storeName} / {@code productName} 由 service 内存聚合批量填（避免 N+1）；
 * {@code operatorName} 走 {@code USER_ID_TO_NAME}（契约 4.5）。</p>
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreReturn.class)
public class StoreReturnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @ExcelProperty(value = "退回单号")
    private String returnNo;

    @ExcelProperty(value = "退回方向")
    private String returnDirection;

    private Long storeId;

    /** 门店名称（service 内存聚合填）。 */
    @ExcelProperty(value = "退回门店")
    private String storeName;

    private Long productId;

    /** 产品名称（service 内存聚合填）。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    private Long locationId;

    /** 入库库位名称（service 内存聚合填，STR-RETURN-REBUILD-001 K4 联动外购入库目标库位）。 */
    @ExcelProperty(value = "入库库位")
    private String locationName;

    @ExcelProperty(value = "退回数量")
    private BigDecimal returnQuantity;

    @ExcelProperty(value = "退回原因")
    private String returnReason;

    @ExcelProperty(value = "追溯码")
    private String traceCode;

    @ExcelProperty(value = "退回日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnDate;

    @ExcelProperty(value = "会员ID")
    private Long memberId;

    private Long operatorId;

    /** 经手人姓名（USER_ID_TO_NAME，契约 4.5）。 */
    @ExcelProperty(value = "经手人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

    private String remark;

    @ExcelProperty(value = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
