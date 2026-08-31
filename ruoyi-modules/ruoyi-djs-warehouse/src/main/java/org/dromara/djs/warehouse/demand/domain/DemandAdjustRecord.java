package org.dromara.djs.warehouse.demand.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求量调整留痕实体（V6-R140）。
 *
 * <p>「需求调整管理」每调一次需求量写一行，只增不改。甲方点名要留的 8 项都在：
 * 需求日期 / 需求门店 / 需求产品编码 / 原始需求量 / 调整后需求量 / 备注 / 调整人 / 调整时间。</p>
 *
 * <p>门店名 / 产品名 / 产品编码存的是调整<b>当时</b>的快照而不是靠 FK 现查 —— 留痕表要回答的是
 * 「当时改了什么」，门店改名或产品下架之后回看不能跟着变。FK 仍保留，供下钻回原需求单。</p>
 *
 * @author djs
 * @since V6-R140
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_demand_adjust_record")
public class DemandAdjustRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK {@code t_warehouse_demand_manage.id}。 */
    private Long demandId;

    /** 需求单号（快照）。 */
    private String demandNo;

    /** 需求日期（快照）。 */
    private LocalDate demandDate;

    /** 需求门店 FK {@code t_md_store.id}。 */
    private Long storeId;

    /** 需求门店名称（快照）。 */
    private String storeName;

    /** 需求产品 FK {@code t_warehouse_product_info.id}。 */
    private Long productId;

    /** 需求产品编码（快照，= {@code t_warehouse_product_info.product_id} 业务码）。 */
    private String productCode;

    /** 需求产品名称（快照）。 */
    private String productName;

    /** 原始需求量。 */
    private BigDecimal oldQuantity;

    /** 调整后需求量。 */
    private BigDecimal newQuantity;

    /** 调整备注。 */
    private String adjustRemark;

    /** 调整人 FK {@code sys_user.user_id}。 */
    private Long adjusterId;

    /** 调整时间。 */
    private LocalDateTime adjustTime;

    @TableLogic
    private String delFlag;
}
