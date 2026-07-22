package org.dromara.djs.warehouse.location.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序库位 picker Controller（MP-UX-002）。
 *
 * <p>专给 mp {@code LocationPicker} 用，复用 {@link LocationInfoMapper#selectList}
 * 直接查 entity 转轻量 VO，不动 admin {@link org.dromara.djs.warehouse.location.service.ILocationInfoService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/warehouse/location/list?locationType=&keyword=}
 *       仅返启用（locationStatus=1）库位；按 keyword 同时 LIKE locationName/locationCode。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin} + {@code @SaCheckPermission("djs:applet:warehouse:location:list")}
 * mp_role 默认含；菜单 seed 见 {@code V202606061100__MP-UX-002-applet-permission-menus.sql}。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/warehouse/location")
public class LocationAppletController {

    private final LocationInfoMapper locationInfoMapper;

    /**
     * 库位 picker 列表。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>仅返 {@code location_status = 1}（启用）；停用库位不允许工人选</li>
     *   <li>{@code locationType} 精确匹配（业务页面常锁某类，如燎毛白条入库位走 {@code frozen}）；
     *       多类型筛选（如白条库 + 冻品库）由前端 LocationPicker 逐类型查询后合并，本端点只接单值</li>
     *   <li>{@code keyword} 同时 LIKE locationName / locationCode</li>
     *   <li>结果按 ID 倒序，最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param locationType 字典 {@code djs_location_type} 精确匹配
     * @param keyword      关键字（同时 LIKE locationName / locationCode）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:location:list")
    @GetMapping("/list")
    public R<List<LocationPickerVo>> list(
        @RequestParam(required = false) String locationType,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<LocationInfo> wrapper = new LambdaQueryWrapper<LocationInfo>()
            .eq(LocationInfo::getLocationStatus, 1)
            .eq(StringUtils.isNotBlank(locationType), LocationInfo::getLocationType, locationType)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(LocationInfo::getLocationName, keyword)
                .or()
                .like(LocationInfo::getLocationCode, keyword))
            .orderByDesc(LocationInfo::getId)
            .last("LIMIT 200");
        List<LocationInfo> rows = locationInfoMapper.selectList(wrapper);
        List<LocationPickerVo> vos = rows.stream().map(l -> {
            LocationPickerVo vo = new LocationPickerVo();
            vo.setId(l.getId());
            vo.setLocationCode(l.getLocationCode());
            vo.setLocationName(l.getLocationName());
            vo.setLocationType(l.getLocationType());
            vo.setLocationSort(l.getLocationSort());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
