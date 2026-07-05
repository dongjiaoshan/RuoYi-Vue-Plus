package org.dromara.djs.warehouse.demand.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量确认需求入参（row41）。
 *
 * <p>前端在需求汇总列表勾选若干「需求日期 + 需求产品」分组行 → 一次确认这些分组下所有
 * SUBMITTED 态的门店需求单（等价逐条走「查看需求」里的确认，状态机逻辑一致）。</p>
 *
 * @author djs
 */
@Data
public class DemandBatchConfirmBo {

    /** 选中的分组（需求日期 + 产品）。 */
    @NotEmpty(message = "请至少选择一条需求")
    private List<GroupKey> groups;

    /** 确认备注（可选，写入每条需求的状态流转历史）。 */
    private String remark;

    /**
     * 分组键：一行汇总 = 一个（需求日期，需求产品）。
     */
    @Data
    public static class GroupKey {

        /** 需求日期 yyyy-MM-dd。 */
        private String demandDate;

        /** 需求产品 id（雪花，前端 string 传入 Jackson 自动转 Long）。 */
        private Long productId;
    }
}
