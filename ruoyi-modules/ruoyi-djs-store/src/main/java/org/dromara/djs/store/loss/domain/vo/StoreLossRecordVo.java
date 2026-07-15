package org.dromara.djs.store.loss.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.store.loss.domain.StoreLossRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店损耗记录 VO（DENGBO-R15）。
 *
 * <p>{@code storeName} / {@code productName} 由查询方内存聚合填（本 ticket 仅定时落盘，无查询端点，展示列预留）。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
@Data
@AutoMapper(target = StoreLossRecord.class)
public class StoreLossRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long storeId;

    /** 门店名称（查询方内存聚合填）。 */
    private String storeName;

    private Long productId;

    /** 产品名称（查询方内存聚合填）。 */
    private String productName;

    /** 产品单位。 */
    private String productUnit;

    /** 损耗量。 */
    private BigDecimal lossQty;

    /** 损耗日期。 */
    private LocalDate lossDate;

    /** 损耗类型：store_daily_loss / white_bar_split_loss。 */
    private String lossType;

    /** 白条到店重量kg（仅白条分割损耗行）。 */
    private BigDecimal whiteBarArriveWeight;

    /** 白条退回入库重量kg（仅白条分割损耗行）。 */
    private BigDecimal whiteBarSplitWeight;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
