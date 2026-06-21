package org.dromara.djs.breed.core.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.domain.vo.PigBarnCountVo;
import org.dromara.djs.breed.core.domain.vo.PigIntroDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序猪只 Controller（BRD-LIST-001 D7 carryover #1）。
 *
 * <p>URL 前缀 {@code /applet/pig/*}，与现有 applet 系列（{@code /applet/user/*} / {@code /applet/auth/*}）
 * 保持小程序统一前缀风格——admin 端继续走 {@code /djs/breed/pig/*}（{@link
 * org.dromara.djs.breed.core.controller.PigController}）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /applet/pig/search} — 耳号关键字 + 状态/性别/类型/栋舍过滤的轻量搜索，给 mp 端
 *       PigPicker / PigSelectPanel 组件用（让事件表单从"手输 earNo 兜底"升级到 picker 选猪）。</li>
 *   <li>{@code GET /applet/pig/barn-count} — 栋舍 × 头数聚合，给 PigSelectPanel 顶部「栋舍 chip」
 *       快筛行用（BRD-FIX-MP-PIGSELECT-001）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:pig:search")} —— mp role 默认含；admin role 也含，便于
 * 联调时 admin 端调试 picker 行为。</p>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/pig")
public class PigAppletController {

    private final IPigCoreService pigCoreService;

    private final IPigQueryService pigQueryService;

    private final IProductionCycleConfigService cycleConfigService;

    /**
     * 耳号关键字搜索（picker 用）。
     *
     * <p>语义：默认排除 END 猪只（给事件录入 picker 用，END 不能再触发配种/转栏等事件）；
     *   但当 {@code statusFilter} 显式包含 {@code "END"}（如 WMS-PIG-001 燎毛工序选已出栏猪）时放行。
     *   其他过滤都是可选的，都不传时返最近 N 条非 END。</p>
     *
     * @param earNoKeyword  耳号 LIKE 中部匹配；空 → 不过滤
     * @param statusFilter  状态 CSV（如 {@code "HB,DN,LC,KH,FQ"} 给配种 picker / {@code "END"} 给燎毛 picker）
     * @param sexFilter     {@code "M"} / {@code "F"}；castrate 表单调用时强制 {@code "M"}
     * @param pigTypeFilter {@code "sow"/"boar"/"piglet"/"fattening"}
     * @param barnCode      栋舍编码精确过滤（PigSelectPanel 栋舍 chip 点击后传；空 → 不限栋舍）
     * @param limit         1-100，默认 20
     * @param dueType       到期窗口过滤（{@code FARROW}=已到产期 / {@code WEANING}=已到断奶期；
     *                      空 → 不做日期过滤。分娩 / 断奶 mp 选猪传，按生产周期配置天数 + 基准日期判定）
     * @param excludeNullBarn 是否排除无栋舍归属（{@code barn_id} 为 null）的猪只（FIX-BREEDING-001 #23a）。
     *                        {@code true} → 列表口径与 {@code /barn-count} chip 一致；配种选猪传 true，
     *                        其余调用方不传（默认 false）行为不变。
     * @param minAgeDays    最小日龄过滤（天）：仅返回 {@code 日龄 >= minAgeDays} 的猪只（出栏选猪传出栏
     *                      配置 {@code slaughter_age_days}，只列到龄肥猪）。{@code null}/≤0 → 不过滤。
     * @param isCastrated   是否阉割过滤（{@code 1}=否 / {@code 2}=是）：阉割选猪传 {@code 1}（仅未阉割猪可选）。
     *                      {@code null} → 不过滤（向后兼容所有现有调用方）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/search")
    public R<List<PigSearchVo>> search(
        @RequestParam(required = false) String earNoKeyword,
        @RequestParam(required = false) String statusFilter,
        @RequestParam(required = false) String sexFilter,
        @RequestParam(required = false) String pigTypeFilter,
        @RequestParam(required = false) String barnCode,
        @RequestParam(required = false, defaultValue = "20") Integer limit,
        @RequestParam(required = false) String dueType,
        @RequestParam(required = false) Boolean excludeNullBarn,
        @RequestParam(required = false) Integer minAgeDays,
        @RequestParam(required = false) Integer isCastrated,
        @RequestParam(required = false) Boolean breedReady
    ) {
        return R.ok(pigCoreService.searchByEarKeyword(earNoKeyword, statusFilter, sexFilter, pigTypeFilter, barnCode, limit, dueType, excludeNullBarn, minAgeDays, isCastrated, breedReady));
    }

    /**
     * 出栏日龄配置（mp 出栏选猪用）。
     *
     * <p>返回出栏配置 {@code slaughter_age_days} 的生效值（custom 优先，无则 default 175）。mp 出栏录入页
     * onMounted 调本端点拿到日龄阈值后，调 {@code /search?minAgeDays=&pigTypeFilter=fattening}
     * 只列到龄育肥猪。复用 {@code djs:applet:pig:search} 权限（mp 默认含），不新引 production-cycle 权限。</p>
     *
     * @return 出栏日龄阈值（天）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/slaughter-age")
    public R<Integer> slaughterAge() {
        Integer days = cycleConfigService.getValue("slaughter_age_days");
        return R.ok(days != null ? days : 175);
    }

    /**
     * 栋舍 × 头数聚合（PigSelectPanel 顶部栋舍 chip 快筛用，BRD-FIX-MP-PIGSELECT-001）。
     *
     * <p>语义：在 statusFilter（默认排除 END） + sexFilter + pigTypeFilter 过滤维度下，
     * 按 barn_id 分组 count，enrich barnCode/barnName 返回。无栋舍归属（barn_id 为 null）的
     * 猪只不计入；count 为 0 的栋舍不返回；按 barnCode 升序。</p>
     *
     * @param statusFilter  状态 CSV（与 search 同语义；含 END 放行终态）
     * @param sexFilter     {@code "M"} / {@code "F"}
     * @param pigTypeFilter {@code "sow"/"boar"/"piglet"/"fattening"}
     * @param earNoKeyword  耳号关键字（row60：栋舍 chip 头数随搜索缩减，与 search 同 LIKE 口径）；空 → 不过滤
     * @param breedReady    配种选猪过滤（row13）：{@code true} 时与 search 同口径按最小在场天数剔除未达天数母猪；空 → 不过滤
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/barn-count")
    public R<List<PigBarnCountVo>> barnCount(
        @RequestParam(required = false) String statusFilter,
        @RequestParam(required = false) String sexFilter,
        @RequestParam(required = false) String pigTypeFilter,
        @RequestParam(required = false) String earNoKeyword,
        @RequestParam(required = false) Boolean breedReady
    ) {
        return R.ok(pigCoreService.countByBarn(statusFilter, sexFilter, pigTypeFilter, earNoKeyword, breedReady));
    }

    /**
     * 内部引种 auto-fill 单头查猪明细（BRD-FIX-MP-INTRO-001）。
     *
     * <p>mp 端「内部引种」segment 查耳号选中后，用拿到的 pigId 调本端点带出只读「猪只信息」卡：
     * 性别 label / 品种 label / 品系 label / 日龄 / 当前位置（栋舍名+栏位名）。所有 label 已在
     * service 层翻好，mp 端直接展示，不显原始 code。</p>
     *
     * @param pigId 猪只 ID（mp 端 picker 选中后回填，snowflake string，后端反序列化为 Long）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/intro-detail")
    public R<PigIntroDetailVo> introDetail(@RequestParam Long pigId) {
        return R.ok(pigCoreService.queryIntroDetail(pigId));
    }

    /**
     * 出栏可用猪只列表（WMS-DEMAND-002 mp 调度白条选猪 PigPicker 用）。
     *
     * <p>复用 admin 端「指定猪只」对话框同款 {@link IPigQueryService#listAvailableForOutbound}
     * ——「出栏可用」语义 {@code pig_type='fattening' AND current_status != 'END' AND 未被非取消态需求占用}。
     * mp 调度员为白条需求挑猪时多选，选中后调 {@code /djs/applet/warehouse/demand/{id}/assign-pig} 提交耳号清单。</p>
     *
     * <p>返回字段含 {@code lastBackfat}（最新背膘 mm，来自 {@code t_farm_pig_growth}）——支持业务备注
     * "背膘不要太厚" 的人工判断；mp 端按背膘/性别筛选在前端做（数据已全量返回该页）。</p>
     *
     * <p>{@code storeId} / {@code productType} 入参 V1 仅作占位接收——白条可出栏猪不绑定门店/业态
     * （仓库统一调度池），实际过滤在 mp 前端按背膘/性别。V1.x 如需按门店预约切池再下沉到 SQL。</p>
     *
     * @param storeId     预约门店 ID（V1 占位，不参与过滤）
     * @param productType 业态（V1 占位，不参与过滤）
     * @param pageQuery   分页（pageNum/pageSize；mp 端默认 pageSize=50）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/available")
    public TableDataInfo<PigAvailableVo> available(
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) String productType,
        PageQuery pageQuery
    ) {
        return pigQueryService.listAvailableForOutbound(pageQuery);
    }
}
