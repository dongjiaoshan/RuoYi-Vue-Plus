package org.dromara.djs.plant.team.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.team.domain.bo.PlantWorkPeopleBatchBo;
import org.dromara.djs.plant.team.domain.bo.PlantWorkTeamBo;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.CandidateUserVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkPeopleVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;
import org.dromara.djs.plant.team.service.IPlantWorkPeopleService;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
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
import java.util.List;

/**
 * 种植班组 Controller（PLT-MD-002）。
 *
 * <p>菜单 + 按钮权限 seed 见 {@code V202606060901__PLT-MD-002-menu.sql}（menu_id 8030-8034）。
 * URL 前缀 {@code /djs/plant/team}。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/team")
public class PlantWorkTeamController extends BaseController {

    private final IPlantWorkTeamService teamService;
    private final IPlantWorkPeopleService peopleService;

    // -------- 班组 CRUD --------

    /** 分页查询班组列表。 */
    @SaCheckPermission("djs:plant:team:list")
    @GetMapping("/list")
    public TableDataInfo<PlantWorkTeamVo> list(PlantWorkTeamQuery query, PageQuery pageQuery) {
        return teamService.queryPageList(query, pageQuery);
    }

    /** 列表（不分页，给下游 ticket / 表单下拉用）。 */
    @SaCheckPermission("djs:plant:team:list")
    @GetMapping("/listAll")
    public R<List<PlantWorkTeamVo>> listAll(PlantWorkTeamQuery query) {
        return R.ok(teamService.queryList(query));
    }

    /** 班组详情。 */
    @SaCheckPermission("djs:plant:team:list")
    @GetMapping("/getInfo/{id}")
    public R<PlantWorkTeamVo> getInfo(@PathVariable Long id) {
        return R.ok(teamService.queryById(id));
    }

    /** 新增班组。 */
    @SaCheckPermission("djs:plant:team:add")
    @Log(title = "种植-班组", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody PlantWorkTeamBo bo) {
        return toAjax(teamService.insertByBo(bo));
    }

    /** 修改班组（leader_id 走 /setLeader 端点）。 */
    @SaCheckPermission("djs:plant:team:edit")
    @Log(title = "种植-班组", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PlantWorkTeamBo bo) {
        return toAjax(teamService.updateByBo(bo));
    }

    /** 软删班组（支持批量；下有成员则拒绝）。 */
    @SaCheckPermission("djs:plant:team:remove")
    @Log(title = "种植-班组", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(teamService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /** 导出班组列表。 */
    @SaCheckPermission("djs:plant:team:list")
    @Log(title = "种植-班组", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlantWorkTeamQuery query, HttpServletResponse response) {
        List<PlantWorkTeamVo> list = teamService.queryList(query);
        ExcelUtil.exportExcel(list, "班组数据", PlantWorkTeamVo.class, response);
    }

    // -------- 成员管理 --------

    /** 查询班组成员列表。 */
    @SaCheckPermission("djs:plant:team:member")
    @GetMapping("/{teamId}/members")
    public R<List<PlantWorkPeopleVo>> members(@PathVariable Long teamId) {
        return R.ok(peopleService.listByTeamId(teamId));
    }

    /** 批量加入班组成员。 */
    @SaCheckPermission("djs:plant:team:member")
    @Log(title = "种植-班组成员", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/{teamId}/members")
    public R<Void> addMembers(@PathVariable Long teamId, @Valid @RequestBody PlantWorkPeopleBatchBo bo) {
        // path 中 teamId 优先，覆盖 body
        bo.setTeamId(teamId);
        return toAjax(peopleService.batchAddMembers(bo));
    }

    /** 调出单个班组成员。 */
    @SaCheckPermission("djs:plant:team:member")
    @Log(title = "种植-班组成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{teamId}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        return toAjax(peopleService.removeMember(teamId, userId));
    }

    /** 设置班组负责人（leader_id 与 is_leader 双写）。 */
    @SaCheckPermission("djs:plant:team:edit")
    @Log(title = "种植-班组", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{teamId}/leader/{userId}")
    public R<Void> setLeader(@PathVariable Long teamId, @PathVariable Long userId) {
        return toAjax(teamService.setLeader(teamId, userId));
    }

    /** 候选员工列表（dept_id=种植部 + 未在任何班组）。 */
    @SaCheckPermission("djs:plant:team:member")
    @GetMapping("/candidate-users")
    public R<List<CandidateUserVo>> candidateUsers() {
        return R.ok(peopleService.listCandidateUsers());
    }
}
