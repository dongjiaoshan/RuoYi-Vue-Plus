package org.dromara.djs.breed.event.castrate.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;
import org.dromara.djs.breed.event.castrate.mapper.CastrateRecordMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 单头自动阉割的事务执行单元（admin row186）。
 *
 * <p><b>为什么单独成 bean</b>：批处理入口 {@code CastrateServiceImpl.autoCastrateOverAgePiglets}
 * 要逐头开独立事务（单头失败不牵连整批），但同类内部方法调用不过 Spring AOP 代理，
 * 写在同一个类里的 {@code @Transactional} 是死代码 —— claim 成功后若写记录/发事件失败将不回滚，
 * 猪会卡成「is_castrated=2 但没有任何阉割记录与事件」的幽灵态，且因扫描条件是
 * {@code is_castrated=1} 而永远不会被后续跑批捡回。抽成协作 bean 后调用真正过代理，事务才生效。</p>
 *
 * @author djs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCastrateExecutor {

    /** 自动阉割的阉割人落款（无登录上下文，与人工录入区分）。 */
    static final String AUTO_CASTRATER = "系统自动";

    private final CastrateRecordMapper castrateMapper;
    private final PigMapper pigMapper;
    private final org.dromara.djs.breed.core.service.IPigCoreService pigCoreService;

    /**
     * 单头自动阉割：抢占 {@code is_castrated 1→2} → 写阉割记录 → 发 CASTRATE 事件，三写同一事务。
     *
     * <p>先用带 {@code is_castrated=1} 条件的 UPDATE「抢占」再写台账：批量 SELECT 到逐头处理之间存在
     * 分钟级时间窗，期间人工可能已把同一头猪阉了。只有抢到（影响行数=1）的才继续写，
     * 否则跳过，避免一头猪两条阉割记录 / 两条 CASTRATE 事件（{@code t_farm_castrate_record} 无唯一约束）。</p>
     *
     * @param pig       目标猪（来自批量扫描快照）
     * @param baseDate  阉割日期（= 跑批基准日）
     * @param ageDays   日龄阈值（写进备注）
     * @return 是否真正处理了该头（false = 已被他处阉割，跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean castrateOne(Pig pig, LocalDate baseDate, int ageDays) {
        // wrapper-only update：不走实体乐观锁（同人工 recordCastrate）；条件 is_castrated=1 兼作并发抢占
        int claimed = pigMapper.update(null, Wrappers.<Pig>lambdaUpdate()
            .set(Pig::getIsCastrated, 2)
            .eq(Pig::getId, pig.getId())
            .eq(Pig::getIsCastrated, 1));
        if (claimed == 0) {
            log.info("[BRD-AUTO-CASTRATE] pigId={} 已被他处阉割，跳过", pig.getId());
            return false;
        }

        CastrateRecord entity = new CastrateRecord();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setCastrateDate(baseDate.atStartOfDay());
        entity.setCastrater(AUTO_CASTRATER);
        entity.setRemark("超过 " + ageDays + " 日龄自动阉割");
        // 定时线程无登录上下文，操作人留空（与 castrater='系统自动' 一起标识非人工录入）
        entity.setOperatorId(null);
        entity.setDelFlag("0");
        castrateMapper.insert(entity);

        PigEventBo eventBo = new PigEventBo();
        eventBo.setPigId(pig.getId());
        eventBo.setEventType(PigStatusEvent.CASTRATE);
        eventBo.setRelatedEventId(entity.getId());
        eventBo.setEventAt(baseDate.atStartOfDay());
        pigCoreService.fireEvent(eventBo);
        return true;
    }
}
