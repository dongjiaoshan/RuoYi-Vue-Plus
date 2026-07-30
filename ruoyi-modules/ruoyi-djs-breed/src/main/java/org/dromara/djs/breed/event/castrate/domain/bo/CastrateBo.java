package org.dromara.djs.breed.event.castrate.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阉割事件录入入参（BRD-EVENT-004 CASTRATE）。
 *
 * <p>service 层提前校验 pig_sex='M'（早于状态机），抛 castrate.male_only。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class CastrateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 ID（与 earNo 二选一；admin 端可直传 id，mp 端建议传 earNo）。 */
    private Long pigId;

    /** 猪只耳号简版（mp 端工人输入；与 pigId 二选一，service 入口按 earNo 查 pig.id）。 */
    private String earNo;

    @NotNull(message = "{castrate.date.required}")
    private LocalDateTime castrateDate;

    /**
     * 阉割人员（原型 87「阉割人员」字段，可选）。
     *
     * <p>V1 无员工 picker applet 端点，mp 端退化为手填人员名/编码字符串；落库到
     * {@code t_farm_castrate_record.castrater}，便于 V1.x 接员工下拉后回填。
     * 与 {@code operatorId}（系统登录态操作人）并存：operatorId 是录入账号，
     * castrater 是实际执刀人员（现场可不同人）。</p>
     */
    @Size(max = 64, message = "{castrate.castrater.size}")
    private String castrater;

    @Size(max = 500, message = "{castrate.remark.size}")
    private String remark;
}
