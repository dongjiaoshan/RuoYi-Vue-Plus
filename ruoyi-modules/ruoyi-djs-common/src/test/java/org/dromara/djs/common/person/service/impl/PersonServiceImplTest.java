package org.dromara.djs.common.person.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.person.domain.Person;
import org.dromara.djs.common.person.domain.bo.PersonBo;
import org.dromara.djs.common.person.domain.query.PersonQuery;
import org.dromara.djs.common.person.domain.vo.PersonVo;
import org.dromara.djs.common.person.mapper.PersonMapper;
import org.dromara.system.domain.SysPost;
import org.dromara.system.mapper.SysPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PersonServiceImpl} 单元测试（SYS-MD-001）。
 *
 * <p>覆盖 happy path：insert → list → query by id → update → soft-delete；
 * 同时校验 person_code 由后端生成、edit 不允许覆盖 person_code、update/delete 的非法入参分支。</p>
 *
 * <p>不启 Spring；用 Mockito mock {@link PersonMapper}，通过子类覆盖 {@code toEntity} 钩子
 * 绕开 {@code MapstructUtils.convert}（其依赖 Spring 单例 Converter）。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PersonServiceImpl 单元测试")
class PersonServiceImplTest {

    @Mock
    private PersonMapper personMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    @Mock
    private SysPostMapper sysPostMapper;

    private TestablePersonServiceImpl service;

    /**
     * 子类覆盖 {@code toEntity} 钩子，避开 SpringUtils.getBean(Converter.class)。
     * 直接把 BO 的字段手动拷到 Entity，模拟 MapStruct 生成的 mapper。
     */
    static class TestablePersonServiceImpl extends PersonServiceImpl {
        TestablePersonServiceImpl(PersonMapper personMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  SysPostMapper sysPostMapper) {
            super(personMapper, bizCodeGenerator, sysPostMapper);
        }

        @Override
        protected Person toEntity(PersonBo bo) {
            if (bo == null) {
                return null;
            }
            Person p = new Person();
            p.setId(bo.getId());
            p.setName(bo.getName());
            p.setGender(bo.getGender());
            p.setPhone(bo.getPhone());
            p.setIdCard(bo.getIdCard());
            p.setPostId(bo.getPostId());
            p.setHireDate(bo.getHireDate());
            p.setStatus(bo.getStatus());
            p.setAvatarUrl(bo.getAvatarUrl());
            p.setRemark(bo.getRemark());
            return p;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestablePersonServiceImpl(personMapper, bizCodeGenerator, sysPostMapper);
        when(bizCodeGenerator.generate(eq(BizCodeType.MEMBER_NO), any())).thenReturn("M0001");
    }

