package org.dromara.djs.common.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.bo.StoreBo;
import org.dromara.djs.common.store.domain.query.StoreQuery;
import org.dromara.djs.common.store.domain.vo.StoreVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
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
 * {@link StoreServiceImpl} 单元测试（SYS-MD-002）。
 *
 * <p>覆盖 happy path：insert → list → query by id → update → soft-delete；
 * 同时校验 store_code 由后端生成、edit 不允许覆盖 store_code、默认 storeType / businessStatus、
 * 软删走基类 {@code softDelete} → 每个 id 走一次 updateById 显式 set delFlag/delUnique。</p>
 *
 * <p>不启 Spring；用 Mockito mock {@link StoreMapper}，通过子类覆盖 {@code toEntity} 钩子
 * 绕开 {@code MapstructUtils.convert}（其依赖 Spring 单例 Converter）。</p>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreServiceImpl 单元测试")
class StoreServiceImplTest {

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    private TestableStoreServiceImpl service;

    /**
     * 子类覆盖 {@code toEntity} 钩子，避开 SpringUtils.getBean(Converter.class)。
     * 直接把 BO 的字段手动拷到 Entity，模拟 MapStruct 生成的 mapper。
     */
    static class TestableStoreServiceImpl extends StoreServiceImpl {
        TestableStoreServiceImpl(StoreMapper storeMapper, IBizCodeGenerator bizCodeGenerator) {
            super(storeMapper, bizCodeGenerator);
        }

        @Override
        protected Store toEntity(StoreBo bo) {
            if (bo == null) {
                return null;
            }
            Store s = new Store();
            s.setId(bo.getId());
            s.setStoreName(bo.getStoreName());
            s.setStoreType(bo.getStoreType());
            s.setBusinessStatus(bo.getBusinessStatus());
            s.setAddress(bo.getAddress());
            s.setContactName(bo.getContactName());
            s.setContactPhone(bo.getContactPhone());
            s.setRemark(bo.getRemark());
            return s;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStoreServiceImpl(storeMapper, bizCodeGenerator);
        when(bizCodeGenerator.generate(eq(BizCodeType.STORE_CODE), any())).thenReturn("ST0001");
    }

    private StoreBo sampleBo() {
        StoreBo bo = new StoreBo();
        bo.setStoreName("东角山旗舰店");
        bo.setStoreType("direct");
        bo.setBusinessStatus(1);
        bo.setAddress("上海市浦东新区张江高科园区A栋101");
        bo.setContactName("王经理");
        bo.setContactPhone("13800138001");
        bo.setRemark("D03 SYS-MD-002 单测样本");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → storeCode 由后端生成 / mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        StoreBo bo = sampleBo();
        when(storeMapper.insert(any(Store.class))).thenAnswer(inv -> {
            Store entity = inv.getArgument(0);
            entity.setId(20001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper, times(1)).insert(captor.capture());
        Store saved = captor.getValue();
        assertThat(saved.getStoreName()).isEqualTo("东角山旗舰店");
        assertThat(saved.getStoreType()).isEqualTo("direct");
        assertThat(saved.getBusinessStatus()).isEqualTo(1);
        // store_code 由 IBizCodeGenerator 按 BizCodeType.STORE_CODE 生成（pattern ST{seq4}）
        assertThat(saved.getStoreCode()).isEqualTo("ST0001");
    }

    @Test
    @DisplayName("insertByBo: 未传 businessStatus → 默认 1（合作中）/ 未传 storeType → 默认 direct")
    void testInsertByBo_DefaultsApplied() {
        StoreBo bo = sampleBo();
        bo.setBusinessStatus(null);
        bo.setStoreType(null);
        when(storeMapper.insert(any(Store.class))).thenReturn(1);

        service.insertByBo(bo);

        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).insert(captor.capture());
        Store saved = captor.getValue();
        assertThat(saved.getBusinessStatus()).isEqualTo(1);
        assertThat(saved.getStoreType()).isEqualTo("direct");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返回封装后的 TableDataInfo")
    void testQueryPageList() {
        StoreQuery query = new StoreQuery();
        query.setStoreName("旗舰");
        PageQuery pageQuery = new PageQuery(1, 10);

        StoreVo vo = new StoreVo();
        vo.setId(20001L);
        vo.setStoreName("东角山旗舰店");
        Page<StoreVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(storeMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<StoreVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getStoreName()).isEqualTo("东角山旗舰店");
    }

    @Test
    @DisplayName("queryById: 走 mapper.selectVoById")
    void testQueryById() {
        StoreVo vo = new StoreVo();
        vo.setId(20001L);
        vo.setStoreName("东角山旗舰店");
        when(storeMapper.selectVoById(20001L)).thenReturn(vo);

        StoreVo got = service.queryById(20001L);
        assertThat(got).isNotNull();
        assertThat(got.getStoreName()).isEqualTo("东角山旗舰店");
    }

    @Test
    @DisplayName("updateByBo: happy path → 编辑不允许覆盖 storeCode（保留 DB 原值）")
    void testUpdateByBo_PreservesStoreCode() {
        Store existing = new Store();
        existing.setId(20001L);
        existing.setStoreCode("ST0007");
        existing.setStoreName("东角山旗舰店");
        when(storeMapper.selectById(20001L)).thenReturn(existing);
        when(storeMapper.updateById(any(Store.class))).thenReturn(1);

        StoreBo bo = sampleBo();
        bo.setId(20001L);
        bo.setStoreName("东角山旗舰店（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
        verify(storeMapper).updateById(captor.capture());
        Store saved = captor.getValue();
        assertThat(saved.getStoreName()).isEqualTo("东角山旗舰店（改）");
        assertThat(saved.getStoreCode()).isEqualTo("ST0007");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        StoreBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店 ID 不能为空");
    }

    @Test
    @DisplayName("updateByBo: DB 查不到（已软删 / id 错误）→ 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(storeMapper.selectById(99999L)).thenReturn(null);

        StoreBo bo = sampleBo();
        bo.setId(99999L);

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店不存在");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，每个 id 一次 update(entity, wrapper)，entity 携 id+delUnique，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(storeMapper.update(any(Store.class), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(20001L, 20002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<Store> entityCaptor = ArgumentCaptor.forClass(Store.class);
        ArgumentCaptor<UpdateWrapper<Store>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(storeMapper, times(2)).update(entityCaptor.capture(), wrapperCaptor.capture());

        List<Store> capturedEntities = entityCaptor.getAllValues();
        assertThat(capturedEntities).extracting(Store::getId).containsExactly(20001L, 20002L);
        assertThat(capturedEntities).extracting(Store::getDelUnique).containsExactly(20001L, 20002L);
        // delFlag 不再走 entity，由 wrapper.setSql 强写，绕过 @TableLogic 剥离
        assertThat(capturedEntities).allSatisfy(s -> assertThat(s.getDelFlag()).isNull());

        List<UpdateWrapper<Store>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).hasSize(2);
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag = '1'");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 直接返 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(storeMapper, times(0)).update(any(Store.class), any(UpdateWrapper.class));
    }
}
