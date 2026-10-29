package org.dromara.djs.warehouse.inout.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.constant.StockFlowTypes;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutFinishBo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockRow;
import org.dromara.djs.warehouse.inout.mapper.VegInoutWorkbenchMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 果蔬出库完成（V6 row293 / D-0144）：所选库存卡剩余量结转毛菜处理损耗。
 * 保留库存篮行；不改采摘、毛菜处理状态及其汇总字段。扣库与损耗明细同事务。
 *
 * <p>记账方式与小程序毛菜「处理完成」结转损耗（{@code VegetableHandleServiceImpl#settleRemainAsLoss}）一致：
 * 扣篮 + 写 {@code settle_loss} 出库流水 + 写统一损耗台账 {@code loss_flow(veg_handle_loss)}（关联该流水）。
 * 流水类型用 {@code settle_loss} 而不是 {@code loss}：「今日领用剩余」只认 {@code loss / pick_loss}，
 * 结转的是没被领走的在库余量，不能扣进同篮当天的领用剩余；而库存日 / 月汇总按库位回放流水，
 * 不写这条流水期末就会比库存篮多出这批货（见 {@link StockFlowTypes}）。</p>
 */
@Service
@RequiredArgsConstructor
public class VegOutFinishService {
    private final VegInoutWorkbenchMapper workbenchMapper;
    private final LocationStockMapper stockMapper;
    private final ILossFlowService lossService;
    private final IStockCheckService checkService;
    private final StockFlowMapper stockFlowMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    /**
     * @return 每个库存篮对应的损耗明细 id
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> finish(VegOutFinishBo bo) {
        if (bo.getProductId() == null || bo.getStockIds() == null || bo.getStockIds().isEmpty()
            || bo.getStockIds().stream().anyMatch(Objects::isNull)) {
            throw new ServiceException("请选择果蔬产品和出库地块");
        }
        Set<Long> requested = new LinkedHashSet<>(bo.getStockIds());
        if (requested.size() != bo.getStockIds().size()) {
            throw new ServiceException("库存篮不能重复，请刷新后重试");
        }
        // 正库存锁定读：等待其他扣库事务提交后重新读取余量；新篮/消失篮不默默漏结转。
        List<VegOutStockRow> all = workbenchMapper.selectVegStocksForUpdate(bo.getProductId());
        List<VegOutStockRow> selected = all.stream().filter(r -> requested.contains(r.getStockId())).toList();
        if (selected.size() != requested.size()) {
            throw new ServiceException("所选库存已变化或出库完成，请刷新后重试");
        }
        String key = groupKey(selected.getFirst());
        if (selected.stream().anyMatch(r -> !key.equals(groupKey(r)))) {
            throw new ServiceException("只能完成同一产品、库位、地块的库存卡");
        }
        Set<Long> currentGroup = all.stream().filter(r -> key.equals(groupKey(r)))
            .map(VegOutStockRow::getStockId).collect(Collectors.toSet());
        if (!currentGroup.equals(requested)) {
            throw new ServiceException("该地块库存篮已变化，请刷新后重新选择完整库存卡");
        }
        checkService.assertLocationUnlocked(selected.getFirst().getLocationId());
        Long userId = LoginHelper.getUserId();
        Date now = new Date();
        List<Long> lossIds = new ArrayList<>();
        for (VegOutStockRow row : selected) {
            if (stockMapper.deductStockById(row.getStockId(), row.getStockWeight(), userId) != 1) {
                throw new ServiceException("库存已变化，本次出库完成全部撤销，请刷新后重试");
            }
            StockFlow flow = new StockFlow();
            flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, java.util.Map.of("ioCode", "OT")));
            flow.setFlowDate(now);
            flow.setProductId(row.getProductId());
            flow.setWarehouseId(row.getLocationId());
            flow.setInoutType("OT");
            flow.setFlowType(StockFlowTypes.SETTLE_LOSS);
            flow.setChangeNum(row.getStockWeight().negate());
            flow.setChangeQuantity(row.getStockWeight());
            flow.setPlotId(row.getPlotId());
            flow.setEarNo(row.getEarNo());
            flow.setThirdPhase(row.getThirdPhase() != null && row.getThirdPhase() == 1 ? 1 : 0);
            flow.setOperatorId(userId);
            flow.setRemark("果蔬出库完成：剩余库存结转损耗");
            stockFlowMapper.insert(flow);

            LossFlow loss = new LossFlow();
            loss.setLossDate(now);
            loss.setProductId(row.getProductId());
            loss.setLocationId(row.getLocationId());
            loss.setPlotId(row.getPlotId());
            loss.setEarNo(row.getEarNo());
            loss.setLossType("veg_handle_loss");
            loss.setLossWeight(row.getStockWeight());
            loss.setOperatorId(userId);
            loss.setSourceBizType("veg_out_finish");
            loss.setSourceBizId(row.getStockId());
            loss.setRemark("果蔬出库完成：剩余库存结转毛菜处理损耗");
            loss.setSourceFlowId(flow.getId());
            Long lossId = lossService.record(loss);
            if (lossId == null) {
                throw new ServiceException("损耗明细写入失败，本次出库完成全部撤销");
            }
            lossIds.add(lossId);
        }
        return lossIds;
    }

    /** 与工作台库存卡的可见分组一致。 */
    private static String groupKey(VegOutStockRow r) {
        return r.getProductId() + "|" + r.getLocationId() + "|" + (r.getEarNo() == null ? "" : r.getEarNo())
            + "|" + r.getPlotId() + "|" + (r.getThirdPhase() != null && r.getThirdPhase() == 1 ? 1 : 0);
    }
}