    private PersonBo sampleBo() {
        PersonBo bo = new PersonBo();
        bo.setName("张三");
        bo.setGender("0");
        bo.setPhone("13800138000");
        bo.setIdCard("310101199001010011");
        bo.setPostId(2L);
        bo.setHireDate(LocalDate.of(2026, 5, 1));
        bo.setRemark("D02 单测样本");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → person_code 由后端生成 / status 默认 '0' / mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        PersonBo bo = sampleBo();
        when(personMapper.insert(any(Person.class))).thenAnswer(inv -> {
            Person entity = inv.getArgument(0);
            entity.setId(10001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMapper, times(1)).insert(captor.capture());
        Person saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getStatus()).isEqualTo("0");
        // person_code 由 IBizCodeGenerator 按 BizCodeType.MEMBER_NO 生成（pattern M{seq4}）
        assertThat(saved.getPersonCode()).isEqualTo("M0001");
    }

    @Test
    @DisplayName("insertByBo: 已传 status='2'（试用）→ 保留不覆盖")
    void testInsertByBo_KeepProvidedStatus() {
        PersonBo bo = sampleBo();
        bo.setStatus("2");
        when(personMapper.insert(any(Person.class))).thenReturn(1);

        service.insertByBo(bo);

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("2");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返回封装后的 TableDataInfo")
    void testQueryPageList() {
        PersonQuery query = new PersonQuery();
        query.setName("张");
        PageQuery pageQuery = new PageQuery(1, 10);

        PersonVo vo = new PersonVo();
        vo.setId(10001L);
        vo.setName("张三");
        Page<PersonVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(personMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<PersonVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("queryPageList: postId 非空 → batch enrich postName 自 sys_post")
    void testQueryPageList_EnrichPostName() {
        PersonQuery query = new PersonQuery();
        PageQuery pageQuery = new PageQuery(1, 10);

        PersonVo a = new PersonVo();
        a.setId(10001L);
        a.setName("张三");
        a.setPostId(2L);
        PersonVo b = new PersonVo();
        b.setId(10002L);
        b.setName("李四");
        b.setPostId(3L);
        PersonVo c = new PersonVo();
        c.setId(10003L);
        c.setName("王五");
        // c.postId == null → 跳过 enrich

        Page<PersonVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(a, b, c));
        mockPage.setTotal(3);
        when(personMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        SysPost p2 = new SysPost();
        p2.setPostId(2L);
        p2.setPostName("项目经理");
        SysPost p3 = new SysPost();
        p3.setPostId(3L);
        p3.setPostName("人力资源");
        when(sysPostMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(p2, p3));

        TableDataInfo<PersonVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getRows()).extracting(PersonVo::getPostName)
            .containsExactly("项目经理", "人力资源", null);
        verify(sysPostMapper, times(1)).selectBatchIds(any(Collection.class));
    }

    @Test
    @DisplayName("queryById: postId 非空 → enrich postName 单条")
    void testQueryById_EnrichPostName() {
        PersonVo vo = new PersonVo();
        vo.setId(10001L);
        vo.setName("张三");
        vo.setPostId(1L);
        when(personMapper.selectVoById(10001L)).thenReturn(vo);

        SysPost p1 = new SysPost();
        p1.setPostId(1L);
        p1.setPostName("董事长");
        when(sysPostMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(p1));

        PersonVo got = service.queryById(10001L);
        assertThat(got.getPostName()).isEqualTo("董事长");
    }

    @Test
    @DisplayName("queryById: 走 mapper.selectVoById")
    void testQueryById() {
        PersonVo vo = new PersonVo();
        vo.setId(10001L);
        vo.setName("张三");
        when(personMapper.selectVoById(10001L)).thenReturn(vo);

        PersonVo got = service.queryById(10001L);
        assertThat(got).isNotNull();
        assertThat(got.getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("updateByBo: happy path → 编辑不允许覆盖 person_code（保留 DB 原值）")
    void testUpdateByBo_PreservesPersonCode() {
        Person existing = new Person();
        existing.setId(10001L);
        existing.setPersonCode("M0007");
        existing.setName("张三");
        when(personMapper.selectById(10001L)).thenReturn(existing);
        when(personMapper.updateById(any(Person.class))).thenReturn(1);

        PersonBo bo = sampleBo();
        bo.setId(10001L);
        bo.setName("张四");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMapper).updateById(captor.capture());
        Person saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("张四");
        assertThat(saved.getPersonCode()).isEqualTo("M0007");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        PersonBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("人员 ID 不能为空");
    }

    @Test
    @DisplayName("updateByBo: DB 查不到（已软删 / id 错误）→ 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(personMapper.selectById(99999L)).thenReturn(null);

        PersonBo bo = sampleBo();
        bo.setId(99999L);

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("人员不存在");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 每个 id 一次 wrapper-only update，sqlSet 显式写 del_flag/del_unique/update_by/update_time")
    void testDeleteWithValidByIds_HappyPath() {
        when(personMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(10001L, 10002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<Person>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(personMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<Person>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).hasSize(2);
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
        // del_unique 写入 id（MP paramNameValuePairs 中以 String 形式存储）
        assertThat(capturedWrappers.get(0).getParamNameValuePairs().values())
            .extracting(Object::toString).contains("10001");
        assertThat(capturedWrappers.get(1).getParamNameValuePairs().values())
            .extracting(Object::toString).contains("10002");
        // del_flag 写 '1'
        assertThat(capturedWrappers).allSatisfy(w ->
            assertThat(w.getParamNameValuePairs().values()).extracting(Object::toString).contains("1"));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 直接返 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(personMapper, times(0)).update(isNull(), any(UpdateWrapper.class));
    }
}
