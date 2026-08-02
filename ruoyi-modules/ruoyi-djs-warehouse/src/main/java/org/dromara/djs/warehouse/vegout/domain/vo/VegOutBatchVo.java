package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 毛菜间出库单（admin row187 列表行）：一次提交聚合成一行。
 *
 * @author djs
 */
@Data
public class VegOutBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批量出库单号（stock_flow.batch_no，详情按它查明细）。 */
    private String batchNo;

    /** 出库日期。 */
    private Date outDate;

    /** 出库去向（字典 djs_stock_out_dest）。 */
    private String outDest;

    /** 出库果蔬品类数（该单去重产品数）。 */
    private Integer productKinds;

    /** 出库果蔬重量合计（kg）。 */
    private BigDecimal totalWeight;

    /** 出库操作人 id。 */
    private Long operatorId;

    /** 出库操作人姓名（service 反查 sys_user.nick_name 回填）。 */
    private String operatorName;
}
