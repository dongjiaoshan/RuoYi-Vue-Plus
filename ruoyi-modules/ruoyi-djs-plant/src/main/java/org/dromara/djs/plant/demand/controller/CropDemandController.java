package org.dromara.djs.plant.demand.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.demand.domain.bo.CropDemandBo;
import org.dromara.djs.plant.demand.domain.bo.CropDemandReplyBo;
import org.dromara.djs.plant.demand.domain.query.CropDemandQuery;
import org.dromara.djs.plant.demand.domain.vo.CropDemandVo;
import org.dromara.djs.plant.demand.service.ICropDemandService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 作物需求 Controller（V6-R152 运营端「作物需求」 + V6-R153 种植端「需求反馈」共用）。
 *
 * <p>URL 前缀 {@code /djs/plant/cropDemand}；菜单 + 按钮权限 seed 见 V202609010400
 * （menu_id 12040-12043 运营端 / 8300-8302 种植端）。</p>
 *
 * <p>list / getInfo 两端共用，走 {@link SaMode#OR} 双权限（同 StoreReturnController 先例）。</p>
 *
 * @author djs
 * @since V6-R152
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/cropDemand")
public class CropDemandController extends BaseController {

    private final ICropDemandService cropDemandService;

    /** 分页查询（运营端「作物需求」与种植端「需求反馈」共用）。 */
    @SaCheckPermission(value = {"djs:ops:cropDemand:list", "djs:plant:demandFeedback:list"}, mode = SaMode.OR)
    @GetMapping("/list")
    public TableDataInfo<CropDemandVo> list(CropDemandQuery query, PageQuery pageQuery) {
        return cropDemandService.queryPageList(query, pageQuery);
    }

    /** 详情（查看详情弹窗 / 回复弹窗共用）。 */
    @SaCheckPermission(value = {"djs:ops:cropDemand:list", "djs:plant:demandFeedback:list"}, mode = SaMode.OR)
    @GetMapping("/getInfo/{id}")
    public R<CropDemandVo> getInfo(@PathVariable Long id) {
        return R.ok(cropDemandService.queryById(id));
    }

    /** 新增需求（运营端）。 */
    @SaCheckPermission("djs:ops:cropDemand:add")
    @Log(title = "种植-作物需求", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody CropDemandBo bo) {
        return toAjax(cropDemandService.insertByBo(bo));
    }

    /** 删除需求（只有创建人本人能删，服务端校验）。 */
    @SaCheckPermission("djs:ops:cropDemand:remove")
    @Log(title = "种植-作物需求", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(cropDemandService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /** 回复需求（种植端；已回复的可继续改回复内容）。 */
    @SaCheckPermission("djs:plant:demandFeedback:reply")
    @Log(title = "种植-需求反馈", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/reply")
    public R<Void> reply(@Validated @RequestBody CropDemandReplyBo bo) {
        return toAjax(cropDemandService.reply(bo));
    }
}
