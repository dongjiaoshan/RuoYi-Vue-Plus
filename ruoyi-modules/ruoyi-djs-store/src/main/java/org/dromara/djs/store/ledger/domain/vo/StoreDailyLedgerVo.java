package org.dromara.djs.store.ledger.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店经营流水盘点台账行 VO（盘点详情 / 产品盘点历史共用，STORE-LEDGER-001）。
 *
 * <p>{@code storeName} / {@code productName} / {@code productUnit} 由 service 内存聚合批量填（避免 N+1）；
 * {@code operatorName} 走 {@code USER_ID_TO_NICKNAME}。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreDailyLedger.class)
public class StoreDailyLedgerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long storeId;

    /** 门店名称（service 内存聚合填）。 */
    @ExcelProperty(value = "门店")
    private String storeName;

    private Long productId;

    /** 产品名称（service 内存聚合填）。 */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /** 产品单位（service 内存聚合填）。 */
    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "盘点日期")
    private LocalDate ledgerDate;

    @ExcelProperty(value = "期初库存")
    private BigDecimal openingQty;

    @ExcelProperty(value = "当日入库")
    private BigDecimal inboundQty;

    @ExcelProperty(value = "销售量")
    private BigDecimal saleQty;

    @ExcelProperty(value = "赠送量")
    private BigDecimal giftQty;

    @ExcelProperty(value = "退货量")
    private BigDecimal returnQty;

    @ExcelProperty(value = "损耗量")
    private BigDecimal lossQty;

    @ExcelProperty(value = "期末库存")
    private BigDecimal closingQty;

    private Long operatorId;

    /** 盘点人姓名（USER_ID_TO_NICKNAME）。 */
    @ExcelProperty(value = "盘点人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    private String remark;

    @ExcelProperty(value = "盘点时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
