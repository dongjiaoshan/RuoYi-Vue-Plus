package org.dromara.djs.warehouse.thirdphase.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 三期作物入库记录实体（V6 row88，表 {@code t_warehouse_third_phase_in}）。
 *
 * <p>「三期」= 甲方作物的第三茬 / 第三期（不是开发三期，Kevin 2026-08-12 拍板）。</p>
 *
 * <p>本表是<b>本模块自己的操作台账</b>，不是库存真相 —— 库存真相仍在
 * {@code t_warehouse_stock_flow} + {@code t_warehouse_location_stock}
 * （与「采摘去向[毛菜保鲜室]入库」同一套写法，见
 * {@code VegetableHandleServiceImpl#insertPickStockIn}）。这里额外承载甲方要的
 * 「本次操作内容 + 所录产品对应的作物信息」快照。</p>
 *
 * <p>作物名 / 产品名 / 库位名 / 地块名一律存<b>写入当时的快照</b>：作物改名、地块调整之后，
 * 历史记录仍要显示当时录的是什么。</p>
 *
 * @author djs
 * @since V6-R88
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_third_phase_in")
public class ThirdPhaseIn extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 入库时间（列表显示到时分）。 */
    private Date inTime;

    /** 作物 FK → {@code t_plant_crop_info.id}；一个产品命中多作物时取 id 最小的一个。 */
    private Long cropId;

    /** 作物名称快照；命中多作物时逗号拼接全部。 */
    private String cropName;

    /** 入库产品 FK → {@code t_warehouse_product_info.id}。 */
    private Long productId;

    /** 产品名称快照。 */
    private String productName;

    /** 入库量（kg，3 位小数）。 */
    private BigDecimal stockNum;

    /** 入库库位 FK → {@code t_warehouse_location_info.id}（恒为毛菜鲜品库 L0006）。 */
    private Long locationId;

    /** 入库库位名称快照。 */
    private String locationName;

    /** 本次打【三期】标识的地块 id 逗号串（无在种地块时为空串）。 */
    private String plotIds;

    /** 本次打【三期】标识的地块名逗号串（无在种地块时为空串）。 */
    private String plotNames;

    /** 本次入库产生的 {@code t_warehouse_stock_flow.id}（对账用）。 */
    private Long flowId;

    /** 入库人 FK → {@code sys_user.user_id}。 */
    private Long operatorId;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列。 */
    private Long delUnique;
}
