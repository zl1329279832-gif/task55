package cn.mafangui.hotel.service;

import cn.mafangui.hotel.HotelApplication;
import cn.mafangui.hotel.entity.CheckIn;
import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.Room;
import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.mapper.CheckInMapper;
import cn.mafangui.hotel.mapper.OrderMapper;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import cn.mafangui.hotel.mapper.RoomMapper;
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
 * 入住/退房测试：状态流转、重复入住拦截、退房后订单状态更新
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HotelApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Sql(scripts = {"classpath:test-schema.sql", "classpath:test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class CheckInServiceTest {

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomInventoryMapper inventoryMapper;

    private static final int SINGLE_TYPE_ID = 1;

    private Date date(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(s);
    }

    private Order createAndPayOrder(int typeId, Date orderDate, int days) throws Exception {
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
        orderService.payOrder(order.getOrderId());
        return orderService.selectById(order.getOrderId());
    }

    @Test
    public void testCheckIn_success() throws Exception {
        Order order = createAndPayOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);

        CheckIn ci = new CheckIn();
        ci.setOrderId(order.getOrderId());
        ci.setPeoCount(1);
        ci.setPersons("TestGuest");
        ci.setIds("110101199001011234");

        Room room = checkInService.checkIn(ci);
        assertNotNull("check-in should assign a room", room);
        assertEquals("room should be IN_USE", 3, (int) room.getRoomStatus());

        // Order status should be CHECK_IN
        Order updated = orderService.selectById(order.getOrderId());
        assertEquals(OrderStatus.CHECK_IN.getCode(), (int) updated.getOrderStatus());
    }

    @Test
    public void testCheckIn_unpaidOrder_rejected() throws Exception {
        // Create an UNPAID order (not paid)
        Order order = new Order();
        order.setOrderTypeId(2);
        order.setOrderType("online");
        order.setUserId(1);
        order.setName("TestGuest");
        order.setPhone("13800000001");
        order.setRoomTypeId(SINGLE_TYPE_ID);
        order.setRoomType("single");
        order.setOrderDate(date("2026-07-01"));
        order.setOrderDays(1);
        order.setOrderStatus(OrderStatus.UNPAID.getCode());
        order.setOrderCost(200.0);
        orderMapper.insertSelective(order);

        CheckIn ci = new CheckIn();
        ci.setOrderId(order.getOrderId());
        ci.setPeoCount(1);
        ci.setPersons("TestGuest");
        ci.setIds("110101199001011234");

        Room room = checkInService.checkIn(ci);
        assertNull("unpaid order check-in should return null", room);
    }

    @Test
    public void testCheckIn_alreadyCheckedIn_rejected() throws Exception {
        Order order = createAndPayOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);

        CheckIn ci1 = new CheckIn();
        ci1.setOrderId(order.getOrderId());
        ci1.setPeoCount(1);
        ci1.setPersons("TestGuest");
        ci1.setIds("110101199001011234");
        Room room1 = checkInService.checkIn(ci1);
        assertNotNull("first check-in should succeed", room1);

        // Try to check in again with the same order
        CheckIn ci2 = new CheckIn();
        ci2.setOrderId(order.getOrderId());
        ci2.setPeoCount(2);
        ci2.setPersons("TestGuest2");
        ci2.setIds("110101199001011235");
        Room room2 = checkInService.checkIn(ci2);
        assertNull("duplicate check-in should return null", room2);
    }

    @Test
    public void testCheckOut_success() throws Exception {
        Order order = createAndPayOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);

        CheckIn ci = new CheckIn();
        ci.setOrderId(order.getOrderId());
        ci.setPeoCount(1);
        ci.setPersons("TestGuest");
        ci.setIds("110101199001011234");
        Room room = checkInService.checkIn(ci);
        assertNotNull(room);

        // Check out
        int result = checkInService.checkOut(room.getRoomNumber());
        assertTrue("check-out should return positive", result > 0);

        // Room should be AVAILABLE again
        Room afterCheckout = roomMapper.selectByNumber(room.getRoomNumber());
        assertEquals("room should be AVAILABLE after checkout", 1, (int) afterCheckout.getRoomStatus());

        // Order status should be CHECK_OUT
        Order afterOrder = orderService.selectById(order.getOrderId());
        assertEquals("order should be CHECK_OUT", OrderStatus.CHECK_OUT.getCode(), (int) afterOrder.getOrderStatus());
    }

    @Test
    public void testCheckOut_noActiveCheckIn_rejected() {
        // Try to check out a room that has no check-in record
        int result = checkInService.checkOut("999");
        assertTrue("check-out non-existent room should fail", result < 0);
    }

    @Test
    public void testCheckOut_updatesOrderStatus() throws Exception {
        Order order = createAndPayOrder(SINGLE_TYPE_ID, date("2026-07-01"), 1);

        CheckIn ci = new CheckIn();
        ci.setOrderId(order.getOrderId());
        ci.setPeoCount(1);
        ci.setPersons("TestGuest");
        ci.setIds("110101199001011234");
        Room room = checkInService.checkIn(ci);
        assertNotNull(room);

        checkInService.checkOut(room.getRoomNumber());

        Order updated = orderService.selectById(order.getOrderId());
        assertEquals(OrderStatus.CHECK_OUT.getCode(), (int) updated.getOrderStatus());
    }
}
