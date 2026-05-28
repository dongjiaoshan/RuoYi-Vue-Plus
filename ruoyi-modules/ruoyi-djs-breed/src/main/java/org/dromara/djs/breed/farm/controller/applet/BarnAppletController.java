package org.dromara.djs.breed.farm.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.vo.BarnPickerVo;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序栋舍 picker Controller（D9 closing Group D — BarnPicker）。
 *
 * <p>专给 mp 端 {@code BarnPicker} 用——工人在转移 / 引种事件需要选目标栋舍编码，
 * 不能手输（避免拼错），picker 拉所有启用栋舍展示 barnCode + barnName + capacity / used。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/barn/list?farmId=}
 *       返启用（barnStatus=1）栋舍。V1 单农场（ADR-0001）{@code farmId} 参数预留，service
 *       层暂不按 farm_id 过滤（{@code Barn} 实体无 farm_id 列；多农场 ramp 后再加）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:breed:barn:list")}；菜单 seed 见
 * {@code V202606071540__D9-closing-applet-farrow-menu.sql}。</p>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/breed/barn")
public class BarnAppletController {

    private final BarnMapper barnMapper;

    /**
     * 栋舍 picker 列表。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>仅返 {@code barn_status = 1}（启用）</li>
     *   <li>按 barn_code 升序（B01 / B02 / B03 工人习惯顺序）</li>
     *   <li>最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param farmId V1 预留参数，service 暂不按此过滤（单农场）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:barn:list")
    @GetMapping("/list")
    public R<List<BarnPickerVo>> list(@RequestParam(required = false) Long farmId) {
        LambdaQueryWrapper<Barn> wrapper = new LambdaQueryWrapper<Barn>()
            .eq(Barn::getBarnStatus, 1)
            .orderByAsc(Barn::getBarnCode)
            .last("LIMIT 200");
        List<Barn> rows = barnMapper.selectList(wrapper);
        List<BarnPickerVo> vos = rows.stream().map(b -> {
            BarnPickerVo vo = new BarnPickerVo();
            vo.setId(b.getId());
            vo.setBarnCode(b.getBarnCode());
            vo.setBarnName(b.getBarnName());
            vo.setBarnType(b.getBarnType());
            vo.setCapacity(b.getCapacity());
            vo.setUsedCount(b.getCurrentCount());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }
}
