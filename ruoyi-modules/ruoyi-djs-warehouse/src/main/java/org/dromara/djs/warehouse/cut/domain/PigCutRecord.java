package org.dromara.djs.warehouse.cut.domain;

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
 * 猪肉分割工序记录实体（WMS-PIG-002）。
 *
 * <p>对应表 {@code t_warehouse_pig_cut_record}（V202606071100）。</p>
 *
 * <h3>3 阶段状态机</h3>
 * <ul>
 *   <li>{@code pending_pickup}（草稿，本 V1 不走，pickup 提交即写 picked）</li>
 *   <li>{@code picked}：白条领用完成 → bar_info 由 in_stock → pending_cut</li>
 *   <li>{@code cutting}：首次 cutOut 提交进入 → bar_info 由 pending_cut → cutting</li>
 *   <li>{@code done}：cutDone 提交进入 → bar_info 由 cutting → cut_done</li>
 * </ul>
 *
 * <p>跨表事务（{@link org.dromara.djs.warehouse.cut.service.impl.PigCutRecordServiceImpl}）：</p>
 * <ul>
 *   <li>pickup：cut_record INSERT + bar_info UPDATE</li>
 *   <li>cutOut（多部位）：product_inhouse N 行 INSERT + stock_flow N+1 行（N 入冻品 + 1 白条出）+ cut_record / bar_info status 推进</li>
 *   <li>cutDone：cut_record UPDATE done + bar_info UPDATE cut_done + 排酸字段计算</li>
 * </ul>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_pig_cut_record")
public class PigCutRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 业务码 {@code CUT+YYMMDD+4}（本表幂等键）。
     */
    private String cutId;

    /**
     * 被分割的白条 FK → {@code t_warehouse_bar_info.id}。
     */
    private Long whiteBarId;

    /**
     * 半只白条流水号（BizCodeType.BAR_NO；燎毛按半只生成，外购/旧数据领用时补生成）。
     * 分割按半只 surface + 剩余可分割/超量校验按 white_bar_no 聚合；空回落 white_bar_id（整猪，兼容旧记录）。
     */
    private String whiteBarNo;

    /**
     * 冗余 {@code bar_info.bar_id} 便于查询展示。
     */
    private String barId;

    /**
     * 冗余白条对应耳号（外购白条可空）。
     */
    private String earNo;

    /**
     * 白条领用时间（pickup 阶段写）。
     */
    private Date pickupTime;

    /**
     * 分割开始时间（cutOut 首次提交时写）。
     */
    private Date cutStartTime;

    /**
     * 出库完成时间（cutDone 提交时写）。
     */
    private Date cutDoneTime;

    /**
     * 领用重量 kg（= bar_info.in_weight 快照）。
     */
    private BigDecimal pickupWeight;

    /**
     * 滴水损失 kg（cutDone 提交时写）。
     */
    private BigDecimal dripLoss;

    /**
     * 排酸时长（分钟，= cut_done_time - pickup_time）。
     */
    private Integer acidRemoveMinutes;

    /**
     * mp 分割师 FK → {@code sys_user.user_id}。
     */
    private Long operatorId;

    /**
     * 分割产出入冻品库 location FK。
     */
    private Long locationId;

    /**
     * 指定目标门店（V1 可空）。
     */
    private Long targetStoreId;

    /**
     * 关联需求 FK → {@code t_warehouse_demand_manage.id}（V1 可空）。
     */
    private Long targetDemandId;

    /**
     * 是否半扇分割 1=是 / 2=否（整只）。
     */
    private Integer isHalf;

    /**
     * 状态：{@code pending_pickup / picked / cutting / done}。
     */
    private String cutStatus;

    /**
     * 出库类型（字典 {@code djs_pig_cut_out_type}）：{@code cut}=分割车间 / {@code ship}=发货月台 / {@code warehouse}=仓库出库。
     *
     * <p>本表定位为「白条领用表」：白条领用页 3 个出库位置确认出库时都写一条记录。仅 {@code cut} 后续进分割
     * （有分割品重量/损耗）；{@code ship/warehouse} 领用即终态（{@code cut_status='done'}）。分割统计
     * （分割白条数/总重/率、trace 领用时点）只统计 {@code out_type='cut'}，ship/warehouse 不计入分割口径。</p>
     */
    private String outType;

    /**
     * 出库去向码值（字典 {@code djs_stock_out_dest}：矿山/厨房/大冶门店/个人…）。
     *
     * <p>admin row2：仅 {@code out_type='warehouse'}（仓库出库）领用时记录页面所选出库去向；
     * {@code cut}/{@code ship} 无出库去向概念，保持 {@code null}。</p>
     */
    private String outDest;

    /**
     * 凭证图 OSS IDs CSV（biz_type=warehouse_pig_cut）。
     */
    private String proofOssIds;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
