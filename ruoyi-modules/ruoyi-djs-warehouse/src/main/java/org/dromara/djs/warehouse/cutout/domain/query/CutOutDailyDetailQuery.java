package org.dromara.djs.warehouse.cutout.domain.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 猪肉日分割出库统计「查看」明细参数（V7-R6）：把明细钉到列表的某一行。
 *
 * <p>前端从被点击的汇总行原样带：{@code statDate + destType}，再按类别带
 * {@code outDest}（仓库出库，字典原始值）或 {@code storeId}（门店需求）。
 * 明细与汇总共用同一份 FROM / WHERE（见 {@code CutOutDailyMapper}），逐条加起来等于该行出库总重量。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Data
public class CutOutDailyDetailQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 列表行类别：仓库出库。 */
    public static final String DEST_TYPE_WAREHOUSE = "warehouse";
    /** 列表行类别：门店需求。 */
    public static final String DEST_TYPE_STORE = "store";

    /** 统计日期（yyyy-MM-dd）。 */
    @NotNull(message = "统计日期不能为空")
    private Date statDate;

    /** 行类别：warehouse 仓库出库 / store 门店需求。 */
    @NotBlank(message = "出库类别不能为空")
    @Pattern(regexp = "^(warehouse|store)$", message = "出库类别只能是 warehouse 或 store")
    private String destType;

    /** 出库去向字典原始值（destType=warehouse 时使用；「未指定」那一桶传空串）。 */
    private String outDest;

    /** 门店 ID（destType=store 时必填）。 */
    private Long storeId;
}
