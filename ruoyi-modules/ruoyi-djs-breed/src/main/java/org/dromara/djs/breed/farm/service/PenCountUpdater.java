package org.dromara.djs.breed.farm.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 栏位在场头数（{@code t_farm_barn_pen.current_count}）唯一维护入口（FIX-BRD-PENCOUNT-001）。
 *
 * <p><b>口径</b>：该栏位当前在场猪只数 = {@code t_farm_pig_info} 中 {@code pen_id} = 本栏 +
 * {@code del_flag='0'} + {@code current_status <> 'END'} 的行数（<b>含仔猪</b>，与
 * {@code FarmStatMapper} 栏位存栏 headCount 同口径）。容量校验是另一套口径（排除仔猪）且实时
 * COUNT，走 {@link PenCapacityChecker}，不读本列。</p>
 *
 * <p>所有改变「猪在哪个栏 / 是否还在场」的写路径都必须过本类，保证加减配对：</p>
 * <ul>
 *   <li>外部引种落栏 → {@link #increase}（{@code PigIntroServiceImpl}）</li>
 *   <li>仔猪打耳标落母猪所在栏 → {@link #increase}（{@code PigEarTagServiceImpl}）</li>
 *   <li>转群 / 断奶转栏 → {@link #move}（{@code PigCoreServiceImpl} TRANSFER 事件）</li>
 *   <li>死亡 / 淘汰 / 出栏 → {@link #decrease}（{@code PigCoreServiceImpl} 终止事件 → END）</li>
 * </ul>
 *
 * <p>全部走 wrapper-only {@code setSql} 原子加减（不 read-modify-write，避免并发丢更新）；
 * 减法带 {@code GREATEST(..., 0)} 防负。本类不开事务，跟随调用方事务一起提交 / 回滚。</p>
 *
 * @author djs
 * @since FIX-BRD-PENCOUNT-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenCountUpdater {

    private final PenMapper penMapper;

    /**
     * {@code current_count += delta}。
     *
     * @param penId 栏位 ID（null → 空操作，猪只允许只落栋舍不落栏）
     * @param delta 入栏头数（≤ 0 → 空操作）
     */
    public void increase(Long penId, int delta) {
        if (penId == null || delta <= 0) {
            return;
        }
        penMapper.update(null, Wrappers.<Pen>update()
            .eq("id", penId)
            .setSql("current_count = COALESCE(current_count, 0) + " + delta));
        log.debug("[FIX-BRD-PENCOUNT-001] pen current_count += {} penId={}", delta, penId);
    }

    /**
     * {@code current_count -= delta}，下限 0（历史脏数据偏小时不会算成负数）。
     *
     * @param penId 栏位 ID（null → 空操作）
     * @param delta 离栏头数（≤ 0 → 空操作）
     */
    public void decrease(Long penId, int delta) {
        if (penId == null || delta <= 0) {
            return;
        }
        penMapper.update(null, Wrappers.<Pen>update()
            .eq("id", penId)
            .setSql("current_count = GREATEST(COALESCE(current_count, 0) - " + delta + ", 0)"));
        log.debug("[FIX-BRD-PENCOUNT-001] pen current_count -= {} penId={}", delta, penId);
    }

    /**
     * 迁栏：旧栏 {@code -count}、新栏 {@code +count}（两条原子 UPDATE，同调用方事务）。
     *
     * <p>旧栏 / 新栏任一为 null 时只做存在那一侧（转出到「只有栋舍没有栏」= 只减旧栏）。
     * 同栏（含两侧都为 null）直接返回，避免自减自加空转。</p>
     *
     * @param fromPenId 转出栏位 ID（可 null）
     * @param toPenId   转入栏位 ID（可 null）
     * @param count     迁栏头数（≤ 0 → 空操作）
     */
    public void move(Long fromPenId, Long toPenId, int count) {
        if (count <= 0 || Objects.equals(fromPenId, toPenId)) {
            return;
        }
        decrease(fromPenId, count);
        increase(toPenId, count);
    }
}
