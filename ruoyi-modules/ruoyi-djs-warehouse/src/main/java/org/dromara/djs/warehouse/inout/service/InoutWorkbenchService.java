package org.dromara.djs.warehouse.inout.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.inout.domain.bo.BurnWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.bo.CutWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.vo.*;
import org.dromara.djs.warehouse.inout.mapper.InoutWorkbenchMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** 工作台只编排现有记账服务。整猪锁、回执唯一键和库存原子扣减共同保护一次操作。 */
@Service
@RequiredArgsConstructor
public class InoutWorkbenchService {
    private final IPigBurnRecordService burnService;
    private final IPigCutRecordService cutService;
    private final IProductProductionService productionService;
    private final IMatFlowService matFlowService;
    private final ILocationStockService stockService;
    private final BarInfoMapper barMapper;
    private final PigCutRecordMapper cutMapper;
    private final ProductInhouseMapper inhouseMapper;
    private final StockFlowMapper flowMapper;
    private final LocationStockMapper stockMapper;
    private final LocationInfoMapper locationMapper;
    private final InoutWorkbenchMapper workbenchMapper;
    private final DictService dictService;
    private final WeightCompletionPolicy completionPolicy;

    public List<BarPendingVo> burnPigs() { return burnService.queryPendingBars(); }
    public List<BurnProductTypeVo> burnProducts(Long barInfoId) { return burnService.queryProductTypes(barInfoId); }
    public List<CutProductTypeVo> cutProducts() { return cutService.queryCutProductTypes(); }
    public List<CutStoreDemandVo> cutStoreDemands(Long materialProductId) {
        if (materialProductId == null || cutProducts().stream().noneMatch(p -> Objects.equals(p.getProductId(), materialProductId))) {
            throw new ServiceException("请选择有效的分割原材料");
        }
        return workbenchMapper.selectCutStoreDemands(materialProductId, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    }
    public List<StoreDemandCopiesVo> shipStores(Long productId) { return productionService.listStoreDemandCopies(productId); }

    public List<CutWorkbenchBarVo> cutBars() {
        List<CutWorkbenchBarVo> rows = new ArrayList<>(workbenchMapper.selectUnpickedBars());
        rows.addAll(workbenchMapper.selectPickedBars());
        rows.sort(Comparator.comparing(CutWorkbenchBarVo::getInTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(v -> v.getCutRecordId() == null ? v.getInhouseId() : v.getCutRecordId(), Comparator.reverseOrder()));
        return rows;
    }

    public List<RecentOutDestVo> recentOutDests() {
        Map<String, String> labels = dictService.getAllDictByDictType("djs_stock_out_dest");
        List<RecentOutDestVo> rows = workbenchMapper.selectRecentOutDests();
        rows.forEach(r -> r.setLabel(labels.getOrDefault(r.getValue(), r.getValue())));
        return rows;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WorkbenchSubmitVo submitBurn(BurnWorkbenchSubmitBo bo) {
        String key = requestKey(bo.getRequestId());
        String hash = fingerprint("burn", bo.getBarInfoId(), bo.getProductId(), normalized(bo.getWeight()),
            bo.getDestination(), bo.getStoreId(), bo.getOutDest(), LoginHelper.getUserId());
        StockFlow receipt = existing(key);
        if (receipt != null) { return replay(receipt, hash, null); }
        requireBar(bo.getBarInfoId());
        receipt = flowMapper.selectRequestReceipt(key);
        if (receipt != null) { return replay(receipt, hash, null); }

        BurnProductTypeVo product = burnProducts(bo.getBarInfoId()).stream()
            .filter(p -> Objects.equals(p.getProductId(), bo.getProductId())).findFirst()
            .orElseThrow(() -> new ServiceException("请选择有效的燎毛产品"));
        boolean whiteBar = Boolean.TRUE.equals(product.getIsWhiteBar());
        if (!(whiteBar ? Set.of("warehouse", "store") : Set.of("warehouse", "outbound")).contains(bo.getDestination())) {
            throw new ServiceException("所选产品不支持该去向");
        }
        Long locationId = requireConfiguredLocation(product.getDefaultLocationId());
        if ("store".equals(bo.getDestination())) {
            if (bo.getStoreId() == null || shipStores(bo.getProductId()).stream()
                .noneMatch(s -> Objects.equals(s.getStoreId(), bo.getStoreId()) && s.getCopies().signum() > 0)) {
                throw new ServiceException("所选门店没有该产品的未满足需求，请刷新后重选");
            }
        } else if (bo.getStoreId() != null) {
            throw new ServiceException("该去向不接受门店参数");
        }
        validateOutDest(bo.getDestination(), bo.getOutDest());

        PigBurnRecordBo burn = new PigBurnRecordBo();
        burn.setBarInfoId(bo.getBarInfoId());
        burn.setBurnTime(new Date());
        burn.setOperatorId(LoginHelper.getUserId());
        burn.setLocationId(locationId);
        PigBurnRecordBo.ProductTypeItem item = new PigBurnRecordBo.ProductTypeItem();
        item.setProductId(bo.getProductId()); item.setWeight(bo.getWeight());
        burn.setProductTypeItems(List.of(item));
        Long burnRecordId = burnService.submitBurnRecord(burn);
        ProductInhouse source = inhouseMapper.selectOne(new LambdaQueryWrapper<ProductInhouse>()
            .eq(ProductInhouse::getBurnRecordId, burnRecordId));
        if (source == null) { throw new ServiceException("本次入库产出缺失，操作已撤销"); }
        receipt = flowMapper.selectOne(new LambdaQueryWrapper<StockFlow>()
            .eq(StockFlow::getWhiteBarNo, source.getWhiteBarNo())
            .eq(StockFlow::getFlowType, "slaughter_burn").eq(StockFlow::getInoutType, "IN"));
        if ("store".equals(bo.getDestination())) {
            WhiteBarOutBo out = new WhiteBarOutBo();
            out.setSourceInhouseId(source.getId()); out.setProductWeight(bo.getWeight()); out.setStoreId(bo.getStoreId());
            productionService.submitWhiteBarOut(out);
        } else if ("outbound".equals(bo.getDestination())) {
            LocationStock stock = stockMapper.selectOne(new LambdaQueryWrapper<LocationStock>()
                .eq(LocationStock::getWhiteBarNo, source.getWhiteBarNo()).eq(LocationStock::getProductId, bo.getProductId())
                .eq(LocationStock::getLocationId, locationId).last("FOR UPDATE"));
            if (stock == null) { throw new ServiceException("本次入库库存缺失，操作已撤销"); }
            directOut(stock.getId(), bo.getWeight(), bo.getOutDest());
            if (inhouseMapper.deductWeightById(source.getId(), bo.getWeight()) != 1
                || inhouseMapper.deleteById(source.getId()) != 1) {
                throw new ServiceException("本次产出已被占用，操作已撤销");
            }
        }
        return persistReceipt(receipt, key, hash, null);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WorkbenchSubmitVo submitCut(CutWorkbenchSubmitBo bo) {
        if ((bo.getInhouseId() == null) == (bo.getCutRecordId() == null)) {
            throw new ServiceException("请选择一条在库白条或已领用白条");
        }
        String key = requestKey(bo.getRequestId());
        // 保留旧去向回执的指纹，新增门店分支绑定门店、成品与计量确认，重试不可换单。
        String hash = "store".equals(bo.getDestination())
            ? fingerprint("cut-store", bo.getInhouseId(), bo.getCutRecordId(), bo.getProductId(),
                normalized(bo.getWeight()), bo.getStoreId(), bo.getProductionProductId(), Boolean.TRUE.equals(bo.getAllowOverMeasure()), LoginHelper.getUserId())
            : fingerprint("cut", bo.getInhouseId(), bo.getCutRecordId(), bo.getProductId(),
                normalized(bo.getWeight()), bo.getDestination(), bo.getOutDest(), LoginHelper.getUserId());
        StockFlow receipt = existing(key);
        if (receipt != null) { return replayCut(receipt, hash, bo.getCutRecordId()); }
        ProductInhouse source = bo.getInhouseId() == null ? null : inhouseMapper.selectById(bo.getInhouseId());
        PigCutRecord record = bo.getCutRecordId() == null ? null : cutMapper.selectById(bo.getCutRecordId());
        Long barId = source != null ? source.getWhiteBarId() : record != null ? record.getWhiteBarId() : null;
        requireBar(barId);
        receipt = flowMapper.selectRequestReceipt(key);
        if (receipt != null) { return replayCut(receipt, hash, bo.getCutRecordId()); }
        CutProductTypeVo product = cutProducts().stream().filter(p -> Objects.equals(p.getProductId(), bo.getProductId()))
            .findFirst().orElseThrow(() -> new ServiceException("请选择有效的分割产品"));
        validateOutDest(bo.getDestination(), bo.getOutDest());
        CutStoreDemandVo demand = null;
        if ("store".equals(bo.getDestination())) {
            demand = cutStoreDemands(bo.getProductId()).stream()
                .filter(d -> Objects.equals(d.getStoreId(), bo.getStoreId()) && Objects.equals(d.getProductId(), bo.getProductionProductId()))
                .findFirst().orElseThrow(() -> new ServiceException("所选门店没有该原材料外售产品的当天未满足需求，请刷新后重选"));
            if (bo.getWeight().compareTo(demand.getMinimumWeight()) < 0) {
                throw new ServiceException("重量未满足需求或生产计量规则，请处理后再试");
            }
        } else if (bo.getStoreId() != null || bo.getProductionProductId() != null || Boolean.TRUE.equals(bo.getAllowOverMeasure())) {
            throw new ServiceException("该去向不接受门店生产参数");
        }
        Long locationId = switch (bo.getDestination()) {
            case "outbound", "store" -> requireConfiguredLocation(product.getDefaultLocationId());
            case "fresh" -> requireNamedLocation("猪肉鲜品库");
            case "frozen" -> requireNamedLocation("冻品库");
            default -> throw new ServiceException("无效的产品去向");
        };
        Long cutId = bo.getCutRecordId();
        if (source != null) {
            source = inhouseMapper.selectOne(new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getId, source.getId()).last("FOR UPDATE"));
            if (source == null || Integer.valueOf(1).equals(source.getPickupStatus())) {
                throw new ServiceException("白条已领用，请刷新后选择已领用记录");
            }
            // 仅允许列表中的真实白条库存，排除副产和已消费行。
            Long sourceId = source.getId();
            if (workbenchMapper.selectUnpickedBars().stream().noneMatch(b -> Objects.equals(b.getInhouseId(), sourceId))) {
                throw new ServiceException("所选白条已不在可领用库存中，请刷新");
            }
            PigCutPickupBo pickup = new PigCutPickupBo();
            pickup.setBarInfoId(barId); pickup.setInhouseId(source.getId());
            pickup.setPickupWeight(source.getProductWeight()); pickup.setOperatorId(LoginHelper.getUserId());
            pickup.setIsHalf(1);
            cutId = cutService.submitPickup(pickup);
        }
        PigCutOutBo cut = new PigCutOutBo();
        cut.setCutRecordId(cutId); cut.setLocationId(locationId);
        PigCutOutBo.PartItem part = new PigCutOutBo.PartItem();
        part.setProductId(bo.getProductId()); part.setProductWeight(bo.getWeight());
        cut.setPartItems(List.of(part));
        var produced = cutService.submitCutOutWithReceipt(cut);
        if (produced.size() != 1) { throw new ServiceException("分割入库回执异常，操作已撤销"); }
        if ("outbound".equals(bo.getDestination())) {
            directOut(produced.getFirst().stockId(), bo.getWeight(), bo.getOutDest());
        } else if ("store".equals(bo.getDestination())) {
            Long materialSourceId = matFlowService.pickCutOutput(produced.getFirst().stockId(), produced.getFirst().flowId());
            DryPackBo pack = new DryPackBo();
            pack.setSourceInhouseId(materialSourceId); pack.setProductId(bo.getProductionProductId());
            pack.setStoreId(bo.getStoreId()); pack.setProductWeight(bo.getWeight());
            pack.setProductUnit(demand.getProductUnit()); pack.setAllowOverMeasure(bo.getAllowOverMeasure());
            pack.setDeliverDest("platform");
            productionService.submitCutStorePack(pack);
        }
        receipt = flowMapper.selectById(produced.getFirst().flowId());
        return persistReceipt(receipt, key, hash, cutId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CompletionCheckVo cutFinishCheck(Long cutRecordId) {
        return lockedCutFinishCheck(cutRecordId);
    }

    /** 仅新工作台采用可确认的完成比例规则；旧 submitCutDone 入口继续沿用原业务。 */
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void finishCut(org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo bo) {
        CompletionCheckVo check = lockedCutFinishCheck(bo.getCutRecordId());
        WeightCompletionPolicy.requireConfirmation(check, bo.getConfirmAbnormalWeight());
        cutService.submitCutDone(bo);
    }

    private CompletionCheckVo lockedCutFinishCheck(Long cutRecordId) {
        PigCutRecord record = cutRecordId == null ? null : cutMapper.selectById(cutRecordId);
        if (record == null) { throw new ServiceException("分割单不存在，请刷新列表"); }
        requireBar(record.getWhiteBarId());
        record = cutMapper.selectForUpdate(cutRecordId);
        if (record == null || !"cutting".equals(record.getCutStatus())
            || (record.getOutType() != null && !"cut".equals(record.getOutType()))) {
            throw new ServiceException("分割单状态不符，请先录入分割产品后再完成");
        }
        if (record.getPickupTime() == null || record.getPickupWeight() == null || record.getPickupWeight().signum() <= 0) {
            throw new ServiceException("分割单原始领用信息缺失或无效，请核对记录");
        }
        boolean numbered = record.getWhiteBarNo() != null && !record.getWhiteBarNo().isBlank();
        BigDecimal produced = numbered ? flowMapper.sumCutOutByWhiteBarNo(record.getWhiteBarNo())
            : flowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
        if (produced == null) { produced = BigDecimal.ZERO; }
        if (produced.signum() < 0 || produced.compareTo(record.getPickupWeight()) > 0) {
            throw new ServiceException("分割产品累计重量超过领用重或数据无效，请核对出入库记录");
        }
        BigDecimal original = workbenchMapper.selectCutOriginalInWeight(cutRecordId);
        if (original != null && record.getPickupWeight().compareTo(original) > 0) {
            throw new ServiceException("领用重量超过原入库重量，请核对原始记录");
        }
        return completionPolicy.cut(record.getPickupWeight().subtract(produced), original);
    }

    private StockFlow existing(String key) {
        return flowMapper.selectOne(new LambdaQueryWrapper<StockFlow>().eq(StockFlow::getRequestKey, key));
    }

    private WorkbenchSubmitVo replayCut(StockFlow flow, String hash, Long cutId) {
        if (cutId == null) {
            PigCutRecord record = cutMapper.selectOne(new LambdaQueryWrapper<PigCutRecord>()
                .eq(PigCutRecord::getWhiteBarNo, flow.getWhiteBarNo()).eq(PigCutRecord::getOutType, "cut"));
            if (record == null) { throw new ServiceException("成功回执对应的分割记录缺失，请联系管理员"); }
            cutId = record.getId();
        }
        return replay(flow, hash, cutId);
    }

    private WorkbenchSubmitVo replay(StockFlow flow, String hash, Long cutId) {
        if (!Objects.equals(flow.getRequestHash(), hash)) {
            throw new ServiceException("requestId 已用于不同操作，请为新操作生成新的 requestId");
        }
        return new WorkbenchSubmitVo(flow.getRequestKey(), flow.getId(), cutId);
    }

    private WorkbenchSubmitVo persistReceipt(StockFlow flow, String key, String hash, Long cutId) {
        if (flow == null) { throw new ServiceException("入库流水回执缺失，操作已撤销"); }
        StockFlow update = new StockFlow();
        update.setId(flow.getId()); update.setRequestKey(key); update.setRequestHash(hash);
        try {
            if (flowMapper.updateById(update) != 1) { throw new ServiceException("回执保存失败，操作已撤销"); }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("requestId 已被使用，本次操作已撤销，请核对后重试");
        }
        return new WorkbenchSubmitVo(key, flow.getId(), cutId);
    }

    private BarInfo requireBar(Long id) {
        BarInfo bar = id == null ? null : barMapper.selectForUpdate(id);
        if (bar == null) { throw new ServiceException("猪只或白条不存在，请刷新列表"); }
        return bar;
    }

    private Long requireConfiguredLocation(Long id) {
        LocationInfo location = id == null ? null : locationMapper.selectById(id);
        if (location == null || !Integer.valueOf(1).equals(location.getLocationStatus())) {
            throw new ServiceException("请先为产品配置有效的默认存储库位");
        }
        return id;
    }

    private Long requireNamedLocation(String name) {
        List<LocationInfo> locations = locationMapper.selectList(new LambdaQueryWrapper<LocationInfo>()
            .eq(LocationInfo::getLocationName, name).eq(LocationInfo::getLocationStatus, 1));
        if (locations.size() != 1) { throw new ServiceException("请配置唯一启用的" + name); }
        return locations.getFirst().getId();
    }

    private void validateOutDest(String destination, String outDest) {
        if ("outbound".equals(destination)) {
            if (outDest == null || !dictService.getAllDictByDictType("djs_stock_out_dest").containsKey(outDest)) {
                throw new ServiceException("请选择有效的仓库出库去向");
            }
        } else if (outDest != null && !outDest.isBlank()) {
            throw new ServiceException("该去向不接受仓库出库去向参数");
        }
    }

    private void directOut(Long stockId, BigDecimal weight, String destination) {
        StockOutBo out = new StockOutBo();
        out.setStockIds(List.of(stockId)); out.setQuantity(weight); out.setOutDate(new Date()); out.setStockOutDest(destination);
        stockService.cutRoomOut(out);
    }

    static String requestKey(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException | NullPointerException e) { throw new ServiceException("requestId 必须为 UUID"); }
    }

    private static String normalized(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 3) { throw new ServiceException("请输入有效重量，最多三位小数"); }
        return value.stripTrailingZeros().toPlainString();
    }

    static String fingerprint(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            String value = field == null ? "" : field.toString();
            canonical.append(field == null ? -1 : value.length()).append(':').append(value);
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
