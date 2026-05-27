package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.core.service.DemandStateMachine;
import org.dromara.djs.warehouse.demand.core.service.I18nMessages;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.DemandPig;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 需求状态推进服务实现（WMS-DEMAND-001）。
 *
 * <p>分布式锁防并发：{@code djs:demand:transition:<id>} 同一 demand 串行化（参 BRD-CORE-001
 * PigStatusServiceImpl 范式）。</p>
 *
 * <p>audit_history 写入：每次成功 transition 后 append 1 条 JSON 节点；解析失败时初始化为空数组
 * （兼容历史数据）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Slf4j
@Service
public class DemandStatusServiceImpl implements IDemandStatusService {

    private static final long LOCK_WAIT_SECONDS = 3L;

    private static final long LOCK_LEASE_SECONDS = 30L;

    private final DemandStateMachine stateMachine;

    private final DemandManageMapper demandMapper;

    private final DemandPigMapper demandPigMapper;

    private final RedissonClient redissonClient;

    public DemandStatusServiceImpl(DemandStateMachine stateMachine,
                                   DemandManageMapper demandMapper,
                                   DemandPigMapper demandPigMapper,
                                   RedissonClient redissonClient) {
        this.stateMachine = stateMachine;
        this.demandMapper = demandMapper;
        this.demandPigMapper = demandPigMapper;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long demandId, DemandEvent event, Long operator, String remark) {
        if (demandId == null) {
            throw new ServiceException(I18nMessages.t("demand.id.required"));
        }
        if (event == null) {
            throw new ServiceException(I18nMessages.t("demand.event.required"));
        }
        Long actualOperator = resolveOperator(operator);

        String lockKey = "djs:demand:transition:" + demandId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException(I18nMessages.t("demand.state.lock_timeout"), 409);
            }
            doTransition(demandId, event, actualOperator, remark);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(I18nMessages.t("demand.state.lock_interrupted"), 500);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doTransition(Long demandId, DemandEvent event, Long operator, String remark) {
        DemandManage demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", demandId), 404);
        }
        DemandStatus from = DemandStatus.fromCodeSafe(demand.getDemandStatus());
        if (from == null) {
            throw new ServiceException(
                I18nMessages.t("demand.state.invalid", demand.getDemandStatus(), demandId), 500);
        }
        DemandStatus to = stateMachine.nextStatus(from, event);

        // 业务前置校验：白条业态 CONFIRM 前必须已指定猪只
        if (event == DemandEvent.CONFIRM && "white_bar".equals(demand.getProductType())) {
            long pigCount = demandPigMapper.selectCount(
                new LambdaQueryWrapper<DemandPig>().eq(DemandPig::getDemandId, demandId));
            if (pigCount == 0) {
                throw new ServiceException(I18nMessages.t("demand.white_bar.no_pig_assigned"), 400);
            }
        }

        // 更新状态字段
        demand.setDemandStatus(to.name());
        if (event == DemandEvent.CONFIRM) {
            demand.setDemandConfirmer(operator);
            demand.setConfirmerTime(LocalDateTime.now());
        }
        // append audit_history
        demand.setAuditHistory(appendAuditHistory(demand.getAuditHistory(), from, to, event, operator, remark));

        int rows = demandMapper.updateById(demand);
        if (rows == 0) {
            throw new ServiceException(I18nMessages.t("demand.update_failed", demandId), 500);
        }

        log.info("[WMS-DEMAND-001] transition demandId={} from={} → to={} event={} operator={}",
            demandId, from, to, event, operator);
    }

    /**
     * 在原 audit_history JSON 串后 append 1 条新转移记录，返回新 JSON 串。
     *
     * <p>原串 null / 空 / 非法 → 初始为空数组。</p>
     */
    private String appendAuditHistory(String original, DemandStatus from, DemandStatus to,
                                      DemandEvent event, Long operator, String remark) {
        List<Map<String, Object>> history = parseHistory(original);
        Map<String, Object> entry = new HashMap<>();
        entry.put("from", from.name());
        entry.put("to", to.name());
        entry.put("event", event.name());
        entry.put("operator", operator);
        // operatorName 由 admin 端解析 audit_history 时通过 @Translation USER_ID_TO_NAME 反查，
        // 后端不写死姓名，避免改名后历史记录漂移 + 跨模块依赖 ruoyi-system Mapper。
        entry.put("ts", LocalDateTime.now().toString());
        entry.put("remark", Optional.ofNullable(remark).orElse(""));
        history.add(entry);
        return JsonUtils.toJsonString(history);
    }

    /**
     * 解析原始 audit_history 串；null / 空 / 解析失败均返空 list。
     */
    private List<Map<String, Object>> parseHistory(String original) {
        if (original == null || original.isBlank()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper om = JsonUtils.getObjectMapper();
            List<Map<String, Object>> parsed = om.readValue(
                original, new TypeReference<List<Map<String, Object>>>() {
                });
            return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
        } catch (Exception e) {
            log.warn("[WMS-DEMAND-001] audit_history parse failed, fallback to empty list. raw={}", original, e);
            return new ArrayList<>();
        }
    }

    /**
     * operator 入参缺省 → 从 sa-token 取当前登录用户；都拿不到时报错（避免 0L 兜底导致审计断链）。
     */
    private Long resolveOperator(Long operator) {
        if (operator != null) {
            return operator;
        }
        try {
            Long id = LoginHelper.getUserId();
            if (id != null) {
                return id;
            }
        } catch (Exception ignored) {
            // 无登录上下文（如系统任务）
        }
        throw new ServiceException(I18nMessages.t("demand.operator.required"));
    }
}
