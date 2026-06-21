package org.dromara.djs.breed.core.util;

import org.dromara.djs.breed.core.domain.Pig;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 猪只「操作当时日龄」计算（ADR-0017）。
 *
 * <p>母猪各类事件操作在落库时，把「该操作发生当时猪只的日龄」冻结写入事件表 {@code age_days} 列，
 * 作为不可变历史快照——日后修正 {@code birth_date} 不回溯改历史算出值。口径与各事件 VO 的实时
 * 日龄完全一致：</p>
 *
 * <pre>age_days = eventDate - (birth_date 优先，缺则 introduce_date)；两者或 eventDate 缺 → null；负值归 0</pre>
 *
 * <p>与 Flyway 历史回填 SQL 的 {@code GREATEST(DATEDIFF(eventDate, COALESCE(birth_date, introduce_date)), 0)}
 * 逐字对齐（{@code ChronoUnit.DAYS.between(start, end)} == MySQL {@code DATEDIFF(end, start)}）。</p>
 */
public final class PigAgeUtil {

    private PigAgeUtil() {
    }

    /**
     * 操作当时日龄（天）。
     *
     * @param birthDate     出生日期（优先基准）
     * @param introduceDate 引种日期（birth_date 缺时回落基准）
     * @param eventDate     事件发生日期
     * @return 日龄（≥0）；基准日或事件日缺失 → {@code null}
     */
    public static Integer ageDaysAt(LocalDate birthDate, LocalDate introduceDate, LocalDate eventDate) {
        LocalDate base = birthDate != null ? birthDate : introduceDate;
        if (base == null || eventDate == null) {
            return null;
        }
        return (int) Math.max(ChronoUnit.DAYS.between(base, eventDate), 0L);
    }

    /**
     * 操作当时日龄（天）——便捷重载：从 {@link Pig} 取 birth_date / introduce_date。
     *
     * @param pig       猪只（含 birthDate / introduceDate）；{@code null} → 返回 {@code null}
     * @param eventDate 事件发生日期
     * @return 日龄（≥0）；猪只为空或基准/事件日缺失 → {@code null}
     */
    public static Integer ageDaysAt(Pig pig, LocalDate eventDate) {
        if (pig == null) {
            return null;
        }
        return ageDaysAt(pig.getBirthDate(), pig.getIntroduceDate(), eventDate);
    }
}
