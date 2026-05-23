package org.dromara.djs.breed.pig.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.pig.domain.query.PigInfoQuery;
import org.dromara.djs.breed.pig.domain.vo.PigInfoVo;
import org.dromara.djs.breed.pig.service.IPigInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 猪只基础信息Controller（小程序入口）（BRD-MD-004）。
 *
 * <p>小程序后台接口，提供猪只基础信息查询功能，供多个业务模块共用。</p>
 * <p>URL 前缀 {@code /djs/applet/pig/*}。</p>
 *
 * @author djs
 * @since BRD-MD-004
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/pig")
public class AppletPigInfoController {

    private final IPigInfoService pigInfoService;

    /**
     * 猪只基础信息查询接口。
     *
     * <p>根据耳号查询猪只基础信息，校验耳号合法性。</p>
     * <p>耳号不存在或状态为死亡、淘汰、出栏时返回明确错误提示。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /djs/applet/pig/query
     * { "earTag": "PIG-2024001" }
     * </pre>
     *
     * <p>响应示例：</p>
     * <pre>
     * {
     *   "id": 1001,
     *   "earTag": "PIG-2024001",
     *   "pigType": "1",
     *   "pigTypeName": "母猪",
     *   "pigSex": "0",
     *   "pigSexName": "雌性",
     *   "pigBreedName": "长白猪",
     *   "pigStrainName": "纯种",
     *   "pigAge": 180,
     *   "pigLocation": "育成舍1栋",
     *   "entryDate": "2024-01-15",
     *   "weight": 120.5
     * }
     * </pre>
     */
    @SaCheckLogin
    @PostMapping("/query")
    public R<PigInfoVo> queryPigInfo(@RequestBody PigInfoQuery query) {
        log.info("[applet-pig] 查询猪只信息 earTag={}", query.getEarTag());
        try {
            return R.ok(pigInfoService.queryByEarTag(query.getEarTag()));
        } catch (ServiceException e) {
            log.warn("[applet-pig] 查询猪只信息失败 earTag={} error={}", 
                     query.getEarTag(), e.getMessage());
            return R.fail(e.getCode() != null ? e.getCode() : 400, e.getMessage());
        }
    }

}
