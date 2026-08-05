package org.dromara.djs.breed.event.intro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroInternalBo;
import org.dromara.djs.breed.event.intro.domain.query.PigIntroQuery;
import org.dromara.djs.breed.event.intro.domain.vo.IntroRecordVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroduceVo;
import org.dromara.djs.breed.event.intro.service.IPigIntroService;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 引种 Controller（BRD-EVENT-001）。
 *
 * <p>两端点：</p>
 * <ul>
 *   <li>{@code POST /intro}        单头引种</li>
 *   <li>{@code POST /intro/batch}  批量引种（≥ 2 头）</li>
 * </ul>
 *
 * <p>所有端点都加 {@code @RepeatSubmit} 防止小程序双击造成的重复 INSERT（同 token 防重 token bucket）。
 * 真正的业务级幂等（同 introduce_no 不重复入库）由 UNIQUE 约束兜底（DDL t_farm_pig_introduce.introduce_no
 * 隐含唯一）+ BizCodeGenerator 同时刻不会发同号。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event")
public class PigIntroController extends BaseController {

    private final IPigIntroService introService;

    /** admin 只读列表 — 历史引种记录分页查询（mp 不调用）。 */
    @SaCheckPermission("djs:breed:event:intro:list")
    @GetMapping("/intro/list")
    public TableDataInfo<PigIntroduceVo> list(PigIntroQuery query, PageQuery pageQuery) {
        return introService.queryPage(query, pageQuery);
    }

    /** 单头引种。 */
    @SaCheckPermission("djs:breed:event:intro")
    @Log(title = "引种登记", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/intro")
    public R<PigIntroResultVo> introduce(@Validated @RequestBody PigIntroBo bo) {
        return R.ok(introService.introduce(bo));
    }

    /** 批量引种（pigCount ≥ 2）。 */
    @SaCheckPermission("djs:breed:event:intro")
    @Log(title = "批量引种登记", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/intro/batch")
    public R<PigIntroResultVo> introduceBatch(@Validated @RequestBody PigIntroBatchBo bo) {
        return R.ok(introService.introduceBatch(bo));
    }

    /**
     * 内部引种（BRD-FIX-MP-INTRO-001）：登记一头已存在的猪进引种台账，不新建猪。
     *
     * <p>mp 端「内部引种」segment 提交用——查耳号 auto-fill 后填引种日期/体重/人员提交。</p>
     */
    @SaCheckPermission("djs:breed:event:intro")
    @Log(title = "内部引种登记", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/intro/internal")
    public R<PigIntroResultVo> introduceInternal(@Validated @RequestBody PigIntroInternalBo bo) {
        return R.ok(introService.introduceInternal(bo));
    }

    /**
     * mp 端「引种记录」列表（BRD-FIX-MP-INTRO-001，原型 86 第 3 段）。
     *
     * <p>按 introduceDate DESC 返轻量 {@link IntroRecordVo}（含引种方式 label + 耳号 + 品种 label）。
     * 权限走 mp 通用只读串 {@code djs:applet:pig:search}（mp role 默认含，复用免新增菜单 seed）。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/intro/applet-records")
    public TableDataInfo<IntroRecordVo> appletRecords(PigIntroQuery query, PageQuery pageQuery) {
        return introService.queryAppletRecords(query, pageQuery);
    }

    /**
     * 预生成"下一个可用首耳号"（外部引种可改耳号预填用，601-5 / ADR-0011 §2.6）。
     *
     * <p>按品系 + 品种 + 公母 + 出生日组装带分隔符前缀（品系1-品种2-公母1-yyMMdd6，如 1-01-1-260609），取当天级 max+1 拼出首号（如 1-01-1-260609-0001）返给前端预填，不落库。
     * 权限走 mp 通用只读串 {@code djs:applet:pig:search}（复用免新增菜单 seed）。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/intro/preview-earno")
    public R<String> previewEarNo(@RequestParam String strainCode,
                                  @RequestParam String breedCode,
                                  @RequestParam String pigSex,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate) {
        // ⚠️ R.ok(String) 会命中 R.ok(String msg) 重载 → 值进 msg、data=null（前端读 data 拿空）。
        // 显式走 R.ok(msg, data) 把耳号放进 data，前端才能拿到预填值。
        return R.ok("操作成功", introService.previewNextEarNo(strainCode, breedCode, pigSex, birthDate));
    }

    /**
     * 耳号占用查询（外部引种 mp 端输入即查重，甲方 mp row271）。
     *
     * <p>返 {@code true} = 这个耳号（整串）已被占用，与提交时查重同口径。
     * 权限走 mp 通用只读串 {@code djs:applet:pig:search}（复用免新增菜单 seed）。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/intro/earno-taken")
    public R<Boolean> earNoTaken(@RequestParam String earNo) {
        return R.ok(introService.isEarNoTaken(earNo));
    }
}
