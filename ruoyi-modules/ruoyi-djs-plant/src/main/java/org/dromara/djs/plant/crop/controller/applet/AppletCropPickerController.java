package org.dromara.djs.plant.crop.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.vo.CropPickerVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序作物 picker Controller（MP-PICKERS-001）。
 *
 * <p>专给 mp {@code CropPicker} 用，复用 {@link CropInfoMapper#selectList} 直接查 entity 转轻量 VO，
 * 不动 admin {@code ICropInfoService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/crop/listAll?keyword=}
 *       返回全部作物，按 keyword LIKE cropName/cropCode 过滤，按 cropCode 升序最多 200 条。</li>
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
@RequestMapping("/djs/applet/plant/crop")
public class AppletCropPickerController {

    private final CropInfoMapper cropInfoMapper;

    /**
     * 作物 picker 列表。
     *
     * @param keyword 关键字（同时 LIKE cropName / cropCode），可空
     */
    @SaCheckLogin
    @GetMapping("/listAll")
    public R<List<CropPickerVo>> listAll(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<CropInfo> wrapper = new LambdaQueryWrapper<CropInfo>()
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(CropInfo::getCropName, keyword)
                .or()
                .like(CropInfo::getCropCode, keyword))
            .orderByAsc(CropInfo::getCropCode)
            .last("LIMIT 200");
        List<CropInfo> rows = cropInfoMapper.selectList(wrapper);
        List<CropPickerVo> vos = rows.stream().map(c -> {
            CropPickerVo vo = new CropPickerVo();
            vo.setId(c.getId());
            vo.setCropName(c.getCropName());
            vo.setCropCode(c.getCropCode());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

}
