package org.dromara.djs.plant.plot.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.domain.vo.PlotPickerVo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序地块 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code PlotPicker} 用，复用 {@link PlotInfoMapper#selectList} 直接查 entity 转轻量 VO，
 * 不动 admin {@code IPlotInfoService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/plot/listAll?keyword=&zoneId=}
 *       返回全部地块，按 keyword LIKE plotName/plotCode 过滤、zoneId 非空时按片区过滤，
 *       按 plotCode 升序最多 200 条。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（worker 角色可用，不加 @SaCheckPermission）。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/plant/plot")
public class AppletPlotPickerController {

    private final PlotInfoMapper plotInfoMapper;

    /**
     * 地块 picker 列表。
     *
     * @param keyword 关键字（同时 LIKE plotName / plotCode），可空
     * @param zoneId  片区 ID（非空时按片区过滤地块），可空
     */
    @SaCheckLogin
    @GetMapping("/listAll")
    public R<List<PlotPickerVo>> listAll(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long zoneId) {
        LambdaQueryWrapper<PlotInfo> wrapper = new LambdaQueryWrapper<PlotInfo>()
            .eq(zoneId != null, PlotInfo::getZoneId, zoneId)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(PlotInfo::getPlotName, keyword)
                .or()
                .like(PlotInfo::getPlotCode, keyword))
            .orderByAsc(PlotInfo::getPlotCode)
            .last("LIMIT 200");
        List<PlotInfo> rows = plotInfoMapper.selectList(wrapper);
        List<PlotPickerVo> vos = rows.stream().map(p -> {
            PlotPickerVo vo = new PlotPickerVo();
            vo.setId(p.getId());
            vo.setPlotCode(p.getPlotCode());
            vo.setPlotName(p.getPlotName());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
