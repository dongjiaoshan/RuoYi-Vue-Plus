package org.dromara.djs.warehouse.veg.domain;

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
 * 毛菜处理流水实体（WMS-VEG-001）。
 *
 * <p>对应表 {@code t_warehouse_handle_record}（V202606071200）。一次采摘 → 多次处理录入，
 * record_type 区分：1=采收（首次过磅）/ 2=处理（清洗后流向）。</p>
 *
 * <p>record_type=2 时 handle_target 必填（1=入库 / 2=月台 / 3=饲料），handle_target=1 同时必填 location_id。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_handle_record")
public class HandleRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 汇总 FK → {@code t_warehouse_vegetable_handle.id}。
     */
    private Long handleId;

    /**
     * 冗余 plot_id。
     */
    private Long plotId;

    /**
     * 冗余 crop_id。
     */
    private Long cropId;

    /**
     * 采摘班组 FK → {@code t_plant_work_team.id}（record_type=1 采收录入必填；record_type=2 处理录入为 null）。
     */
    private Long teamId;

    /**
     * 记录类型 djs_record_type：1=采收 / 2=处理。
     */
    private Integer recordType;

    /**
     * 本次重量(kg)。
     */
    private BigDecimal recordWeight;

    /**
     * 是否完成称重 1=是 / 2=否。
     */
    private Integer isWeighed;

    /**
     * 是否完成 1=是 / 2=否。
     */
    private Integer isFinish;

    /**
     * 处理目标 djs_handle_target：1=入库 / 2=月台 / 3=饲料（record_type=2 必填）。
     */
    private Integer handleTarget;

    /**
     * 入库库位 FK → {@code t_warehouse_location_info.id}（handle_target=1 必填）。
     */
    private Long locationId;

    /**
     * 处理人 FK → {@code sys_user.user_id}。
     */
    private Long handleUser;

    /**
     * 处理时间。
     */
    private Date handleTime;

    /**
     * 凭证图 OSS IDs CSV（biz_type=warehouse_veg_handle）。
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
