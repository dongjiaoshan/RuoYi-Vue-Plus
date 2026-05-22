package org.dromara.djs.breed.event.eartag.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 分娩记录只读视图（BRD-EVENT-003 临时依赖）。
 *
 * <p>对应表 {@code t_farm_pig_farrow}（DDL 在 SYS-INIT-001 L329-362）。
 * BRD-EVENT-002（D6）会正式实现该表的 Controller/Service/Entity；
 * 本 ticket 仅需要读 {@code pig_id / ear_no / breeding_id / farrow_date / live_born / parity}
 * 5 个字段做"已贴 ≤ live_born"校验 + 找母猪、找父猪耳号。</p>
 *
 * <p>BRD-EVENT-002 落地后本类应被替换为该 ticket 提供的 entity；届时本文件可删，
 * mapper 与 service 改 import 即可（字段名同 DDL 不变）。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
@TableName("t_farm_pig_farrow")
public class FarrowRef extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 母猪 pig_info.id。 */
    private Long pigId;

    /** 母猪耳号。 */
    private String earNo;

    /** 关联配种记录 ID。 */
    private Long breedingId;

    /** 分娩日期。 */
    private LocalDateTime farrowDate;

    /** 活产仔数（贴耳标上限）。 */
    private Integer liveBorn;

    /** 本次胎次。 */
    private Integer parity;
}
