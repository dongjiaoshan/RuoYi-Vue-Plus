package org.dromara.djs.breed.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.domain.query.ProductionCycleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.ProductionCycleConfigVo;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产周期配置 Controller（BRD-MD-003 Tab1 + A1 母猪生产配置 / 出栏配置）。
 *
 * <p>权限串 {@code djs:breed:production-cycle:*}，菜单 {@code 7050-7059}。</p>
 *
 * <p>A1 新增表单语义端点（非 list-CRUD）：</p>
 * <ul>
 *   <li>{@code GET /sow/get} + {@code POST /sow/save} —— 母猪生产配置 6 项（断奶-配种 / 返情-配种 /
 *       空怀-配种 / 流产-配种 / 配种-分娩 / 分娩-断奶），整数天数表单</li>
 *   <li>{@code GET /slaughter/get} + {@code POST /slaughter/save} —— 出栏配置（出栏日龄单值）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/production/cycle")
public class ProductionCycleConfigController extends BaseController {

    private final IProductionCycleConfigService cycleConfigService;

    /**
     * 母猪生产配置 7 项业务键 → 业内默认值（首次落库填 default_value 用）。
     * <p>注意：与旧 6 周期 key（gestation_days 等）独立，不替换、不删；新增。</p>
     */
    private static final Map<String, Integer> SOW_DEFAULTS = new LinkedHashMap<>();
    /** 母猪生产配置 7 项业务键 → 说明（首次落库填 description 用）。 */
    private static final Map<String, String> SOW_DESCRIPTIONS = new LinkedHashMap<>();
    /** 出栏配置业务键。 */
    private static final String SLAUGHTER_AGE_KEY = "slaughter_age_days";
    private static final int SLAUGHTER_AGE_DEFAULT = 175;

    /**
     * 用药配置业务键 → 默认值（小程序 row251 / row252：把两个原先写死的阈值改成后台可配）。
     *
     * <ul>
     *   <li>{@code fatten_med_max_age_days}：育肥猪最大用药日龄。超过该日龄的育肥猪不再进疫苗药品板块
     *       的猪只列表（临近出栏不再用药，避免休药期问题）。原先无此限制，全部育肥猪都列。</li>
     *   <li>{@code med_pick_usable_days}：药品领用可用天数。用药治疗「使用药品」候选 = 近 N 天内已领用
     *       的药品；超过 N 天的领用不再出现在候选里。原先写死 3 天，客户要 15 天且可配。</li>
     * </ul>
     */
    private static final Map<String, Integer> MED_DEFAULTS = new LinkedHashMap<>();
    /** 用药配置业务键 → 说明（首次落库填 description 用）。 */
    private static final Map<String, String> MED_DESCRIPTIONS = new LinkedHashMap<>();

    /** 育肥猪最大用药日龄 key（mp 疫苗药品猪只列表过滤用，applet 端点单独透出）。 */
    public static final String FATTEN_MED_MAX_AGE_KEY = "fatten_med_max_age_days";
    /** 育肥猪最大用药日龄默认值（天）。 */
    public static final int FATTEN_MED_MAX_AGE_DEFAULT = 300;
    /** 药品领用可用天数 key（用药治疗「使用药品」候选窗口）。 */
    public static final String MED_PICK_USABLE_DAYS_KEY = "med_pick_usable_days";
    /** 药品领用可用天数默认值（天）。 */
    public static final int MED_PICK_USABLE_DAYS_DEFAULT = 15;

