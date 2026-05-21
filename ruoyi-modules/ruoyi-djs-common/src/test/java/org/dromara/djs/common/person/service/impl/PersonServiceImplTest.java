package org.dromara.djs.common.person.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private TestablePersonServiceImpl service;

    /**
     * 子类覆盖 {@code toEntity} 钩子，避开 SpringUtils.getBean(Converter.class)。
     * 直接把 BO 的字段手动拷到 Entity，模拟 MapStruct 生成的 mapper。
     */
    static class TestablePersonServiceImpl extends PersonServiceImpl {
        TestablePersonServiceImpl(PersonMapper personMapper, IBizCodeGenerator bizCodeGenerator) {
            super(personMapper, bizCodeGenerator);
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
            p.setPosition(bo.getPosition());
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
        service = new TestablePersonServiceImpl(personMapper, bizCodeGenerator);
        when(bizCodeGenerator.generate(eq(BizCodeType.MEMBER_NO), any())).thenReturn("M0001");
    }

    private PersonBo sampleBo() {
        PersonBo bo = new PersonBo();
        bo.setName("张三");
        bo.setGender("0");
        bo.setPhone("13800138000");
        bo.setIdCard("310101199001010011");
        bo.setPosition("饲养员");
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
        assertThat(saved.getPostId()).isNull();
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
    @DisplayName("deleteWithValidByIds: happy path → 每个 id 走一次 updateById，delFlag='1' + delUnique=id")
    void testDeleteWithValidByIds_HappyPath() {
        when(personMapper.updateById(any(Person.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(10001L, 10002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personMapper, times(2)).updateById(captor.capture());
        List<Person> captured = captor.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured).allSatisfy(p -> assertThat(p.getDelFlag()).isEqualTo("1"));
        assertThat(captured).extracting(Person::getId).containsExactly(10001L, 10002L);
        assertThat(captured).extracting(Person::getDelUnique).containsExactly(10001L, 10002L);
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 直接返 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(personMapper, times(0)).updateById(any(Person.class));
    }
}
