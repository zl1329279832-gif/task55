package cn.mafangui.hotel.service;

import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.mapper.OrderMapper;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import cn.mafangui.hotel.mapper.RoomTypeMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Sql(scripts = {"classpath:test-schema.sql", "classpath:test-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RoomTypeMapper roomTypeMapper;
    @Autowired
    private RoomInventoryMapper inventoryMapper;

    private Date orderDate;

    @Before
    public void setUp() throws Exception {
        orderDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-08-01");
    }

    private Order createUnpaidOrder(int roomTypeId, int days) {
        Order order = new Order();
        order.setOrderTypeId(2);
        order.setOrderType("online");
        order.setUserId(1);
        order.setName("TestUser");
        order.setPhone("13800000001");
        order.setRoomTypeId(roomTypeId);
        order.setRoomType("single");
        order.setOrderDate(orderDate);
        order.setOrderDays(days);
        order.setOrderStatus(OrderStatus.UNPAID.getCode());
        order.setOrderCost(200.0 * days);
        orderMapper.insertSelective(order);
        return order;
    }

    @Test
    public void testPayOrder_success() {
        Order order = createUnpaidOrder(1, 2);
        int restBefore = roomTypeMapper.selectByPrimaryKey(1).getRest();

        int result = orderService.payOrder(order.getOrderId());

        assertEquals(1, result);
        Order updated = orderMapper.selectByPrimaryKey(order.getOrderId());
        assertEquals(OrderStatus.PAID.getCode(), (int) updated.getOrderStatus());
        // rest should be decremented by 1
        int restAfter = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals(restBefore - 1, restAfter);
        // inventory should be occupied for each day
        RoomInventory inv = inventoryMapper.selectByTypeAndDate(1, orderDate);
        assertNotNull(inv);
        assertEquals(Integer.valueOf(1), inv.getOrdered());
    }

    @Test
    public void testPayOrder_alreadyPaid_returnsError() {
        Order order = createUnpaidOrder(1, 1);
        orderService.payOrder(order.getOrderId());

        int result = orderService.payOrder(order.getOrderId());

        assertEquals(-3, result);
    }

    @Test
    public void testPayOrder_nullOrder_returnsError() {
        int result = orderService.payOrder(99999);
        assertEquals(-3, result);
    }

    @Test
    public void testCancelOrder_unpaid_noInventoryRelease() {
        Order order = createUnpaidOrder(1, 1);
        int restBefore = roomTypeMapper.selectByPrimaryKey(1).getRest();

        int result = orderService.cancelOrder(order.getOrderId());

        assertEquals(1, result);
        Order updated = orderMapper.selectByPrimaryKey(order.getOrderId());
        assertEquals(OrderStatus.WAS_CANCELED.getCode(), (int) updated.getOrderStatus());
        // rest should NOT change since unpaid order never occupied anything
        int restAfter = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals(restBefore, restAfter);
    }

    @Test
    public void testCancelOrder_paid_releasesInventory() {
        Order order = createUnpaidOrder(1, 2);
        orderService.payOrder(order.getOrderId());
        int restAfterPay = roomTypeMapper.selectByPrimaryKey(1).getRest();

        int result = orderService.cancelOrder(order.getOrderId());

        assertEquals(1, result);
        Order updated = orderMapper.selectByPrimaryKey(order.getOrderId());
        assertEquals(OrderStatus.WAS_CANCELED.getCode(), (int) updated.getOrderStatus());
        // rest should be restored
        int restAfterCancel = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals(restAfterPay + 1, restAfterCancel);
        // inventory ordered should be decremented back
        RoomInventory inv = inventoryMapper.selectByTypeAndDate(1, orderDate);
        assertEquals(Integer.valueOf(0), inv.getOrdered());
    }

    @Test
    public void testCancelOrder_checkedIn_returnsError() {
        Order order = createUnpaidOrder(1, 1);
        orderService.payOrder(order.getOrderId());
        // simulate check-in by setting status directly
        order = orderMapper.selectByPrimaryKey(order.getOrderId());
        order.setOrderStatus(OrderStatus.CHECK_IN.getCode());
        orderMapper.updateByPrimaryKeySelective(order);

        int result = orderService.cancelOrder(order.getOrderId());

        assertEquals(-3, result);
    }

    @Test
    public void testCancelOrder_alreadyCanceled_returnsError() {
        Order order = createUnpaidOrder(1, 1);
        orderService.payOrder(order.getOrderId());
        orderService.cancelOrder(order.getOrderId());

        int result = orderService.cancelOrder(order.getOrderId());

        assertEquals(-3, result);
    }
}
