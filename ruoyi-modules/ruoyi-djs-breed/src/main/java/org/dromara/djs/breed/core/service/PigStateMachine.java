package org.dromara.djs.breed.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import static org.dromara.djs.breed.core.enums.PigLifecycle.DN;
import static org.dromara.djs.breed.core.enums.PigLifecycle.END;
import static org.dromara.djs.breed.core.enums.PigLifecycle.FM;
import static org.dromara.djs.breed.core.enums.PigLifecycle.FQ;
import static org.dromara.djs.breed.core.enums.PigLifecycle.HB;
import static org.dromara.djs.breed.core.enums.PigLifecycle.KH;
import static org.dromara.djs.breed.core.enums.PigLifecycle.LC;
import static org.dromara.djs.breed.core.enums.PigLifecycle.PH;
import static org.dromara.djs.breed.core.enums.PigLifecycle.PZ;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.BREED;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.CASTRATE;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.DIE;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.ELIMINATE;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.FARROW;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.INTRO;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.NULL_RETURN;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.OESTRUS;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.SLAUGHTER;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.TRANSFER;
import static org.dromara.djs.breed.core.enums.PigStatusEvent.WEAN;

/**
 * 猪只状态机（BRD-CORE-001 ★ 业务心脏）。
 *
 * <p>手写 FSM，不引 spring-statemachine / Flowable。{@link #nextStatus} 为纯函数，
 * 不访问 DB，方便单测覆盖 11×10 transition 矩阵。</p>
 *
 * <p><b>三类 transition</b>：</p>
 * <ul>
 *   <li>简单 {@code (from, event) → to}：BREED / FARROW / WEAN — 查 {@link #SIMPLE} 表；</li>
 *   <li>复杂按 payload 分流：OESTRUS（{@code isPregnantConfirmed}）/ NULL_RETURN（{@code abnormalType}）— 走 {@link #COMPLEX} 函数；</li>
 *   <li>特殊：</li>
 *   <li>&nbsp;&nbsp;- INTRO：不走本类，由 {@code PigCoreService#createPig} 创建态处理；</li>
 *   <li>&nbsp;&nbsp;- 终态 events（DIE/ELIMINATE/SLAUGHTER）→ END；</li>
 *   <li>&nbsp;&nbsp;- 状态不变 events（CASTRATE/TRANSFER）→ from。</li>
 * </ul>
 *
 * <p>错误信息走 i18n，key 在 {@code ruoyi-admin/.../i18n/messages_zh_CN.properties} +
 * {@code messages_en_US.properties}。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Component
public class PigStateMachine {

    /** 简单 transition: (from, event) → to。 */
    private static final Map<TransitionKey, PigLifecycle> SIMPLE = buildSimpleMap();

    /** 复杂 transition（按 payload 分流）。 */
    private static final Map<PigStatusEvent, BiFunction<PigLifecycle, Map<String, Object>, PigLifecycle>> COMPLEX = buildComplexMap();

    /** 仅母猪允许的事件（公猪触发抛 female_only）。 */
    private static final Set<PigStatusEvent> FEMALE_ONLY = Set.of(BREED, OESTRUS, FARROW, WEAN, NULL_RETURN);

    /** 终态聚合事件（任一非 END from → END，end_reason 在 service 内按事件填）。 */
    private static final Set<PigStatusEvent> TERMINAL_EVENTS = Set.of(DIE, ELIMINATE, SLAUGHTER);

    /** 状态不变事件（仅写 status_record + 业务副作用，不改 currentStatus）。 */
    private static final Set<PigStatusEvent> NO_CHANGE_EVENTS = Set.of(CASTRATE, TRANSFER);

    /**
     * 计算事件触发后的目标状态（纯函数，不访问 DB）。
     *
     * @param from    当前状态（不可为 null）
     * @param event   触发事件（不可为 null；INTRO 在此抛异常 — 走 createPig 路径）
     * @param sex     猪只性别 F / M（用于 gender 校验）
     * @param payload 事件 payload（OESTRUS / NULL_RETURN 等使用；其他事件可空）
     * @return 目标 lifecycle
     * @throws ServiceException 包含 i18n key：终态 / 性别不符 / 非法 transition 等
     */
    public PigLifecycle nextStatus(PigLifecycle from, PigStatusEvent event, String sex, Map<String, Object> payload) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(event, "event must not be null");

        // 1. INTRO 不走 fireEvent，由 createPig 路径直接创建初始状态
        if (event == INTRO) {
            throw new ServiceException(I18nMessages.t("pig.event.intro_use_create"), 400);
        }

        // 2. 终态拒绝继续推进
        if (from.isTerminal()) {
            throw new ServiceException(I18nMessages.t("pig.state.terminal", from.getLabel()), 400);
        }

        // 3. 性别校验
        validateGender(event, sex);

        // 4. 终态事件：DIE / ELIMINATE / SLAUGHTER → END
        if (TERMINAL_EVENTS.contains(event)) {
            return END;
        }

        // 5. 状态不变事件
        if (NO_CHANGE_EVENTS.contains(event)) {
            return from;
        }

        // 6. 复杂 transition（OESTRUS / NULL_RETURN）
        BiFunction<PigLifecycle, Map<String, Object>, PigLifecycle> fn = COMPLEX.get(event);
        if (fn != null) {
            return fn.apply(from, payload != null ? payload : Map.of());
        }

        // 7. 简单 transition
        PigLifecycle to = SIMPLE.get(new TransitionKey(from, event));
        if (to == null) {
            throw new ServiceException(I18nMessages.t("pig.event.invalid_transition", from.getLabel(), event.getLabel()), 400);
        }
        return to;
    }

    private void validateGender(PigStatusEvent event, String sex) {
        if (FEMALE_ONLY.contains(event) && !"F".equals(sex)) {
            throw new ServiceException(I18nMessages.t("pig.event.female_only", event.getLabel()), 400);
        }
        if (event == CASTRATE && !"M".equals(sex)) {
            throw new ServiceException(I18nMessages.t("pig.event.male_only", event.getLabel()), 400);
        }
    }

    private static Map<TransitionKey, PigLifecycle> buildSimpleMap() {
        Map<TransitionKey, PigLifecycle> m = new HashMap<>();
        // BREED: HB / DN / LC / KH / FQ → PZ
        m.put(new TransitionKey(HB, BREED), PZ);
        m.put(new TransitionKey(DN, BREED), PZ);
        m.put(new TransitionKey(LC, BREED), PZ);
        m.put(new TransitionKey(KH, BREED), PZ);
        m.put(new TransitionKey(FQ, BREED), PZ);
        // FARROW: PH → FM
        m.put(new TransitionKey(PH, FARROW), FM);
        // WEAN: FM → DN
        m.put(new TransitionKey(FM, WEAN), DN);
        return Map.copyOf(m);
    }

    private static Map<PigStatusEvent, BiFunction<PigLifecycle, Map<String, Object>, PigLifecycle>> buildComplexMap() {
        Map<PigStatusEvent, BiFunction<PigLifecycle, Map<String, Object>, PigLifecycle>> m = new HashMap<>();

        // OESTRUS（查情）：仅 PZ；payload.isPregnantConfirmed=true → PH，否则保持 PZ（仅记录状态）
        m.put(OESTRUS, (from, payload) -> {
            if (from != PZ) {
                throw new ServiceException(I18nMessages.t("pig.event.invalid_transition", from.getLabel(), OESTRUS.getLabel()), 400);
            }
            return Boolean.TRUE.equals(payload.get("isPregnantConfirmed")) ? PH : PZ;
        });

        // NULL_RETURN（返空）：PZ/PH；按 abnormalType 分流
        m.put(NULL_RETURN, (from, payload) -> {
            if (from != PZ && from != PH) {
                throw new ServiceException(I18nMessages.t("pig.event.invalid_transition", from.getLabel(), NULL_RETURN.getLabel()), 400);
            }
            Object t = payload.get("abnormalType");
            String type = t == null ? null : t.toString();
            if (type == null) {
                throw new ServiceException(I18nMessages.t("pig.event.invalid_abnormal_type", "null"), 400);
            }
            return switch (type) {
                case "abort" -> LC;
                case "return" -> FQ;
                case "idle" -> KH;
                default -> throw new ServiceException(I18nMessages.t("pig.event.invalid_abnormal_type", type), 400);
            };
        });

        return Map.copyOf(m);
    }

    /** transition 查表 key（(from, event) 二元组）。 */
    public record TransitionKey(PigLifecycle from, PigStatusEvent event) {
    }
}
