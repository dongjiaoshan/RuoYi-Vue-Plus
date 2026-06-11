package org.dromara.djs.common.image.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.image.api.ImageRematchProvider;
import org.dromara.djs.common.image.domain.bo.ImageLibraryBo;
import org.dromara.djs.common.image.domain.query.ImageLibraryQuery;
import org.dromara.djs.common.image.domain.vo.ImageLibraryVo;
import org.dromara.djs.common.image.service.IImageLibraryService;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共图片库 Controller（IMG-LIB-001）。
 *
 * <p>CRUD（5 端点）+ 批量重新匹配。权限串 {@code djs:common:image:{list,add,edit,remove,export,rematch}}。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/image")
public class ImageLibraryController extends BaseController {

    private final IImageLibraryService imageLibraryService;

    /**
     * 主数据图片重新匹配 provider 集合（plant / warehouse 各注册一个，零循环依赖）。
     */
    private final ObjectProvider<ImageRematchProvider> rematchProviders;

    /**
     * 分页查询图库列表。
     */
    @SaCheckPermission("djs:common:image:list")
    @GetMapping("/list")
    public TableDataInfo<ImageLibraryVo> list(ImageLibraryQuery query, PageQuery pageQuery) {
        return imageLibraryService.queryPageList(query, pageQuery);
    }

    /**
     * 导出图库列表。
     */
    @SaCheckPermission("djs:common:image:export")
    @Log(title = "公共图库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ImageLibraryQuery query, HttpServletResponse response) {
        List<ImageLibraryVo> list = imageLibraryService.queryList(query);
        ExcelUtil.exportExcel(list, "公共图库", ImageLibraryVo.class, response);
    }

    /**
     * 根据 ID 查询图库详情。
     */
    @SaCheckPermission("djs:common:image:list")
    @GetMapping("/getInfo/{id}")
    public R<ImageLibraryVo> getInfo(@PathVariable Long id) {
        return R.ok(imageLibraryService.queryById(id));
    }

    /**
     * 新增图库。
     */
    @SaCheckPermission("djs:common:image:add")
    @Log(title = "公共图库", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody ImageLibraryBo bo) {
        return toAjax(imageLibraryService.insertByBo(bo));
    }

    /**
     * 编辑图库。
     */
    @SaCheckPermission("djs:common:image:edit")
    @Log(title = "公共图库", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody ImageLibraryBo bo) {
        return toAjax(imageLibraryService.updateByBo(bo));
    }

    /**
     * 删除图库（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:common:image:remove")
    @Log(title = "公共图库", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(imageLibraryService.deleteByIds(Arrays.asList(ids)));
    }

    /**
     * 批量重新匹配：对所有 {@code image_source=0} 的作物 / 商品主数据按 name 重跑匹配，更新 image_oss_id。
     *
     * <p>覆盖：seed 灌入后字段空 / 图库换图 / 新增图库回填。返回各域更新条数。</p>
     */
    @SaCheckPermission("djs:common:image:rematch")
    @Log(title = "公共图库重新匹配", businessType = BusinessType.UPDATE)
    @PostMapping("/rematch")
    public R<Map<String, Integer>> rematch() {
        // 先确保匹配缓存是最新的
        imageLibraryService.reload();
        Map<String, Integer> result = new LinkedHashMap<>();
        rematchProviders.forEach(p -> result.put(p.domainName(), p.rematchAll()));
        return R.ok(result);
    }

}
