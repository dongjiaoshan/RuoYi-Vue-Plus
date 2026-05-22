package org.dromara.djs.breed.event.intro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.service.IPigIntroService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
