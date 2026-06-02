package org.dromara.djs.warehouse.trace.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 追溯码查询 BO（TRC-CORE-001 提供骨架，TRC-ADMIN-001 D14 复用扩展）。
 *
 * <p>本 ticket 无 controller，genCode 走裸入参 {@code (productId, pigEarNo, plotId)}；
 * 此 BO 供下游 admin 端追溯码管理列表查询条件用，避免 D14 重新定义。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Data
public class TraceCodeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 追溯码（模糊 / 精确，admin 端列表筛选）。
     */
    private String produceCode;

    /**
     * 追溯码类型（字典 {@code djs_trace_code_type}：pork / veg / gift）。
     */
    private String codeType;

    /**
     * 产品 FK。
     */
    private Long productId;

    /**
     * 猪只耳号（猪肉链筛选）。
     */
    private String pigEarNo;

    /**
     * 来源地块 FK（果蔬链筛选）。
     */
    private Long plotId;

    /**
     * 门店 FK。
     */
    private Long storeId;

}
