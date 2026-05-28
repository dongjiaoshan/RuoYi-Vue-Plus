package org.dromara.djs.breed.farm.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.domain.vo.PenPickerVo;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序栏位 picker Controller（D9 closing Group D — PenPicker）。
 *
 * <p>专给 mp 端 {@code PenPicker} 用——工人在转移 / 引种事件选完栋舍后联动选栏位编码。
 * mp 传 {@code barnCode}（业务码），controller 先反查 barnId 再按 barn_id 过滤栏位。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/pen/list?barnCode=}
 *       返该栋舍下启用（penStatus=1）栏位。barnCode 不传 → 返空（不允许全表扫，避免工人误选跨栋舍栏位）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:breed:pen:list")}；菜单 seed 见
 * {@code V202606071540__D9-closing-applet-farrow-menu.sql}。</p>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/breed/pen")
public class PenAppletController {

    private final PenMapper penMapper;
    private final BarnMapper barnMapper;

    /**
     * 栏位 picker 列表（依赖 barnCode 过滤，必传）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>barnCode 必传——空 / 未匹配栋舍直接返空 list（不允许全表扫）</li>
     *   <li>仅返 {@code pen_status = 1}（启用）</li>
     *   <li>按 pen_code 升序（P01 / P02 工人习惯顺序）</li>
     *   <li>最多 200 条（picker 不分页）</li>
     * </ul>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:pen:list")
    @GetMapping("/list")
    public R<List<PenPickerVo>> list(@RequestParam(required = false) String barnCode) {
        if (StringUtils.isBlank(barnCode)) {
            return R.ok(List.of());
        }
        Barn barn = barnMapper.selectOne(new LambdaQueryWrapper<Barn>()
            .eq(Barn::getBarnCode, barnCode)
            .last("LIMIT 1"));
        if (barn == null) {
            return R.ok(List.of());
        }
        LambdaQueryWrapper<Pen> wrapper = new LambdaQueryWrapper<Pen>()
            .eq(Pen::getBarnId, barn.getId())
            .eq(Pen::getPenStatus, 1)
            .orderByAsc(Pen::getPenCode)
            .last("LIMIT 200");
        List<Pen> rows = penMapper.selectList(wrapper);
        List<PenPickerVo> vos = rows.stream().map(p -> {
            PenPickerVo vo = new PenPickerVo();
            vo.setId(p.getId());
            vo.setBarnId(p.getBarnId());
            vo.setPenCode(p.getPenCode());
            vo.setPenName(p.getPenName());
            vo.setPenType(p.getPenType());
            vo.setCapacity(p.getCapacity());
            vo.setUsedCount(p.getCurrentCount());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }
}
