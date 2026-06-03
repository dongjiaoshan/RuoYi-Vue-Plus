package org.dromara.djs.store.check.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.check.domain.StoreCheckRecord;
import org.dromara.djs.store.check.domain.bo.StoreCheckCreateBo;
import org.dromara.djs.store.check.domain.bo.StoreCheckLineBo;
import org.dromara.djs.store.check.domain.query.StoreCheckQuery;
import org.dromara.djs.store.check.domain.vo.StoreCheckRecordVo;
import org.dromara.djs.store.check.mapper.StoreCheckRecordMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreCheckServiceImpl} 单测（STR-STOCK-001）。
 *
 * <p>覆盖核心场景：</p>
 * <ol>
 *   <li>createCheck happy：门店存在 + 无进行中盘点 → INSERT header(status=in_progress / 锁门店)</li>
 *   <li>createCheck 互斥：同门店已有进行中盘点 → 抛 ServiceException + 不 INSERT</li>
 *   <li>submitLine：自动算 sysStock(门店无库存表→0) + diffStock + checkResultType；INSERT line</li>
 *   <li>submitLine 拒绝：盘点单已完成 → 抛 ServiceException</li>
 *   <li>completeCheck：差异已落 line，仅 header status=completed 解锁（门店不回写库存）</li>
 *   <li>assertProductUnlocked：被锁 → 抛 ServiceException（业务锁后端双保险）</li>
 * </ol>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreCheckServiceImpl 单元测试")
class StoreCheckServiceImplTest {

    @Mock private StoreCheckRecordMapper baseMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private DjsUserExtMapper djsUserExtMapper;

    private TestableStoreCheckServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;
    private MockedStatic<TenantHelper> tenantHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long STORE_ID = 5001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final String CHECK_ID = "SC2026061400001";
    private static final String TENANT = "1001";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：service 内
     * LambdaQueryWrapper 在 mock 路径下也可能触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreCheckRecord.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    /**
     * 子类化 stub generateCheckId / currentStock 固定值（避开真实 mapper / Redisson 锁）。
     */
    static class TestableStoreCheckServiceImpl extends StoreCheckServiceImpl {
        BigDecimal stubSysStock = BigDecimal.ZERO;

        TestableStoreCheckServiceImpl(StoreCheckRecordMapper b, StoreMapper sm,
                                      ProductInfoMapper pm, IBizCodeGenerator g,
                                      DjsUserExtMapper um) {
            super(b, sm, pm, g, um);
        }

        @Override
        protected String generateCheckId() {
            return CHECK_ID;
        }

