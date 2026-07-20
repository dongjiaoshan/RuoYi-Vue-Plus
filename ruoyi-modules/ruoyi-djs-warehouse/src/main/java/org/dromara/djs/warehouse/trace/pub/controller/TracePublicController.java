package org.dromara.djs.warehouse.trace.pub.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;
import org.dromara.djs.warehouse.trace.pub.service.ITracePublicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开追溯只读 Controller（TRC-PUB-API-001）。
 *
 * <p>顾客扫码追溯页的<b>唯一公开数据源</b>。{@code @SaIgnore} 无登录可访问（顾客扫码无登录态）。
 * 整类只读，<b>无写端点</b>（追溯链不可篡改，写码由 TRC-CORE 7 事件 hook 完成）。</p>
 *
 * <p>路径 {@code /djs/public/trace/*}（<b>不是</b> ADR-0009 的 {@code /djs/applet/*} —— 那是登录态
 * applet；本端点 public 无登录）。openid 换取顾客复用现有 {@code POST /applet/auth/wxLogin}
 * （@SaIgnore，已返 openid，不强制注册），本 controller 不另建 openid 端点。</p>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
@Slf4j
@RestController
@RequestMapping("/djs/public/trace")
@RequiredArgsConstructor
public class TracePublicController {

    private final ITracePublicService tracePublicService;

    /**
     * 公开追溯聚合（@SaIgnore 无登录）。
     *
     * <p>入参 {@code traceCode} = {@code produce_code}（小程序码 scene 带入，业务码非雪花 id）。
     * 返回 {@link PublicTraceVo}，前端按 {@code codeType}（pork/veg/gift）路由渲染。Redis ~10min 缓存。</p>
     *
     * <p>追溯码不存在 → 返回 {@code code=200 + data=null}（友好响应，前端空判提示「未查到该追溯码」），
     * 不抛 500 栈给 C 端。异常兜底同理。</p>
     *
     * @param traceCode 追溯码（= produce_code）
     * @return 聚合 VO；不存在返 data null
     */
    @SaIgnore
    @GetMapping("/{traceCode}")
    public R<PublicTraceVo> getByTraceCode(@PathVariable("traceCode") String traceCode) {
        try {
            return R.ok(tracePublicService.getByProduceCode(traceCode));
        } catch (Exception e) {
            log.warn("[trace-public] 查询追溯码失败 traceCode={}: {}", traceCode, e.getMessage());
            return R.ok((PublicTraceVo) null);
        }
    }
}
