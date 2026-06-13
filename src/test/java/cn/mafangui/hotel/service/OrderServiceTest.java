package cn.mafangui.hotel.service;

import cn.mafangui.hotel.HotelApplication;
import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.mapper.OrderMapper;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * 订单服务测试：支付、取消状态流转与库存释放
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HotelApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Sql(scripts = {"classpath:test-schema.sql", "classpath:test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RoomInventoryMapper inventoryMapper;

    private static final int SINGLE_TYPE_ID = 1;

    private Date date(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(s);
    }

    /**
     * 创建一笔 UNPAID 订单并插入数据库
     */
    private Order createUnpaidOrder(int typeId, Date orderDate, int days) {
        Order order = new Order();
        order.setOrderTypeId(2);
        order.setOrderType("online");
        order.setUserId(1);
        order.setName("TestGuest");
        order.setPhone("13800000001");
        order.setRoomTypeId(typeId);
        order.setRoomType("single");
        order.setOrderDate(orderDate);
        order.setOrderDays(days);
        order.setOrderStatus(OrderStatus.UNPAID.getCode());
        order.setOrderCost(200.0 * days);
        orderMapper.insertSelective(order);
        return order;
    }

    @Test
    public void testPayOrder_success() throws Exception {
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);
        int result = orderService.payOrder(order.getOrderId());
        assertEquals("pay should succeed", 1, result);

        Order paid = orderService.selectById(order.getOrderId());
        assertEquals("status should be PAID", OrderStatus.PAID.getCode(), (int) paid.getOrderStatus());
    }

    @Test
    public void testPayOrder_nullOrder_returnsError() {
        int result = orderService.payOrder(99999);
        assertEquals("non-existent order should return -3", -3, result);
    }

    @Test
    public void testPayOrder_alreadyPaid_returnsError() throws Exception {
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);
        orderService.payOrder(order.getOrderId());
        // Try to pay again
        int result = orderService.payOrder(order.getOrderId());
        assertEquals("already paid order should return -3", -3, result);
    }

    @Test
    public void testCancelOrder_unpaid_noInventoryRelease() throws Exception {
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);
        // Cancel UNPAID order (never occupied inventory)
        int result = orderService.cancelOrder(order.getOrderId());
        assertEquals("cancel unpaid should succeed", 1, result);

        Order canceled = orderService.selectById(order.getOrderId());
        assertEquals(OrderStatus.WAS_CANCELED.getCode(), (int) canceled.getOrderStatus());
    }

    @Test
    public void testCancelOrder_paid_releasesInventory() throws Exception {
        Date startDate = date("2026-07-01");
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, startDate, 2);
        orderService.payOrder(order.getOrderId());

        // Verify inventory was occupied
        RoomInventory inv = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, startDate);
        assertNotNull(inv);
        assertEquals("ordered should be 1 after pay", 1, (int) inv.getOrdered());

        // Cancel the PAID order
        int result = orderService.cancelOrder(order.getOrderId());
        assertEquals("cancel paid should succeed", 1, result);

        // Verify inventory was released
        RoomInventory invAfter = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, startDate);
        assertEquals("ordered should be 0 after cancel", 0, (int) invAfter.getOrdered());

        Order canceled = orderService.selectById(order.getOrderId());
        assertEquals(OrderStatus.WAS_CANCELED.getCode(), (int) canceled.getOrderStatus());
    }

    @Test
    public void testCancelOrder_alreadyCanceled_returnsError() throws Exception {
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);
        orderService.cancelOrder(order.getOrderId());
        // Try to cancel again
        int result = orderService.cancelOrder(order.getOrderId());
        assertEquals("double cancel should return -3", -3, result);
    }

    @Test
    public void testCancelOrder_checkedIn_returnsError() throws Exception {
        // This tests that a CHECK_IN order cannot be canceled
        // We need to manually set the status since we can't do full check-in here
        Order order = createUnpaidOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);
        orderService.payOrder(order.getOrderId());
        // Manually set to CHECK_IN
        order.setOrderStatus(OrderStatus.CHECK_IN.getCode());
        orderMapper.updateByPrimaryKeySelective(order);

        int result = orderService.cancelOrder(order.getOrderId());
        assertEquals("cancel checked-in order should return -3", -3, result);
    }
}
