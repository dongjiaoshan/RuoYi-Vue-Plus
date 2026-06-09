package org.dromara.djs.warehouse.vegdock.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购果蔬到货记录 VO（FIX-WMS-MP-VEGDOCK-001）。
 *
 * <p>mp 两处使用：</p>
 * <ul>
 *   <li>果蔬月台「外购收货」tab：按 crop 聚合后展示「品种 + 待入库重量」（图 42）</li>
 *   <li>果蔬间入库：单品种下钻每笔到货行「待入库量 vs 实际入库量 + 状态」（图 43）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Data
public class VegPurchaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long cropId;

    private String cropName;

    private Long productId;

    private String supplierCode;

    private String supplierName;

    private String source;

    private Long locationId;

    /**
     * 到货重量(kg)。
     */
    private BigDecimal arrivalWeight;

    /**
     * 待入库量(kg)。
     */
    private BigDecimal pendingWeight;

    /**
     * 实际入库量(kg)。
     */
    private BigDecimal actualWeight;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date arrivalTime;

    /**
     * 状态 {@code djs_veg_purchase_status}：pending / processing / done。
     */
    private String status;

    private String proofOssIds;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

}
