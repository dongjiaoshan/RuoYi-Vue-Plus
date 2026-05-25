package org.dromara.djs.breed.event.slaughter.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 猪只出栏记录实体（BRD-EVENT-004 SLAUGHTER）。
 *
 * <p>对应表 {@code t_farm_pig_marketing}（SYS-INIT-001 DDL #21）。
 * 由 {@code SlaughterServiceImpl} 在出栏事件录入时 INSERT；触发状态机
 * {@code (非 END) → END} 写 end_reason=MARKET 并 publish {@code PigMarketingEvent}
 * 给下游燎毛接收（CROSS-FLOW-001 V1 消费者未实现）。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_marketing")
public class PigMarketing extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;

    private String earNo;

    /** 出栏日期。 */
    private LocalDateTime marketingDate;

    /** 出栏重量 kg。 */
    private BigDecimal outWeight;

    /** 出栏去向（字典 out_house_dest：送宰/外销/...）。 */
    private String outDest;

    /** 目标门店 ID。 */
    private Long storeId;

    /** 燎毛间是否已接收（CROSS-FLOW-001 消费者写）。 */
    private Integer isRoom;

    /** 多角度照片 OSS IDs（强制至少 1 张）。 */
    private String ossIds;

    private Long operatorId;

    @TableLogic
    private String delFlag;

    private String remark;
}
