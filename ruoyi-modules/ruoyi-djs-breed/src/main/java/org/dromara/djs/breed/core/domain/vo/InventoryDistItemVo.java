package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

/**
 * 库存看板 - 分布桶项（胎次分布 / 日龄分布通用，BRD-INVENTORY-001）。
 * label 为分桶展示名（如 "1胎" / "0-30天"），count 为该桶头数。
 */
@Data
public class InventoryDistItemVo {

    /** 分桶展示名（胎次："1胎"；日龄："0-30天"） */
    private String label;

    /** 该桶头数 */
    private Integer count;
}