        @Override
        protected BigDecimal currentStock(Long storeId, Long productId) {
            return stubSysStock;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStoreCheckServiceImpl(baseMapper, storeMapper, productInfoMapper, bizCodeGenerator, djsUserExtMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT);
        when(baseMapper.insert(any(StoreCheckRecord.class))).thenAnswer(inv -> {
            StoreCheckRecord r = inv.getArgument(0);
            r.setId(60000L + (long) (Math.random() * 1000));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
        tenantHelperMock.close();
    }

    private StoreCheckCreateBo createBo() {
        StoreCheckCreateBo bo = new StoreCheckCreateBo();
        bo.setStoreId(STORE_ID);
        bo.setCheckDate(new Date());
        bo.setRemark("ut");
        return bo;
    }

    @Test
    @DisplayName("createCheck happy：门店存在 + 无进行中 → INSERT header(status=in_progress / isHeader=1 / 锁门店)")
    void testCreateCheck_Happy() {
        Store store = new Store();
        store.setId(STORE_ID);
        store.setStoreName("东角山旗舰店");
        when(storeMapper.selectById(STORE_ID)).thenReturn(store);
        when(baseMapper.countActive(eq(STORE_ID), isNull(), eq(TENANT))).thenReturn(0L);

        Long id = service.createCheck(createBo());
        assertThat(id).isNotNull();

        ArgumentCaptor<StoreCheckRecord> cap = ArgumentCaptor.forClass(StoreCheckRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreCheckRecord header = cap.getValue();
        assertThat(header.getCheckStatus()).isEqualTo("in_progress");
        assertThat(header.getIsHeader()).isEqualTo(1);
        assertThat(header.getCheckId()).isEqualTo(CHECK_ID);
        assertThat(header.getStoreId()).isEqualTo(STORE_ID);
        assertThat(header.getProductId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("createCheck 互斥：同门店已有进行中盘点 → 抛 ServiceException + 不 INSERT")
    void testCreateCheck_StoreBusy() {
        Store store = new Store();
        store.setId(STORE_ID);
        when(storeMapper.selectById(STORE_ID)).thenReturn(store);
        when(baseMapper.countActive(eq(STORE_ID), isNull(), eq(TENANT))).thenReturn(1L);

        assertThatThrownBy(() -> service.createCheck(createBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("进行中");

        verify(baseMapper, never()).insert(any(StoreCheckRecord.class));
    }

    @Test
    @DisplayName("submitLine：门店无库存表 sysStock=0 / 实盘 12 → diff=+12 / resultType=异常(2) / INSERT line")
    void testSubmitLine_AutoCalc() {
        StoreCheckRecord header = new StoreCheckRecord();
        header.setId(1L);
        header.setCheckId(CHECK_ID);
        header.setStoreId(STORE_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        header.setCheckDate(new Date());
        // selectOne 第 1 次返 header（找 header），第 2 次返 null（无既有 line）
        when(baseMapper.selectOne(any())).thenReturn(header, (StoreCheckRecord) null);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("有机番茄");
        product.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product);

        service.stubSysStock = BigDecimal.ZERO;

        StoreCheckLineBo bo = new StoreCheckLineBo();
        bo.setCheckId(CHECK_ID);
        bo.setProductId(PRODUCT_ID);
        bo.setCheckStock(new BigDecimal("12"));

        service.submitLine(bo);

        ArgumentCaptor<StoreCheckRecord> cap = ArgumentCaptor.forClass(StoreCheckRecord.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreCheckRecord line = cap.getValue();
        assertThat(line.getIsHeader()).isEqualTo(0);
        assertThat(line.getStoreId()).isEqualTo(STORE_ID);
        assertThat(line.getSysStock()).isEqualByComparingTo("0");
        assertThat(line.getCheckStock()).isEqualByComparingTo("12");
        assertThat(line.getDiffStock()).isEqualByComparingTo("12");
        assertThat(line.getCheckResultType()).isEqualTo(2); // 有差异 → 异常
        assertThat(line.getProductName()).isEqualTo("有机番茄");
        assertThat(line.getCheckBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("submitLine 拒绝：盘点单已完成 → 抛 ServiceException")
    void testSubmitLine_RejectWhenCompleted() {
        StoreCheckRecord header = new StoreCheckRecord();
        header.setId(1L);
        header.setCheckId(CHECK_ID);
        header.setStoreId(STORE_ID);
        header.setCheckStatus("completed");
        header.setIsHeader(1);
        when(baseMapper.selectOne(any())).thenReturn(header);

        StoreCheckLineBo bo = new StoreCheckLineBo();
        bo.setCheckId(CHECK_ID);
        bo.setProductId(PRODUCT_ID);
        bo.setCheckStock(new BigDecimal("5"));

        assertThatThrownBy(() -> service.submitLine(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("非进行中");

        verify(baseMapper, never()).insert(any(StoreCheckRecord.class));
    }

    @Test
    @DisplayName("completeCheck：差异已落 line，仅 header status=completed 解锁（门店不回写库存、不写流水）")
    void testCompleteCheck_OnlyUnlock() {
        StoreCheckRecord header = new StoreCheckRecord();
        header.setId(1L);
        header.setCheckId(CHECK_ID);
        header.setStoreId(STORE_ID);
        header.setCheckStatus("in_progress");
        header.setIsHeader(1);
        when(baseMapper.selectById(1L)).thenReturn(header);

        service.completeCheck(1L);

        // header completed（updateById 被调一次）
        verify(baseMapper, times(1)).updateById(any(StoreCheckRecord.class));
        assertThat(header.getCheckStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("completeCheck 拒绝：盘点单已完成 → 抛 ServiceException")
    void testCompleteCheck_RejectWhenNotInProgress() {
        StoreCheckRecord header = new StoreCheckRecord();
        header.setId(1L);
        header.setCheckStatus("completed");
        header.setIsHeader(1);
        when(baseMapper.selectById(1L)).thenReturn(header);

        assertThatThrownBy(() -> service.completeCheck(1L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("非进行中");
    }

    @Test
    @DisplayName("assertProductUnlocked：门店被锁（有进行中盘点）→ 抛 ServiceException")
    void testAssertProductUnlocked_Locked() {
        when(baseMapper.countActive(eq(STORE_ID), eq(PRODUCT_ID), eq(TENANT))).thenReturn(1L);

        assertThatThrownBy(() -> service.assertProductUnlocked(STORE_ID, PRODUCT_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("盘点中");
    }

    @Test
    @DisplayName("assertProductUnlocked：门店未锁 → 不抛")
    void testAssertProductUnlocked_Free() {
        when(baseMapper.countActive(eq(STORE_ID), eq(PRODUCT_ID), eq(TENANT))).thenReturn(0L);
        service.assertProductUnlocked(STORE_ID, PRODUCT_ID); // 无异常即通过
        assertThat(service.isProductLocked(STORE_ID, PRODUCT_ID)).isFalse();
    }

    @Test
    @DisplayName("queryLineList：导出回填盘点人姓名（@Translation 对 Excel 无效，service 层显式回填 checkByName）")
    void testQueryLineList_FillCheckByName() {
        StoreCheckRecordVo line = new StoreCheckRecordVo();
        line.setId(60001L);
        line.setCheckId(CHECK_ID);
        line.setCheckBy(USER_ID); // 9001
        // storeId 留 null：跳过 fillLineStoreNames 的 store 查询，聚焦盘点人回填
        when(baseMapper.selectVoList(any())).thenReturn(List.of(line));
        when(djsUserExtMapper.selectNickNamesByIds(any()))
            .thenReturn(List.of(Map.of("userId", USER_ID, "nickName", "张三")));

        StoreCheckQuery query = new StoreCheckQuery();
        query.setCheckId(CHECK_ID);
        List<StoreCheckRecordVo> result = service.queryLineList(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCheckByName()).isEqualTo("张三");
        verify(djsUserExtMapper, times(1)).selectNickNamesByIds(any());
    }

}
