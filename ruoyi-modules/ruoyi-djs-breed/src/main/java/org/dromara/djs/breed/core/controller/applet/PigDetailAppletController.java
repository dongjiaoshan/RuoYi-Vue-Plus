package org.dromara.djs.breed.core.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.FarrowRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.PigMedRecordMpVo;
import org.dromara.djs.breed.core.domain.vo.PigTransferMpVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序猪只详情聚合 Controller（BRD-FIX-MP-DETAIL-SPLIT-001 + DJS-FIX-6-14）。
 *
 * <p>URL 前缀 {@code /applet/pig/*}（与 {@link PigAppletController} 同前缀，detail 聚合端点单独成类
 * 避免改 PigAppletController 既有依赖）。母猪详情 6 KPI + 产仔堆叠柱 + 育肥详情谱系卡，
 * + DJS-FIX-6-14 分娩明细 / 用药 / 转移 list，read-only 实时聚合（无 DDL、不触状态机）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /applet/pig/sow-performance?pigId=} — 母猪性能 6 KPI（缺指标返 null）；</li>
 *   <li>{@code GET /applet/pig/farrow-by-parity?pigId=} — 产仔性能按胎次堆叠柱 6 系列；</li>
 *   <li>{@code GET /applet/pig/pedigree?pigId=} — 母系 / 父系谱系卡；</li>
 *   <li>{@code GET /applet/pig/farrow-records?pigId=} — 分娩记录逐条明细（母猪详情）；</li>
 *   <li>{@code GET /applet/pig/med-list?pigId=} — 本猪用药记录（母猪 / 育肥仔猪详情）；</li>
 *   <li>{@code GET /applet/pig/transfer-list?pigId=} — 本猪转移记录（育肥仔猪详情）。</li>
 * </ul>
 *
 * <h2>pigId 参数</h2>
 * <p>统一 {@code String} 接收（snowflake，前端禁 Number()），controller 内解析为 Long。
 * 非数字 / 空 → 返 null / 空集（不抛 NumberFormatException 给前端）。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:pig:search")} —— 复用 PigAppletController 同权限串。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/pig")
public class PigDetailAppletController {

    private final ISowDetailService sowDetailService;

    /**
     * 母猪性能 6 KPI（原型 母猪详情）。
     *
     * @param pigId 母猪 ID（snowflake string）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/sow-performance")
    public R<SowPerformanceMpVo> sowPerformance(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.querySowPerformance(id));
    }

    /**
     * 产仔性能按胎次堆叠柱 6 系列（原型 母猪详情·产仔性能）。
     *
     * @param pigId 母猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/farrow-by-parity")
    public R<List<FarrowByParityVo>> farrowByParity(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.queryFarrowByParity(id));
    }

    /**
     * 母系 / 父系谱系卡（原型 育肥猪详情·出生记录）。
     *
     * @param pigId 本猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/pedigree")
    public R<PedigreeVo> pedigree(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.queryPedigree(id));
    }

    /**
     * 母猪分娩记录逐条明细（DJS-FIX-6-14 row212，原型 母猪详情·分娩记录 tab）。
     *
     * @param pigId 母猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/farrow-records")
    public R<List<FarrowRecordMpVo>> farrowRecords(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.queryFarrowRecords(id));
    }

    /**
     * 本猪用药记录（DJS-FIX-6-14 row212 / row213，原型 用药记录 tab）。
     *
     * @param pigId 本猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/med-list")
    public R<List<PigMedRecordMpVo>> medList(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.queryMedRecords(id));
    }

    /**
     * 本猪转移记录（DJS-FIX-6-14 row213，原型 育肥猪详情·转移记录 tab）。
     *
     * @param pigId 本猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/transfer-list")
    public R<List<PigTransferMpVo>> transferList(@RequestParam String pigId) {
        Long id = parsePigId(pigId);
        return R.ok(sowDetailService.queryTransferRecords(id));
    }

    /** pigId 字符串 → Long（snowflake 防截断）；空 / 非数字 → null（由 service 抛"猪只不存在"）。 */
    private Long parsePigId(String pigId) {
        if (StringUtils.isBlank(pigId)) {
            return null;
        }
        try {
            return Long.valueOf(pigId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
