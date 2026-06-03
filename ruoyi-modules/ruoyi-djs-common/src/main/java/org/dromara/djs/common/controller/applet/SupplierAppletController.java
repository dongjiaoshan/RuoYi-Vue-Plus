package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.supplier.domain.query.SupplierQuery;
import org.dromara.djs.common.supplier.domain.vo.SupplierPickerVo;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;
import org.dromara.djs.common.supplier.service.ISupplierService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序供应商选择器 Controller（D12X-MP-INTRO-001）。
 *
 * <p>path 走 ADR-0009 新范式 {@code /djs/applet/<module>/<biz>}：供应商属通用主数据，
 * module=common，biz=supplier，与 {@link EmployeeAppletController} 同范式。供 mp
 * SupplierPicker 选种猪供应商写进外部引种 supplierCode 字段（替代手输 input + 兜底报错）。</p>
 *
 * <h2>端点</h2>
 *
 * <ul>
 *   <li>{@code GET /djs/applet/common/supplier/list} — 列可选供应商（按 supplierType 过滤）</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 *
 * <p>{@code @SaCheckLogin}：登录后才点 picker，任何登录工人都能查同租户可选供应商（V1 单农场全员可见）。
 * 不挂独立 perm（照搬 EmployeeAppletController，picker 查询挂在已有业务页登录态下，0 perm seed）。
 * 不用 {@code @SaIgnore}（这不是 onLaunch 前置端点）。</p>
 *
 * @author djs
 * @since D12X-MP-INTRO-001
 */
@Slf4j
@RestController
@RequestMapping("/djs/applet/common/supplier")
@RequiredArgsConstructor
public class SupplierAppletController {

    private final ISupplierService supplierService;

    /**
     * 供应商列表查询。
     *
     * <p>请求示例：</p>
     * <pre>
     * GET /djs/applet/common/supplier/list?supplierType=breed         # 仅种猪类供应商
     * GET /djs/applet/common/supplier/list?supplierType=breed&keyword=正大  # 按名称模糊
     * </pre>
     *
     * <p>响应是轻量 {@link SupplierPickerVo}（supplierCode / supplierName / supplierType），
     * 不回传 bank / license 等冗余敏感字段。mp 选中后把 supplierCode 作为业务字段提交。</p>
     *
     * @param supplierType 可选供应商类型（字典 djs_supplier_type，精确匹配；如 {@code breed}）
     * @param keyword      可选关键字（按 supplierName 模糊）
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<SupplierPickerVo>> list(@RequestParam(required = false) String supplierType,
                                          @RequestParam(required = false) String keyword) {
        SupplierQuery query = new SupplierQuery();
        query.setSupplierType(supplierType);
        query.setSupplierName(keyword);
        List<SupplierVo> suppliers = supplierService.queryList(query);
        List<SupplierPickerVo> rows = suppliers.stream().map(this::toPickerVo).toList();
        return R.ok(rows);
    }

    private SupplierPickerVo toPickerVo(SupplierVo s) {
        SupplierPickerVo vo = new SupplierPickerVo();
        vo.setSupplierCode(s.getSupplierCode());
        vo.setSupplierName(s.getSupplierName());
        vo.setSupplierType(s.getSupplierType());
        return vo;
    }
}
