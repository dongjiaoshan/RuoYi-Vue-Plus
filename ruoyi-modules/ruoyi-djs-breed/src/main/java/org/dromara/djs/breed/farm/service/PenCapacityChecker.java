package org.dromara.djs.breed.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.farm.mapper.PigReferenceCheckMapper;
import org.springframework.stereotype.Component;

/**
 * 栏位容量校验/计算可复用 helper（row59）。
 *
 * <p>栏位表 {@code t_farm_barn_pen} 无独立 capacity 配置列——容量按 {@code pen_type} 推导：</p>
 * <ul>
 *   <li>{@code farrow}（产床）= 1（单头母猪 + 仔猪）</li>
 *   <li>{@code stall}（限位栏）= 1（单头母猪）</li>
 *   <li>{@code big}（大栏）= 100</li>
 *   <li>{@code scatter}（散栏）= 100</li>
 *   <li>{@code nursery_pen}（保育栏）= 100</li>
 * </ul>
 *
 * <p>占用头数（{@code usedCount}）= 该栏当前在栏 + 非终止态（{@code current_status <> 'END'}）+
 * 排除仔猪（{@code pig_type='piglet'}）的猪只数。<b>仔猪入栏不计容量、不校验</b>。</p>
 *
 * <p>被「给猪分配栏位」的落栏写入路径调用做超容校验：转移（TransferServiceImpl）、外部引种落栏
 * （PigIntroServiceImpl.introduce/introduceBatch）；内部引种走 transfer 复用同一校验。</p>
 *
 * @author djs
 * @since row59
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenCapacityChecker {

    /** 单头栏（产床 / 限位栏）容量。 */
    private static final int CAPACITY_SINGLE = 1;

    /** 群养栏（大栏 / 散栏 / 保育栏）容量。 */
    private static final int CAPACITY_GROUP = 100;

    private final PenMapper penMapper;
    private final PigReferenceCheckMapper pigReferenceCheckMapper;

    /**
     * 由栏位类型推导容量。未知 / 空类型按群养兜底（避免误拦合法落栏）。
     *
     * @param penType 栏位类型（big / stall / farrow / scatter / nursery_pen）
     * @return 推导容量（头位数）
     */
    public int capacityOf(String penType) {
        if (StringUtils.isBlank(penType)) {
            return CAPACITY_GROUP;
        }
        return switch (penType) {
            case "farrow", "stall" -> CAPACITY_SINGLE;
            case "big", "scatter", "nursery_pen" -> CAPACITY_GROUP;
            default -> CAPACITY_GROUP;
        };
    }

    /**
     * 当前在栏头数（容量口径）：未删 + 非终止态 + 排除仔猪。penId 为 null 返 0。
     *
     * @param penId 栏位 ID
     * @return 计入容量的在栏头数
     */
    public int usedCount(Long penId) {
        if (penId == null) {
            return 0;
        }
        return (int) pigReferenceCheckMapper.countCapacityUsedByPenId(penId);
    }

    /**
     * 落栏前超容校验：该栏当前 usedCount + 本次入栏头数（已排除仔猪）> capacity → 抛业务异常「容量已超限」。
     *
     * <p>penId 为 null（不指定栏位，仅落栋舍）→ 不校验直接放行。{@code incomingCount} ≤ 0 → 不校验。</p>
     *
     * @param penId         目标栏位 ID（null 跳过）
     * @param incomingCount 本次入栏头数（不含仔猪）
     */
    public void checkCapacity(Long penId, int incomingCount) {
        if (penId == null || incomingCount <= 0) {
            return;
        }
        Pen pen = penMapper.selectById(penId);
        if (pen == null) {
            // 栏位不存在交由调用方既有校验报错，这里不重复拦
            return;
        }
        int capacity = capacityOf(pen.getPenType());
        int used = usedCount(penId);
        if (used + incomingCount > capacity) {
            log.warn("[row59] pen capacity exceeded penId={} penType={} capacity={} used={} incoming={}",
                penId, pen.getPenType(), capacity, used, incomingCount);
            throw new ServiceException("容量已超限");
        }
    }
}
