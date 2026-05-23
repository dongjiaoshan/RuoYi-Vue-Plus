package org.dromara.djs.breed.death.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.death.domain.bo.PigDeathBo;
import org.dromara.djs.breed.death.domain.query.PigDeathQuery;
import org.dromara.djs.breed.death.domain.vo.PigDeathVo;
import org.dromara.djs.breed.death.service.IPigDeathService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 猪只死亡模块Controller（小程序入口）（BRD-MD-003）。
 *
 * <p>小程序后台接口，需要小程序身份校验。</p>
 * <p>URL 前缀 {@code /djs/applet/death/*}，与 admin 端 {@code /djs/*} 路径分离。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/death")
public class AppletPigDeathController extends BaseController {

    private final IPigDeathService pigDeathService;

    /**
     * 猪只死亡信息提交接口。
     *
     * <p>提交猪只死亡信息，更新猪只状态为死亡。</p>
     * <p>需校验猪只不能是已淘汰或者已出栏。</p>
     */
    @SaCheckLogin
    @PostMapping("/submit")
    public R<Void> submitDeathInfo(@Validated @RequestBody PigDeathBo bo) {
        log.info("[applet-death] 提交死亡信息 pigId={} earNo={}", bo.getPigId(), bo.getEarNo());
        try {
            int result = pigDeathService.submitDeathInfo(bo);
            if (result > 0) {
                log.info("[applet-death] 死亡信息提交成功 pigId={}", bo.getPigId());
            }
            return toAjax(result);
        } catch (ServiceException e) {
            log.warn("[applet-death] 死亡信息提交失败 pigId={} error={}",
                    bo.getPigId(), e.getMessage());
            return R.fail(e.getCode() != null ? e.getCode() : 400, e.getMessage());
        }
    }

    /**
     * 死亡记录列表查询接口。
     *
     * <p>支持按日期、猪只类型、死亡原因筛选，支持分页查询。</p>
     */
    @SaCheckLogin
    @PostMapping("/list")
    public TableDataInfo<PigDeathVo> list(@RequestBody PigDeathQuery query, PageQuery pageQuery) {
        return pigDeathService.queryPageList(query, pageQuery);
    }

    /**
     * 查询死亡记录详情。
     */
    @SaCheckLogin
    @GetMapping("/getInfo/{id}")
    public R<PigDeathVo> getInfo(@PathVariable Long id) {
        return R.ok(pigDeathService.queryById(id));
    }

    /**
     * 删除死亡记录（软删）。
     */
    @SaCheckLogin
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        log.info("[applet-death] 删除死亡记录 ids={}", Arrays.toString(ids));
        return toAjax(pigDeathService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
