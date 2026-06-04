package org.dromara.djs.store.member.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.member.domain.bo.StoreMemberBo;
import org.dromara.djs.store.member.domain.query.StoreMemberQuery;
import org.dromara.djs.store.member.domain.vo.StoreMemberStatsVo;
import org.dromara.djs.store.member.domain.vo.StoreMemberVo;

import java.util.Collection;
import java.util.List;

/**
 * 会员档案 Service（STR-MEMBER-001）。
 *
 * @author djs
 * @since STR-MEMBER-001
 */
public interface IStoreMemberService {

    /**
     * 会员档案分页（按手机号 / 姓名 / 等级 / 门店筛选）。
     */
    TableDataInfo<StoreMemberVo> queryPageList(StoreMemberQuery query, PageQuery pageQuery);

    /**
     * 会员档案不分页列表（导出用）。
     */
    List<StoreMemberVo> queryList(StoreMemberQuery query);

    /**
     * 会员详情。
     */
    StoreMemberVo queryById(Long id);

    /**
     * 新增会员（手机号查重 → 生成 member_no → INSERT），返回新会员 ID。
     */
    Long add(StoreMemberBo bo);

    /**
     * 编辑会员（改手机号时查重排除自身）。
     */
    void update(StoreMemberBo bo);

    /**
     * 软删会员（{@code del_flag='1' + del_unique=id}，走基类 softDelete）。
     */
    int deleteByIds(Collection<Long> ids);

    /**
     * 简单统计：本月会员数 + 本月录入消费记录数（两个 count，不做 RFM）。
     */
    StoreMemberStatsVo getMonthlyStats();

}
