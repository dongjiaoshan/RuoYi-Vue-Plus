package org.dromara.djs.breed.core.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.dromara.djs.breed.core.enums.PigStatusEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 状态机统一事件入参（BRD-CORE-001 ★）。
 *
 * <p>所有 11 UI events（INTRO 除外，走 createPig 路径）都通过此 BO 进入
 * {@code PigCoreService#fireEvent}。子 ticket（BRD-EVENT-001~004）的调用模式：</p>
 *
 * <pre>{@code
 * @Transactional
 * public void recordBreeding(BreedingBo bo) {
 *     Long breedingId = breedingMapper.insert(bo.toEntity()).getId();
 *     pigCoreService.fireEvent(new PigEventBo()
 *         .setPigId(bo.getPigId())
 *         .setEventType(PigStatusEvent.BREED)
 *         .setRelatedEventId(breedingId)
 *         .setEventAt(bo.getBreedingDate().atStartOfDay()));
 * }
 * }</pre>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
public class PigEventBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只 ID（必填）。 */
    @NotNull(message = "{pig.event.pig_id_required}")
    private Long pigId;

    /** UI 事件类型（必填）。 */
    @NotNull(message = "{pig.event.type_required}")
    private PigStatusEvent eventType;

    /** 事件发生时间；null 时由 service 取 now()。 */
    private LocalDateTime eventAt;

    /**
     * 关联业务事件表 PK（如 t_farm_pig_breeding.id）。
     * 子 ticket 先 INSERT 自身业务表拿 ID，再调本入口。
     */
    private Long relatedEventId;

    /**
     * 事件 payload（按 eventType 取不同 key）：
     * <ul>
     *   <li>OESTRUS：{@code isPregnantConfirmed: Boolean}</li>
     *   <li>NULL_RETURN：{@code abnormalType: "abort"|"return"|"idle"}</li>
     *   <li>TRANSFER：{@code newBarnId: Long, newPenId: Long}</li>
     * </ul>
     */
    private Map<String, Object> payload;

    /** 备注（可选，目前未持久化，预留扩展位）。 */
    private String remark;
}
