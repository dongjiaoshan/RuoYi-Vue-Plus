package org.dromara.djs.warehouse.burn.domain;

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
 * 燎毛工序记录实体（WMS-PIG-001）。
 *
 * <p>对应表 {@code t_warehouse_pig_burn_record}（V202606040900）：</p>
 * <ul>
 *   <li>{@code burnId}：业务码 {@code BURN+YYMMDD+4 位序号}，本表幂等键，UNIQUE (tenant_id, burn_id, del_unique)</li>
 *   <li>{@code earNo}：猪只耳号，关联 {@code t_farm_pig_info.ear_no}（current_status='END' 已出栏才允许燎毛）</li>
 *   <li>{@code burnStatus}：字典 {@code djs_burn_status}（{@code pending} 草稿 / {@code done} 已完成扣库存）；
 *       本 ticket V1 只走 done（提交即扣库存），保留 pending 给后续草稿态扩展</li>
 *   <li>{@code lossWeight}：损耗重量 = {@code arriveWeight - burnWeight}，service 校验 ≥ 0</li>
 *   <li>{@code operatorId}：mp 端提交人（{@link org.dromara.common.satoken.utils.LoginHelper#getUserId()} 注入，ADR-0007）</li>
 *   <li>{@code locationId}：白条入库的库位 FK → {@code t_warehouse_location_info.id}（done 必填）</li>
 *   <li>{@code proofOssIds}：燎毛现场凭证图 OSS IDs，CSV，bizType={@code warehouse_pig_burn}</li>
 * </ul>
 *
 * <p>不动 {@code t_warehouse_bar_info}（D10 CROSS-FLOW-001 自动创建 + D9 WMS-PIG-002 状态推进）。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_pig_burn_record")
public class PigBurnRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId
    private Long id;

    /**
     * 业务码 {@code BURN+YYMMDD+4}（本表幂等键）。
     */
    private String burnId;

    /**
     * 猪只耳号（关联 {@code t_farm_pig_info.ear_no}）。
     */
    private String earNo;

    /**
     * 燎毛时间（工序时间）。
     */
    private Date burnTime;

    /**
     * 到场重量 kg（燎毛前过磅）。
     */
    private BigDecimal arriveWeight;

    /**
     * 燎毛后重量 kg（白条入库重量）。
     */
    private BigDecimal burnWeight;

    /**
     * 损耗重量 kg（{@code arriveWeight - burnWeight}）。
     */
    private BigDecimal lossWeight;

    /**
     * 燎毛状态（字典 {@code djs_burn_status}）。
     */
    private String burnStatus;

    /**
     * 操作人（FK → {@code sys_user.user_id}）。
     */
    private Long operatorId;

    /**
     * 入白条库的库位 FK → {@code t_warehouse_location_info.id}。
     */
    private Long locationId;

    /**
     * 凭证图 OSS IDs CSV。
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
