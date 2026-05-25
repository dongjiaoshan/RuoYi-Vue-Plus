package org.dromara.djs.common.person.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.person.domain.Person;
import org.dromara.djs.common.person.domain.bo.PersonBo;
import org.dromara.djs.common.person.domain.query.PersonQuery;
import org.dromara.djs.common.person.domain.vo.PersonVo;
import org.dromara.djs.common.person.mapper.PersonMapper;
import org.dromara.djs.common.person.service.IPersonService;
import org.dromara.system.domain.SysPost;
import org.dromara.system.mapper.SysPostMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员主数据 Service 实现（SYS-MD-001）。
 *
 * <p>编码策略：{@code person_code} 新增时由 {@link IBizCodeGenerator} 按
 * {@link BizCodeType#MEMBER_NO} 规则生成（pattern {@code M{seq4}}，例 {@code M0001}），
 * 编辑端点不允许覆盖。</p>
 *
 * <p>软删通过基类 {@link DjsBaseServiceImpl#softDelete(Collection)} 走纯 wrapper update，
 * 显式 set {@code del_flag='1'} + {@code del_unique=id}（参基类注释）。业务表
 * UNIQUE(tenant_id, person_code, del_unique) 保证软删后重启用同编码不冲突。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Slf4j
@Service
public class PersonServiceImpl extends DjsBaseServiceImpl<PersonMapper, Person> implements IPersonService {

    private final IBizCodeGenerator bizCodeGenerator;
    private final SysPostMapper sysPostMapper;

    public PersonServiceImpl(PersonMapper baseMapper,
                             IBizCodeGenerator bizCodeGenerator,
                             SysPostMapper sysPostMapper) {
        super(baseMapper);
        this.bizCodeGenerator = bizCodeGenerator;
        this.sysPostMapper = sysPostMapper;
    }

    @Override
    public TableDataInfo<PersonVo> queryPageList(PersonQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Person> wrapper = buildQueryWrapper(query);
        Page<PersonVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichPostNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PersonVo> queryList(PersonQuery query) {
        List<PersonVo> rows = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichPostNames(rows);
        return rows;
    }

    @Override
    public PersonVo queryById(Long id) {
        PersonVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichPostNames(List.of(vo));
        }
        return vo;
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
        // TODO SYS-MD-001 → D3+：业务表 wire 后，删除前需校验"是否被 t_farm_event_* / t_md_user_farm 等引用"，
        // 引用存在则提示业务方先解绑。D05 BRD-EVENT-001 抽 BizReferenceChecker 后统一改声明式注册。
        return softDelete(ids);
    }

    /**
     * 构造查询条件：name like / phone like / status eq / person_code eq / post_id eq。
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
            .eq(query.getPostId() != null, Person::getPostId, query.getPostId())
            .orderByDesc(Person::getId);
        return wrapper;
    }

    /**
     * 生成人员编码：走 {@link IBizCodeGenerator}，类型 {@link BizCodeType#MEMBER_NO}。
     */
    private String generatePersonCode() {
        return bizCodeGenerator.generate(BizCodeType.MEMBER_NO, Map.of());
    }

    /**
     * 批量回填 {@code postName}（人员 ↔ sys_post 跨聚合，VO 出参用名字给用户看；
     * 列表场景一次性 selectBatchIds 避免 N+1，模式同 PigCoreServiceImpl#enrichBarnPenCodes）。
     */
    private void enrichPostNames(List<PersonVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> postIds = rows.stream()
            .map(PersonVo::getPostId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (postIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = sysPostMapper.selectBatchIds(postIds).stream()
            .collect(Collectors.toMap(SysPost::getPostId, SysPost::getPostName, (a, b) -> a));
        for (PersonVo vo : rows) {
            if (vo.getPostId() != null) {
                vo.setPostName(nameMap.get(vo.getPostId()));
            }
        }
    }

}
