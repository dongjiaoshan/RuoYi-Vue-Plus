package org.dromara.djs.warehouse.inout.domain.vo;

/** receiptId 指向本次主入库流水；同键成功重放返回相同回执。 */
public record WorkbenchSubmitVo(String requestId, Long receiptId, Long cutRecordId) { }