    static {
        SOW_DEFAULTS.put("sow_reserve_to_breed_days", 7);
        SOW_DEFAULTS.put("sow_wean_to_breed_days", 6);
        SOW_DEFAULTS.put("sow_return_to_breed_days", 5);
        SOW_DEFAULTS.put("sow_empty_to_breed_days", 5);
        SOW_DEFAULTS.put("sow_abort_to_breed_days", 5);
        SOW_DEFAULTS.put("sow_breed_to_farrow_days", 141);
        SOW_DEFAULTS.put("sow_farrow_to_wean_days", 25);

        SOW_DESCRIPTIONS.put("sow_reserve_to_breed_days", "后备到配种天数");
        SOW_DESCRIPTIONS.put("sow_wean_to_breed_days", "断奶到配种天数");
        SOW_DESCRIPTIONS.put("sow_return_to_breed_days", "返情到配种天数");
        SOW_DESCRIPTIONS.put("sow_empty_to_breed_days", "空怀到配种天数");
        SOW_DESCRIPTIONS.put("sow_abort_to_breed_days", "流产到配种天数");
        SOW_DESCRIPTIONS.put("sow_breed_to_farrow_days", "配种到分娩天数");
        SOW_DESCRIPTIONS.put("sow_farrow_to_wean_days", "分娩到断奶天数");

        MED_DEFAULTS.put(FATTEN_MED_MAX_AGE_KEY, FATTEN_MED_MAX_AGE_DEFAULT);
        MED_DEFAULTS.put(MED_PICK_USABLE_DAYS_KEY, MED_PICK_USABLE_DAYS_DEFAULT);

        MED_DESCRIPTIONS.put(FATTEN_MED_MAX_AGE_KEY, "育肥猪最大用药日龄（超过该日龄的育肥猪不进疫苗药品猪只列表）");
        MED_DESCRIPTIONS.put(MED_PICK_USABLE_DAYS_KEY, "药品领用可用天数（用药治疗「使用药品」只列近 N 天已领用的药品）");
    }

