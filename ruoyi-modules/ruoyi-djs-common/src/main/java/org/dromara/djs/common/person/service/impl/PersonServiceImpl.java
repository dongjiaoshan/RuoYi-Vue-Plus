package org.dromara.djs.common.person.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.person.domain.Person;
import org.dromara.djs.common.person.domain.bo.PersonBo;
import org.dromara.djs.common.person.domain.query.PersonQuery;
import org.dromara.djs.common.person.domain.vo.PersonVo;
import org.dromara.djs.common.person.mapper.PersonMapper;
import org.dromara.djs.common.person.service.IPersonService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 人员主数据 Service 实现（SYS-MD-001）。
 *
 * <p>编码策略：{@code person_code} 新增时由 {@link IBizCodeGenerator} 按
 * {@link BizCodeType#MEMBER_NO} 规则生成（pattern {@code M{seq4}}，例 {@code M0001}），
 * 编辑端点不允许覆盖。</p>
 *
 * <p>软删：循环 {@code updateById}，显式 set {@code delFlag='1'} + {@code delUnique=id}。
 * 不能用 {@code baseMapper.deleteByIds}—— MyBatis-Plus LogicDeleteInterceptor 注入的 UPDATE
 * 在调 updateFill 时合成实体 id=null，{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 拿不到 id 就跳过 del_unique 同步。业务表 UNIQUE(tenant_id, person_code, del_unique)
 * 保证软删后重启用同编码不冲突。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonServiceImpl implements IPersonService {

    private final PersonMapper baseMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    @Override
    public TableDataInfo<PersonVo> queryPageList(PersonQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Person> wrapper = buildQueryWrapper(query);
        Page<PersonVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PersonVo> queryList(PersonQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public PersonVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(PersonBo bo) {
        Person entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("人员入参转换失败");
        }
        entity.setPersonCode(generatePersonCode());
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("0");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(PersonBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("人员 ID 不能为空");
        }
        Person exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("人员不存在或已删除：" + bo.getId());
        }
        Person entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("人员入参转换失败");
        }
        // person_code 不允许通过编辑端点修改
        entity.setPersonCode(exists.getPersonCode());
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus（Spring 注入的 {@code Converter} 单例）。
     *
     * <p>抽成 protected 是为了便于纯 Mockito 单测覆盖（避免启 Spring 上下文），
     * 业务调用方<b>不应</b>子类化本服务来绕开转换逻辑。</p>
     */
    protected Person toEntity(PersonBo bo) {
        return MapstructUtils.convert(bo, Person.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // TODO SYS-MD-001 → D3+：业务表 wire 后，删除前需校验"是否被 t_farm_event_* / t_md_user_farm 等引用"，
        // 引用存在则提示业务方先解绑。当前主数据 ticket 尚无下游引用，直接软删。
        int count = 0;
        for (Long id : ids) {
            Person p = new Person();
            p.setId(id);
            p.setDelFlag("1");
            p.setDelUnique(id);
            count += baseMapper.updateById(p);
        }
        return count;
    }

    /**
     * 构造查询条件：name like / phone like / status eq / post_id eq / person_code eq。
     */
    private LambdaQueryWrapper<Person> buildQueryWrapper(PersonQuery query) {
        LambdaQueryWrapper<Person> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Person::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getName()), Person::getName, query.getName())
            .like(StringUtils.isNotBlank(query.getPhone()), Person::getPhone, query.getPhone())
            .eq(StringUtils.isNotBlank(query.getPersonCode()), Person::getPersonCode, query.getPersonCode())
            .eq(StringUtils.isNotBlank(query.getStatus()), Person::getStatus, query.getStatus())
            .eq(Objects.nonNull(query.getPostId()), Person::getPostId, query.getPostId())
            .orderByDesc(Person::getId);
        return wrapper;
    }

    /**
     * 生成人员编码：走 {@link IBizCodeGenerator}，类型 {@link BizCodeType#MEMBER_NO}。
     */
    private String generatePersonCode() {
        return bizCodeGenerator.generate(BizCodeType.MEMBER_NO, Map.of());
    }

}
