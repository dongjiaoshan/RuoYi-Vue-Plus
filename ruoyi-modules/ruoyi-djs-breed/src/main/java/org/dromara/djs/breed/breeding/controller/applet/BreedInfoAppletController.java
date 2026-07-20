package org.dromara.djs.breed.breeding.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoOptionVo;
import org.dromara.djs.breed.breeding.service.IBreedInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序育种信息（品种/品系）只读 Controller（FIX-INTRO-001 #2）。
 *
 * <p>URL 前缀 {@code /applet/breed/*}，与 {@code /applet/pig/*} 同款 mp 前缀风格；admin 端
 * 继续走 {@link org.dromara.djs.breed.breeding.controller.BreedInfoController}（{@code /djs/breed/breeding/info/*}）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /applet/breed/breed-info?breedStrain=1|2} — 按 {@code breed_strain} 过滤
 *       {@code t_farm_breed_info}（1=品种 / 2=品系），返回 {@code {code, name}} 下拉选项。
 *       mp 外部引种「品种」/「品系」下拉数据源（替代旧的 {@code djs_pig_breed}/{@code djs_pig_strain} 静态字典）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin} —— 只读主数据下拉，mp 任意已登录角色可读（与 {@code /applet/pig/intro-detail}
 * 同级别只读端点一致，不挂细粒度按钮权限）。</p>
 *
 * @author djs
 * @since FIX-INTRO-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/breed")
public class BreedInfoAppletController {

    private final IBreedInfoService breedInfoService;

    /**
     * 品种/品系下拉选项（mp 外部引种用）。
     *
     * @param breedStrain 1=品种 / 2=品系（不传 → 返全部）
     * @return {@code {code, name}} 选项列表（提交存 code）
     */
    @SaCheckLogin
    @GetMapping("/breed-info")
    public R<List<BreedInfoOptionVo>> breedInfo(@RequestParam(required = false) Integer breedStrain) {
        return R.ok(breedInfoService.listOptionsByStrain(breedStrain));
    }
}