    /**
     * 用药配置：取 2 项生效值（custom 优先，无则 default 兜底）。
     * <p>DB 缺某 key（首次未保存）→ 回落 {@link #MED_DEFAULTS}，保证前端两格都有数。</p>
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/medication/get")
    public R<Map<String, Integer>> getMedicationConfig() {
        Map<String, Integer> stored = cycleConfigService.getValuesByKeys(MED_DEFAULTS.keySet());
        Map<String, Integer> result = new LinkedHashMap<>();
        MED_DEFAULTS.forEach((k, def) -> result.put(k, stored.getOrDefault(k, def)));
        return R.ok(result);
    }

    /**
     * 用药配置：保存 2 项（按 key UPSERT custom_value）。只认白名单 key，其余忽略。
     */
    @SaCheckPermission("djs:breed:production-cycle:edit")
    @Log(title = "用药配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/medication/save")
    public R<Void> saveMedicationConfig(@RequestBody Map<String, Integer> values) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        if (values != null) {
            MED_DEFAULTS.keySet().forEach(k -> {
                if (values.containsKey(k)) {
                    filtered.put(k, values.get(k));
                }
            });
        }
        // 两项都必须 ≥1：0 / 负数没有业务含义（最大用药日龄 0 = 一头育肥猪都不能用药；
        // 可用天数 0 = 使用药品候选恒空），而消费侧（PigAppletController#fattenMedMaxAge /
        // MedRecordServiceImpl#resolveMedPickUsableDays）对非正数一律回落默认值 —— 若此处放行 0，
        // 就会出现「后台明明存了 0、小程序仍按默认值跑」的两端不一致（QA 对抗验收实测复现）。
        // 前端 el-input-number 已设 :min="1"，这里是 API 直调的兜底闸。
        filtered.forEach((k, v) -> {
            if (v == null || v <= 0) {
                throw new ServiceException("「" + MED_DESCRIPTIONS.get(k) + "」必须大于 0");
            }
        });
        cycleConfigService.batchSaveValues(filtered, MED_DEFAULTS, MED_DESCRIPTIONS);
        return R.ok();
    }

    /**
     * 母猪生产配置：取 6 项生效值（custom 优先，无则 default 兜底）。
     * <p>DB 缺某 key（首次未保存）→ 回落本类 {@link #SOW_DEFAULTS} 默认值，保证前端 6 格都有数。</p>
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/sow/get")
    public R<Map<String, Integer>> getSowConfig() {
        Map<String, Integer> stored = cycleConfigService.getValuesByKeys(SOW_DEFAULTS.keySet());
        Map<String, Integer> result = new LinkedHashMap<>();
        SOW_DEFAULTS.forEach((k, def) -> result.put(k, stored.getOrDefault(k, def)));
        return R.ok(result);
    }

    /**
     * 母猪生产配置：保存 6 项（按 key UPSERT custom_value）。
     * <p>只接受已声明的 6 个 key，未知 key 静默忽略（防越权写入任意 config）。</p>
     */
    @SaCheckPermission("djs:breed:production-cycle:edit")
    @Log(title = "母猪生产配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/sow/save")
    public R<Void> saveSowConfig(@RequestBody Map<String, Integer> values) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        if (values != null) {
            SOW_DEFAULTS.keySet().forEach(k -> {
                if (values.containsKey(k)) {
                    filtered.put(k, values.get(k));
                }
            });
        }
        cycleConfigService.batchSaveValues(filtered, SOW_DEFAULTS, SOW_DESCRIPTIONS);
        return R.ok();
    }

    /**
     * 出栏配置：取出栏日龄（DB 缺 → 默认 {@value #SLAUGHTER_AGE_DEFAULT}）。
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/slaughter/get")
    public R<Map<String, Integer>> getSlaughterConfig() {
        Map<String, Integer> stored = cycleConfigService.getValuesByKeys(List.of(SLAUGHTER_AGE_KEY));
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(SLAUGHTER_AGE_KEY, stored.getOrDefault(SLAUGHTER_AGE_KEY, SLAUGHTER_AGE_DEFAULT));
        return R.ok(result);
    }

    /**
     * 出栏配置：保存出栏日龄（按 key UPSERT custom_value）。
     */
    @SaCheckPermission("djs:breed:production-cycle:edit")
    @Log(title = "出栏配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/slaughter/save")
    public R<Void> saveSlaughterConfig(@RequestBody Map<String, Integer> values) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        if (values != null && values.containsKey(SLAUGHTER_AGE_KEY)) {
            filtered.put(SLAUGHTER_AGE_KEY, values.get(SLAUGHTER_AGE_KEY));
        }
        Map<String, Integer> defaults = Map.of(SLAUGHTER_AGE_KEY, SLAUGHTER_AGE_DEFAULT);
        Map<String, String> descs = Map.of(SLAUGHTER_AGE_KEY, "出栏日龄（育肥猪达到出栏的标准日龄）");
        cycleConfigService.batchSaveValues(filtered, defaults, descs);
        return R.ok();
    }

    /**
     * 分页查询。
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/list")
    public TableDataInfo<ProductionCycleConfigVo> list(ProductionCycleConfigQuery query, PageQuery pageQuery) {
        return cycleConfigService.queryPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:breed:production-cycle:export")
    @Log(title = "生产周期配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProductionCycleConfigQuery query, HttpServletResponse response) {
        List<ProductionCycleConfigVo> list = cycleConfigService.queryList(query);
        ExcelUtil.exportExcel(list, "生产周期配置", ProductionCycleConfigVo.class, response);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:breed:production-cycle:list")
    @GetMapping("/getInfo/{id}")
    public R<ProductionCycleConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(cycleConfigService.queryById(id));
    }

    /**
     * 新增。
     */
    @SaCheckPermission("djs:breed:production-cycle:add")
    @Log(title = "生产周期配置", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody ProductionCycleConfigBo bo) {
        return toAjax(cycleConfigService.insertByBo(bo));
    }

    /**
     * 编辑（admin 实际只改 customValue）。
     */
    @SaCheckPermission("djs:breed:production-cycle:edit")
    @Log(title = "生产周期配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody ProductionCycleConfigBo bo) {
        return toAjax(cycleConfigService.updateByBo(bo));
    }

    /**
     * 软删（支持批量逗号）。
     */
    @SaCheckPermission("djs:breed:production-cycle:remove")
    @Log(title = "生产周期配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(cycleConfigService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
