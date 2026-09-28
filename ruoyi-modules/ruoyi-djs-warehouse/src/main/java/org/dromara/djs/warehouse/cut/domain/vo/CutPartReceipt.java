package org.dromara.djs.warehouse.cut.domain.vo;

/** 同事务编排使用的实际入库流水和库存篮，避免再次按模糊条件寻找库存。 */
public record CutPartReceipt(Long flowId, Long stockId) { }
