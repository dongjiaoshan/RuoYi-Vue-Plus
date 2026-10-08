package org.dromara.djs.warehouse.inout.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.team.constant.PlantTeamConstants;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
import org.dromara.djs.warehouse.inout.domain.bo.VegInFinishBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegInSubmitBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.vo.RecentOutDestVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegInOptionsVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutProductVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockRow;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegTeamOptionVo;
import org.dromara.djs.warehouse.inout.mapper.VegInoutWorkbenchMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutItemBo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.service.IVegOutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 果蔬入库 / 出库工作台（V6 row282 / row283）。
 *
 * <p>只做编排与展示，不写任何库存或台账：</p>
 * <ul>
 *   <li>入库 = 小程序毛菜处理「采摘录入」：{@link IVegetableHandleService#submitHarvest}
 *       （handle_record + 毛菜保鲜库地块篮 + veg_stock_in 流水，毛菜保鲜库入库的唯一写入口）；
 *       「处理完成」= 同一入口按 0 kg + 地块称重完成收口，判定全部由该入口执行。</li>
 *   <li>出库 = 毛菜间出库的记账：{@link IVegOutService#submitVegetableOut}
 *       （先进先出扣篮 + backstage_out 流水；去向「猪只饲料」同时写有机饲喂记录 feed_log）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VegInoutWorkbenchService {

    /** 毛菜保鲜库库位编码（采摘录入的入库落点，见 VegetableHandleServiceImpl）。 */
    static final String LOCATION_CODE_FRESH_VEG = "L0006";
    /** 去向：仓库出库。 */
    static final String DEST_WAREHOUSE = "warehouse";
    /** 去向：猪养殖饲料 = 出库去向字典的「猪只饲料」。 */
    static final String DEST_FEED = "feed";
    static final String OUT_DEST_DICT = "djs_stock_out_dest";

    private final IVegetableHandleService vegHandleService;
    private final IVegOutService vegOutService;
    private final IPlantWorkTeamService teamService;
    private final LocationInfoMapper locationMapper;
    private final VegInoutWorkbenchMapper workbenchMapper;
    private final DictService dictService;

    /** 作物列表：与小程序毛菜处理作物列表同源。 */
    public List<VegCropVo> crops() {
        return vegHandleService.listCrops();
    }

    /** 作物的采摘地块（含产品配置与称重状态）：与小程序毛菜处理作物详情同源。 */
    public List<VegPlotDetailVo> plots(Long cropId) {
        return vegHandleService.listPlotsByCrop(cropId);
    }

    /** 去向库位名 + 启用班组（小程序 TeamPicker 同样只列启用班组）。 */
    public VegInOptionsVo inOptions() {
        LocationInfo location = locationMapper.selectOne(new LambdaQueryWrapper<LocationInfo>()
            .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG).last("LIMIT 1"));
        PlantWorkTeamQuery query = new PlantWorkTeamQuery();
        query.setTeamStatus(PlantTeamConstants.TEAM_STATUS_ACTIVE);
        List<VegTeamOptionVo> teams = teamService.queryList(query).stream()
            .map(t -> new VegTeamOptionVo(t.getId(), t.getTeamName())).toList();
        return new VegInOptionsVo(location == null ? null : location.getLocationName(), teams);
    }

    /** 确认入库：一次采摘称重，不收口。 */
    public Long submitIn(VegInSubmitBo bo) {
        requirePlot(bo.getPlantingRecordId());
        BigDecimal weight = requirePositive(bo.getWeight(), "入库");
        HarvestSubmitBo harvest = harvest(bo.getPlantingRecordId(), requireTeams(bo.getTeamIds()),
            requirePercent(bo.getPerfPercent()));
        harvest.setProductId(bo.getProductId());
        harvest.setHarvestWeight(weight);
        harvest.setWeighFinish(0);
        return vegHandleService.submitHarvest(harvest);
    }

    /** 处理完成：0 kg + 地块称重完成，种植采收未完成 / 已收口由采摘录入入口拒绝。 */
    public Long finishIn(VegInFinishBo bo) {
        requirePlot(bo.getPlantingRecordId());
        HarvestSubmitBo harvest = harvest(bo.getPlantingRecordId(), requireTeams(bo.getTeamIds()),
            requirePercent(bo.getPerfPercent()));
        harvest.setHarvestWeight(BigDecimal.ZERO);
        harvest.setWeighFinish(1);
        return vegHandleService.submitHarvest(harvest);
    }

    /** 库存里的果蔬产品，跨库位合计。 */
    public List<VegOutProductVo> outProducts() {
        Map<Long, VegOutProductVo> products = new LinkedHashMap<>();
        Map<Long, Set<Long>> plots = new LinkedHashMap<>();
        for (VegOutStockRow row : workbenchMapper.selectVegStocks(null)) {
            VegOutProductVo vo = products.computeIfAbsent(row.getProductId(), id -> {
                VegOutProductVo v = new VegOutProductVo();
                v.setProductId(id);
                v.setProductName(row.getProductName());
                v.setProductUnit(row.getProductUnit());
                v.setTotalStock(BigDecimal.ZERO);
                return v;
            });
            vo.setTotalStock(vo.getTotalStock().add(nz(row.getStockWeight())));
            Set<Long> productPlots = plots.computeIfAbsent(row.getProductId(), id -> new HashSet<>());
            if (row.getPlotId() != null && !isThirdPhase(row)) {
                productPlots.add(row.getPlotId());
            }
        }
        products.forEach((id, vo) -> vo.setPlotCount(plots.get(id).size()));
        return new ArrayList<>(products.values());
    }

    /** 所选产品的地块卡：一张卡 = 一个可整组先进先出出库的库存组；无地块库存单独成卡。 */
    public List<VegOutStockVo> outStocks(Long productId) {
        if (productId == null) {
            throw new ServiceException("请选择果蔬产品");
        }
        Map<String, VegOutStockVo> cards = new LinkedHashMap<>();
        for (VegOutStockRow row : workbenchMapper.selectVegStocks(productId)) {
            VegOutStockVo card = cards.computeIfAbsent(groupKey(row), k -> {
                VegOutStockVo v = new VegOutStockVo();
                v.setProductId(row.getProductId());
                v.setPlotId(row.getPlotId());
                v.setPlotCode(row.getPlotCode());
                v.setPlotName(row.getPlotName());
                v.setEarNo(row.getEarNo());
                v.setThirdPhase(isThirdPhase(row) ? 1 : 0);
                v.setLocationId(row.getLocationId());
                v.setLocationName(row.getLocationName());
                v.setStockWeight(BigDecimal.ZERO);
                v.setStockIds(new ArrayList<>());
                return v;
            });
            card.getStockIds().add(row.getStockId());
            card.setStockWeight(card.getStockWeight().add(nz(row.getStockWeight())));
        }
        List<VegOutStockVo> result = new ArrayList<>(cards.values());
        // 有地块的卡在前（按地块编号），无地块 / 三期卡排在最后，即原文「额外显示」的那张。
        result.sort((a, b) -> {
            boolean aPlot = a.getPlotId() != null && a.getThirdPhase() == 0;
            boolean bPlot = b.getPlotId() != null && b.getThirdPhase() == 0;
            if (aPlot != bPlot) {
                return aPlot ? -1 : 1;
            }
            int byPlot = String.valueOf(a.getPlotCode()).compareTo(String.valueOf(b.getPlotCode()));
            return byPlot != 0 ? byPlot : String.valueOf(a.getLocationName()).compareTo(String.valueOf(b.getLocationName()));
        });
        return result;
    }

    /** 近 30 天果蔬产品出库最多的 10 个去向，只保留字典里仍存在的值。 */
    public List<RecentOutDestVo> recentOutDests() {
        Map<String, String> labels = dictService.getAllDictByDictType(OUT_DEST_DICT);
        List<RecentOutDestVo> rows = new ArrayList<>();
        for (RecentOutDestVo row : workbenchMapper.selectRecentVegOutDests()) {
            if (labels.containsKey(row.getValue())) {
                row.setLabel(labels.get(row.getValue()));
                rows.add(row);
                if (rows.size() == 10) {
                    break;
                }
            }
        }
        return rows;
    }

    /**
     * 确认出库：仓库出库按所选出库去向；猪养殖饲料按有机饲喂（去向「猪只饲料」）出库。
     *
     * <p>先按库存现状重算这张卡：篮必须仍是该产品的果蔬库存、属于同一库存组且总量够，
     * 再按先进先出序交给毛菜间出库记账（它在扣减时还会逐篮原子校验一次）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitOut(VegOutWorkbenchSubmitBo bo) {
        if (bo.getProductId() == null) {
            throw new ServiceException("请选择果蔬产品");
        }
        if (bo.getStockIds() == null || bo.getStockIds().stream().noneMatch(Objects::nonNull)) {
            throw new ServiceException("请选择出库地块");
        }
        BigDecimal weight = requirePositive(bo.getWeight(), "出库");
        String outDest = resolveOutDest(bo.getDestination(), bo.getOutDest());

        Set<Long> requested = new LinkedHashSet<>(bo.getStockIds());
        requested.remove(null);
        List<VegOutStockRow> currentStocks = workbenchMapper.selectVegStocks(bo.getProductId());
        List<VegOutStockRow> baskets = currentStocks.stream()
            .filter(r -> requested.contains(r.getStockId())).toList();
        if (baskets.size() != requested.size()) {
            throw new ServiceException("所选地块库存已变化，请刷新后重新选择");
        }
        if (baskets.stream().map(VegInoutWorkbenchService::groupKey).distinct().count() != 1) {
            throw new ServiceException("所选库存不属于同一地块与库位，请刷新后重新选择");
        }
        String selectedGroup = groupKey(baskets.getFirst());
        Set<Long> completeCard = currentStocks.stream()
            .filter(row -> selectedGroup.equals(groupKey(row)))
            .map(VegOutStockRow::getStockId).collect(java.util.stream.Collectors.toSet());
        if (!completeCard.equals(requested)) {
            throw new ServiceException("所选地块库存已变化，请刷新后重新选择");
        }
        BigDecimal available = baskets.stream().map(r -> nz(r.getStockWeight())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weight.compareTo(available) > 0) {
            throw new ServiceException("出库重量超过所选地块库存（剩余 " + available.stripTrailingZeros().toPlainString() + " kg）");
        }

        VegOutItemBo item = new VegOutItemBo();
        item.setStockIds(baskets.stream().map(VegOutStockRow::getStockId).toList());
        item.setQuantity(weight);
        VegOutSubmitBo out = new VegOutSubmitBo();
        out.setOutDate(new Date());
        out.setOutDest(outDest);
        out.setItems(List.of(item));
        vegOutService.submitVegetableOut(out);
    }

    private String resolveOutDest(String destination, String outDest) {
        if (DEST_FEED.equals(destination)) {
            if (outDest != null && !outDest.isBlank()) {
                throw new ServiceException("猪养殖饲料去向不接受仓库出库去向参数");
            }
            return DEST_FEED;
        }
        if (!DEST_WAREHOUSE.equals(destination)) {
            throw new ServiceException("无效的去向");
        }
        if (outDest == null || !dictService.getAllDictByDictType(OUT_DEST_DICT).containsKey(outDest)) {
            throw new ServiceException("请选择有效的仓库出库去向");
        }
        return outDest;
    }

    private static HarvestSubmitBo harvest(Long plantingRecordId, List<Long> teamIds, Integer perfPercent) {
        HarvestSubmitBo harvest = new HarvestSubmitBo();
        harvest.setPlantingRecordId(plantingRecordId);
        harvest.setWeighUserId(LoginHelper.getUserId());
        harvest.setTeamIds(teamIds);
        harvest.setPerfPercent(perfPercent);
        return harvest;
    }

    private static void requirePlot(Long plantingRecordId) {
        if (plantingRecordId == null) {
            throw new ServiceException("请选择采摘地块");
        }
    }

    private List<Long> requireTeams(List<Long> teamIds) {
        List<Long> distinct = teamIds == null ? List.of()
            : new ArrayList<>(new LinkedHashSet<>(teamIds.stream().filter(Objects::nonNull).toList()));
        if (distinct.isEmpty()) {
            throw new ServiceException("请选择采摘班组");
        }
        PlantWorkTeamQuery query = new PlantWorkTeamQuery();
        query.setTeamStatus(PlantTeamConstants.TEAM_STATUS_ACTIVE);
        Set<Long> activeTeams = teamService.queryList(query).stream()
            .map(team -> team.getId()).collect(java.util.stream.Collectors.toSet());
        if (!activeTeams.containsAll(distinct)) {
            throw new ServiceException("采摘班组不存在或已停用，请刷新后重新选择");
        }
        return distinct;
    }

    private static Integer requirePercent(Integer percent) {
        if (percent == null || percent < 1 || percent > 100) {
            throw new ServiceException("绩效百分比须为 1-100 的整数");
        }
        return percent;
    }

    private static BigDecimal requirePositive(BigDecimal weight, String action) {
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("请输入大于 0 的" + action + "重量");
        }
        if (weight.stripTrailingZeros().scale() > 3) {
            throw new ServiceException(action + "重量最多三位小数");
        }
        return weight;
    }

    /** 与 FifoAllocator 认定「同一行」的键一致（产品 + 库位 + 耳号 + 地块 + 三期）。 */
    private static String groupKey(VegOutStockRow r) {
        return r.getProductId() + "|" + r.getLocationId() + "|" + (r.getEarNo() == null ? "" : r.getEarNo())
            + "|" + r.getPlotId() + "|" + (isThirdPhase(r) ? 1 : 0);
    }

    private static boolean isThirdPhase(VegOutStockRow r) {
        return r.getThirdPhase() != null && r.getThirdPhase() == 1;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
