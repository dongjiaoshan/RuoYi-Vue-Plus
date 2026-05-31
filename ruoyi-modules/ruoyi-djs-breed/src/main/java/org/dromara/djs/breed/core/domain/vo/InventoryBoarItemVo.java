package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

/**
 * 库存看板 - 公猪列表项（公猪段纯列表，无图表，BRD-INVENTORY-001）。
 */
@Data
public class InventoryBoarItemVo {

    /** 猪只ID（string，全链路禁 Number） */
    private String pigId;

    /** 耳号 */
    private String earNo;

    /** 品种 code（djs_pig_breed，前端字典翻译） */
    private String breedCode;

    /** 栋舍名称 */
    private String barnName;

    /** 引种日期 yyyy-MM-dd（无则 null） */
    private String entryDate;
}
