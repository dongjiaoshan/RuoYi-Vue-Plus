package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量确认需求结果（row41）。
 *
 * <p>逐条确认互不阻断：白条未指定猪只 / 状态不符等单条失败被捕获后计入 {@code failed} + {@code failReasons}，
 * 成功条数计入 {@code confirmed}，供前端弹「成功 X 条，失败 Y 条」。</p>
 *
 * @author djs
 */
@Data
public class DemandBatchConfirmResultVo {

    /** 成功确认条数。 */
    private int confirmed;

    /** 失败条数。 */
    private int failed;

    /** 失败原因（逐条）。 */
    private List<String> failReasons = new ArrayList<>();
}
