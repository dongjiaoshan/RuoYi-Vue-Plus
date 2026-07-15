package org.dromara.djs.breed.event.growth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.I18nMessages;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.domain.bo.GrowthBo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.event.growth.service.IPigGrowthService;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生长记录 Service 实现（BRD-EVENT-005 GROWTH）。
 *
 * <h3>事务边界</h3>
 * <p>{@link #addGrowthRecord} 标 {@code @Transactional}：仅 INSERT t_farm_pig_growth，
 * 不调用状态机（生长记录只是测量数据）。</p>
 *
 * <h3>校验</h3>
 * <ul>
 *   <li>pig 必须存在（否则 ServiceException）</li>
 *   <li>weight / backfatThickness 均可选（各端按需前端校验，> 0 且 ≤ 999.99 由 @Valid 兜底）；
 *       mp 端主录 backfatThickness，admin 端可录 weight</li>
 *   <li>删除：{@code deleteById} hard delete（mp / admin 列表删除即物理删，fe-growth row54）；
 *       {@code deleteByIds} 保留 3 天内逻辑删（admin 批量历史 API）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PigGrowthServiceImpl implements IPigGrowthService {

    /** 删除生长记录的时效（创建后 N 天内可删）。 */
    private static final long DELETE_WINDOW_DAYS = 3L;

    private final PigGrowthMapper growthMapper;
    private final PigMapper pigMapper;
    private final BarnMapper barnMapper;
    private final PenMapper penMapper;
    private final IFattenAgeStageService fattenAgeStageService;
    private final OssService ossService;
    private final org.dromara.djs.breed.core.service.IPigCoreService pigCoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PigGrowthVo addGrowthRecord(GrowthBo bo) {
        Objects.requireNonNull(bo, "GrowthBo must not be null");

        // 二选一支持：mp 端传 earNo；admin 端传 pigId（与 D5 BRD-EVENT-001 supplierCode 模式一致）
        if (bo.getPigId() == null) {
            if (bo.getEarNo() == null || bo.getEarNo().isBlank()) {
                throw new ServiceException(I18nMessages.t("pig.id_or_ear_required"), 400);
            }
            Long resolved = pigMapper.selectIdByEarNo(bo.getEarNo());
            if (resolved == null) {
                throw new ServiceException(I18nMessages.t("pig.not_found_by_ear", bo.getEarNo()), 400);
            }
            bo.setPigId(resolved);
        }
        Pig pig = pigMapper.selectById(bo.getPigId());
        if (pig == null) {
            throw new ServiceException(I18nMessages.t("pig.not_found", bo.getPigId()));
        }

        PigGrowth entity = new PigGrowth();
        entity.setPigId(pig.getId());
        entity.setEarNo(pig.getEarNo());
        entity.setMeasureDate(bo.getMeasureDate());
        entity.setWeight(bo.getWeight());
        entity.setBackfatThickness(bo.getBackfatThickness());
        entity.setBackHeight(bo.getBackHeight());
        entity.setPhotoOssIds(bo.getPhotoOssIds());
        entity.setBarnName(resolveBarnName(pig.getBarnId()));
        entity.setPenName(resolvePenName(pig.getPenId()));
        entity.setRemark(bo.getRemark());
        // row52：记录人 = BO 显式传的 operatorId（EmployeePicker 选的现场记录人，snowflake string → Long）；
        // 兜底回落登录态（理论上 @NotBlank 已挡空，防御性 fallback 避免 NPE）。
        entity.setOperatorId(resolveOperatorId(bo.getOperatorId()));
        entity.setDelFlag("0");
        growthMapper.insert(entity);

        log.info("[BRD-EVENT-005] addGrowthRecord pigId={} earNo={} measureDate={} weight={} growthId={}",
            pig.getId(), pig.getEarNo(), bo.getMeasureDate(), bo.getWeight(), entity.getId());

        return toVo(entity);
    }

    @Override
    public List<PigSearchVo> listGrowthTodo(String earNoKeyword) {
        // 1. 取 record_growth=1 的日龄段（升序）；无配置 → 没有待办，返空
        List<FattenAgeStageVo> stages = fattenAgeStageService.queryList().stream()
            .filter(s -> s.getRecordGrowth() != null && s.getRecordGrowth() == 1
                && s.getStartAge() != null && s.getEndAge() != null)
            .collect(Collectors.toList());
        if (stages.isEmpty()) {
            return List.of();
        }

        // 2. 取在场育肥猪（pig_type='fattening' AND status != END），可选耳号 LIKE
        List<Pig> pigs = pigMapper.selectList(Wrappers.<Pig>lambdaQuery()
            .eq(Pig::getPigType, "fattening")
            .ne(Pig::getCurrentStatus, PigLifecycle.END.name())
            .like(StringUtils.isNotBlank(earNoKeyword), Pig::getEarNo, earNoKeyword)
            .orderByDesc(Pig::getId));
        if (pigs.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        // 命中阶段且「该段日龄区间内尚无生长记录」的育肥猪
        List<Pig> todoPigs = new ArrayList<>();
        for (Pig p : pigs) {
            LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
            if (base == null) {
                continue; // 无基准日期算不出日龄 → 跳过（与 ageDist 一致）
            }
            int ageDays = (int) Math.max(ChronoUnit.DAYS.between(base, today), 0L);
            // 找当前日龄落在哪个 record_growth=1 段（含端点）
            FattenAgeStageVo hit = stages.stream()
                .filter(s -> ageDays >= s.getStartAge() && ageDays <= s.getEndAge())
                .findFirst().orElse(null);
            if (hit == null) {
                continue; // 当前日龄不在任何需记录段内
            }
            // 该段日龄区间映射成实际日期窗口 [base+startAge, base+endAge]，查该窗口内是否已有任意 1 条生长记录
            LocalDate windowStart = base.plusDays(hit.getStartAge());
            LocalDate windowEnd = base.plusDays(hit.getEndAge());
            Long recorded = growthMapper.selectCount(Wrappers.<PigGrowth>lambdaQuery()
                .eq(PigGrowth::getPigId, p.getId())
                .ge(PigGrowth::getMeasureDate, windowStart)
                .le(PigGrowth::getMeasureDate, windowEnd));
            if (recorded != null && recorded > 0) {
                continue; // 该阶段已录过 → 剔除（每阶段只录一次）
            }
            todoPigs.add(p);
        }
        if (todoPigs.isEmpty()) {
            return List.of();
        }

        // 3. 批量 enrich 栋舍/栏位 + 日龄，组装 PigSearchVo（与 searchByEarKeyword 卡片字段一致）
        Set<Long> barnIds = todoPigs.stream().map(Pig::getBarnId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> penIds = todoPigs.stream().map(Pig::getPenId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Barn> barnMap = barnIds.isEmpty() ? Map.of()
            : barnMapper.selectBatchIds(barnIds).stream().collect(Collectors.toMap(Barn::getId, b -> b, (a, b) -> a));
        Map<Long, Pen> penMap = penIds.isEmpty() ? Map.of()
            : penMapper.selectBatchIds(penIds).stream().collect(Collectors.toMap(Pen::getId, p -> p, (a, b) -> a));
        // row50：批量取各猪「上次生长记录日期」MAX(measure_date)，给 mp 卡片「上次测量」格用（防 N+1）
        Map<Long, LocalDate> lastMeasureMap = loadLastMeasureDateMap(
            todoPigs.stream().map(Pig::getId).collect(Collectors.toList()));
        // row12：育肥生长记录录入弹框「品系/品种」此前恒空——本路径漏 enrich。与 searchByEarKeyword 同口径
        // 预载 breed_info 名映射（1=品种 / 2=品系）防 N+1，主数据优先 → 字典回落 → code 回落。
        Map<String, String> breedNameMap = pigCoreService.loadBreedStrainNameMap(1);
        Map<String, String> strainNameMap = pigCoreService.loadBreedStrainNameMap(2);

        List<PigSearchVo> result = new ArrayList<>(todoPigs.size());
        for (Pig p : todoPigs) {
            PigSearchVo vo = new PigSearchVo();
            vo.setId(p.getId());
            vo.setEarNo(p.getEarNo());
            vo.setPigSex(p.getPigSex());
            vo.setPigType(p.getPigType());
            // row50：猪只类型中文（育肥待办全是 fattening → 育肥猪）
            vo.setPigTypeName(toPigTypeName(p.getPigType()));
            vo.setCurrentStatus(p.getCurrentStatus());
            // row50：上次生长记录日期（无记录 → null，mp 卡片该格显「—」）
            vo.setLastMeasureDate(lastMeasureMap.get(p.getId()));
            // row12：品种/品系编码 + 中文名（育肥生长录入弹框「品系/品种」格用），与 searchByEarKeyword 同口径
            vo.setPigBreedCode(p.getPigBreedCode());
            vo.setPigBreedName(pigCoreService.resolveBreedStrainName(breedNameMap, p.getPigBreedCode()));
            vo.setPigStrainCode(p.getPigStrainCode());
            vo.setPigStrainName(pigCoreService.resolveBreedStrainName(strainNameMap, p.getPigStrainCode()));
            LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
            if (base != null) {
                vo.setAgeDays((int) Math.max(ChronoUnit.DAYS.between(base, today), 0L));
            }
            vo.setParity(p.getParity());
            if (p.getBarnId() != null) {
                Barn barn = barnMap.get(p.getBarnId());
                if (barn != null) {
                    vo.setBarnCode(barn.getBarnCode());
                    vo.setBarnName(barn.getBarnName());
                }
            }
            if (p.getPenId() != null) {
                Pen pen = penMap.get(p.getPenId());
                if (pen != null) {
                    vo.setPenCode(pen.getPenCode());
                    vo.setPenName(pen.getPenName());
                }
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public TableDataInfo<PigGrowthVo> queryPage(GrowthQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigGrowth> w = Wrappers.<PigGrowth>lambdaQuery()
            .eq(query.getPigId() != null, PigGrowth::getPigId, query.getPigId())
            .like(StringUtils.isNotBlank(query.getEarNo()), PigGrowth::getEarNo, query.getEarNo())
            .ge(query.getBeginDate() != null, PigGrowth::getMeasureDate, query.getBeginDate())
            .le(query.getEndDate() != null, PigGrowth::getMeasureDate, query.getEndDate())
            .orderByDesc(PigGrowth::getMeasureDate, PigGrowth::getId);
        Page<PigGrowthVo> page = growthMapper.selectVoPage(pageQuery.build(), w);
        enrichAgeDays(page.getRecords());
        enrichPhotoUrls(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 列表 enrich 测量时日龄（mp timeline「42 日龄」用）。
     *
     * <p>日龄 = measureDate - pig.birthDate（缺时 fallback introduceDate）；批查去重 pigId 避免 N+1。
     * 基准日期均空 → ageDays 留 null（mp 端该格不渲染）。</p>
     */
    private void enrichAgeDays(java.util.List<PigGrowthVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.List<Long> pigIds = rows.stream()
            .map(PigGrowthVo::getPigId).filter(Objects::nonNull).distinct().toList();
        if (pigIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, Pig> pigById = new java.util.HashMap<>();
        for (Pig p : pigMapper.selectByIds(pigIds)) {
            pigById.put(p.getId(), p);
        }
        for (PigGrowthVo vo : rows) {
            Pig p = vo.getPigId() == null ? null : pigById.get(vo.getPigId());
            if (p == null) {
                continue;
            }
            // row54：猪只类型中文（记录列表卡第二行「猪只类型」格）
            vo.setPigTypeName(toPigTypeName(p.getPigType()));
            if (vo.getMeasureDate() == null) {
                continue;
            }
            LocalDate base = p.getBirthDate() != null ? p.getBirthDate() : p.getIntroduceDate();
            if (base != null) {
                vo.setAgeDays((int) ChronoUnit.DAYS.between(base, vo.getMeasureDate()));
            }
        }
    }

    /**
     * 列表 enrich 测量照片可访问 URL（mp timeline 缩略图用）。
     *
     * <p>photoOssIds（逗号分隔 ossId）→ {@link OssService#selectUrlByIds} 预解析成直链列表注入
     * {@code vo.photoUrls}（仿 {@code PigIntroServiceImpl.resolveProofUrls}）；mp {@code <image>}
     * 无法携 Bearer token 取鉴权下载端点，故后端预解析。无照片 → 空 list。</p>
     */
    private void enrichPhotoUrls(java.util.List<PigGrowthVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (PigGrowthVo vo : rows) {
            String photoOssIds = vo.getPhotoOssIds();
            if (StringUtils.isBlank(photoOssIds)) {
                vo.setPhotoUrls(List.of());
                continue;
            }
            String urls = ossService.selectUrlByIds(photoOssIds);
            if (StringUtils.isBlank(urls)) {
                vo.setPhotoUrls(List.of());
                continue;
            }
            vo.setPhotoUrls(Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList()));
        }
    }

    @Override
    public PigGrowthVo getById(Long id) {
        if (id == null) {
            throw new ServiceException(I18nMessages.t("growth.id.required"));
        }
        PigGrowth entity = growthMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("growth.not_found", id));
        }
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 3 天内可删：逐条校验 createTime，超期抛 ServiceException
        LocalDate today = LocalDate.now();
        for (Long id : ids) {
            PigGrowth entity = growthMapper.selectById(id);
            if (entity == null) {
                continue;
            }
            Date createTime = entity.getCreateTime();
            if (createTime != null) {
                LocalDate created = createTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long daysSince = ChronoUnit.DAYS.between(created, today);
                if (daysSince > DELETE_WINDOW_DAYS) {
                    throw new ServiceException(I18nMessages.t("growth.delete.expired", id, DELETE_WINDOW_DAYS));
                }
            }
        }
        return growthMapper.deleteByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        if (id == null) {
            throw new ServiceException(I18nMessages.t("growth.id.required"));
        }
        // row54：hard delete——直接 DELETE FROM t_farm_pig_growth WHERE id=?（tenant_id 由 MP 拦截器自动注入）。
        // 与 deleteByIds 的「3 天内逻辑删」不同：mp 记录列表卡删除即物理删，删后 reload 列表。
        return growthMapper.deleteById(id);
    }

    /**
     * 解析记录人 userId（BO 传 snowflake string → Long）。
     * 空 / 非法回落登录态（@NotBlank 已挡空，本兜底防御 controller 外的内部调用）。
     */
    private Long resolveOperatorId(String operatorId) {
        if (StringUtils.isBlank(operatorId)) {
            return LoginHelper.getUserId();
        }
        try {
            return Long.valueOf(operatorId.trim());
        }
        catch (NumberFormatException e) {
            throw new ServiceException(I18nMessages.t("growth.operator.required"), 400);
        }
    }

    /**
     * 猪只类型 code → 中文（种母猪 / 种公猪 / 育肥猪 / 仔猪，跨层契约 fe-growth row50/51/54）。
     * 字典 djs_pig_type 标签为「母猪 / 公猪」，契约要求带「种」前缀，故显式映射保证一致；未知 code → null。
     */
    private String toPigTypeName(String pigType) {
        if (StringUtils.isBlank(pigType)) {
            return null;
        }
        return switch (pigType) {
            case "sow" -> "种母猪";
            case "boar" -> "种公猪";
            case "fattening" -> "育肥猪";
            case "piglet" -> "仔猪";
            default -> null;
        };
    }

    /**
     * 批量取各猪「上次生长记录日期」MAX(measure_date)（row50/51 mp 卡片「上次测量」格用）。
     * <p>一次 IN 查全部目标猪的生长记录，内存按 pigId 归约取最大 measure_date，避免逐猪查（N+1）。
     * 无记录的猪不入 map（调用方 get 得 null）。</p>
     */
    private Map<Long, LocalDate> loadLastMeasureDateMap(List<Long> pigIds) {
        if (pigIds == null || pigIds.isEmpty()) {
            return Map.of();
        }
        List<PigGrowth> records = growthMapper.selectList(Wrappers.<PigGrowth>lambdaQuery()
            .select(PigGrowth::getPigId, PigGrowth::getMeasureDate)
            .in(PigGrowth::getPigId, pigIds)
            .isNotNull(PigGrowth::getMeasureDate));
        Map<Long, LocalDate> map = new java.util.HashMap<>();
        for (PigGrowth r : records) {
            if (r.getPigId() == null || r.getMeasureDate() == null) {
                continue;
            }
            map.merge(r.getPigId(), r.getMeasureDate(),
                (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
        }
        return map;
    }

    private String resolveBarnName(Long barnId) {
        if (barnId == null) {
            return null;
        }
        Barn b = barnMapper.selectById(barnId);
        return b == null ? null : b.getBarnName();
    }

    private String resolvePenName(Long penId) {
        if (penId == null) {
            return null;
        }
        Pen p = penMapper.selectById(penId);
        return p == null ? null : p.getPenName();
    }

    private PigGrowthVo toVo(PigGrowth e) {
        PigGrowthVo v = new PigGrowthVo();
        v.setId(e.getId());
        v.setPigId(e.getPigId());
        v.setEarNo(e.getEarNo());
        v.setMeasureDate(e.getMeasureDate());
        v.setWeight(e.getWeight());
        v.setBackfatThickness(e.getBackfatThickness());
        v.setBackHeight(e.getBackHeight());
        v.setPhotoOssIds(e.getPhotoOssIds());
        v.setOperatorId(e.getOperatorId());
        v.setBarnName(e.getBarnName());
        v.setPenName(e.getPenName());
        v.setRemark(e.getRemark());
        Date created = e.getCreateTime();
        if (created != null) {
            v.setCreateTime(created.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return v;
    }
}
