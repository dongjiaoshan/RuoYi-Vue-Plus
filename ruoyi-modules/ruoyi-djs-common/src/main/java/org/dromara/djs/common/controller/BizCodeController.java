package org.dromara.djs.common.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务编码生成器 HTTP 入口（SYS-INFRA-004）。
 *
 * <p>主要供 admin 调试 / 业务模块跨 service 调用补位（绝大多数业务模块应直接注入
 * {@link IBizCodeGenerator} bean，不走 HTTP）。本 controller 仅暴露 admin 端，
 * 小程序端不需要直接调编码生成（小程序端的业务接口由业务模块负责，业务模块内部已 wire 本生成器）。</p>
 *
 * <p>路径：{@code /djs/common/biz-code/generate}。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Slf4j
@RestController
@RequestMapping("/djs/common/biz-code")
@RequiredArgsConstructor
@Validated
public class BizCodeController extends BaseController {

    private final IBizCodeGenerator bizCodeGenerator;

    /**
     * 单条生成业务编码（调试 / 跨模块兜底入口）。
     *
     * <p>context 字段由前端按 {@code type} 的 pattern 自行准备：</p>
     * <ul>
     *   <li>EAR_NO → {@code farmCode=01&barnCode=A1}</li>
     *   <li>TRACE_CODE → {@code productCode=PG}</li>
     *   <li>DEMAND_NO → {@code bizCode=ST}</li>
     *   <li>STOCK_FLOW_NO → {@code ioCode=IN}</li>
     *   <li>PRODUCE_NO → {@code prefix=Z}（业态前缀 Z/G/B/H/D/L）</li>
     *   <li>SHIP_NO / RETURN_NO → 无需 context</li>
     * </ul>
     * <p>{@code PACK_NO} 已废弃（{@link BizCodeType#PACK_NO}），产品生产编号改走 PRODUCE_NO。</p>
     *
     * @param type    {@link BizCodeType} 枚举名
     * @param context 额外占位符上下文（form 参数自动收集到 Map）
     * @return 生成的编码字符串
     */
    @SaCheckPermission("djs:common:bizcode:generate")
    @GetMapping("/generate")
    public R<String> generate(@RequestParam @NotBlank String type,
                              @RequestParam(required = false) Map<String, Object> context) {
        BizCodeType bizType = BizCodeType.valueOf(type);
        Map<String, Object> safeContext = context == null ? new HashMap<>() : context;
        // 移除 type 字段自身（Spring MVC 把所有 query 参数都塞进 Map，需剔除）
        safeContext.remove("type");
        return R.ok(bizCodeGenerator.generate(bizType, safeContext));
    }

    /**
     * 批量生成 N 个连续编码。
     *
     * @param type    编码类型
     * @param count   批量数量；&gt; 0
     * @param context 占位符上下文
     */
    @SaCheckPermission("djs:common:bizcode:generate")
    @GetMapping("/generate-batch")
    public R<List<String>> generateBatch(@RequestParam @NotBlank String type,
                                         @RequestParam @Min(1) int count,
                                         @RequestParam(required = false) Map<String, Object> context) {
        BizCodeType bizType = BizCodeType.valueOf(type);
        Map<String, Object> safeContext = context == null ? new HashMap<>() : context;
        safeContext.remove("type");
        safeContext.remove("count");
        return R.ok(bizCodeGenerator.generateBatch(bizType, safeContext, count));
    }
}
