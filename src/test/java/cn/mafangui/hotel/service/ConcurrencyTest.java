package cn.mafangui.hotel.service;

import cn.mafangui.hotel.HotelApplication;
import cn.mafangui.hotel.entity.CheckIn;
import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.Room;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 并发测试：并发下单、取消释放、跨天入住、重复入住
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HotelApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Sql(scripts = {"classpath:test-schema.sql", "classpath:test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class ConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RoomInventoryMapper inventoryMapper;

    private static final int SINGLE_TYPE_ID = 1; // 3 rooms

    private Date date(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd").parse(s);
    }

    private Order createUnpaidOrder(String guestName, int typeId, Date orderDate, int days) {
        Order order = new Order();
        order.setOrderTypeId(2);
        order.setOrderType("online");
        order.setUserId(1);
        order.setName(guestName);
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

    /**
     * 并发支付同一订单：恰好只有一个成功
     */
    @Test
    public void testConcurrentPayOrder_onlyOneSucceeds() throws Exception {
        Order order = createUnpaidOrder("ConcGuest", SINGLE_TYPE_ID, date("2026-07-01"), 1);
        int orderId = order.getOrderId();

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    int result = orderService.payOrder(orderId);
                    if (result == 1) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads
        latch.await();
        executor.shutdown();

        assertEquals("exactly one thread should succeed", 1, successCount.get());
        assertEquals("remaining threads should fail", threadCount - 1, failCount.get());
    }

    /**
     * 并发入住不同订单：不应分配到相同房间
     */
    @Test
    public void testConcurrentCheckIn_noDuplicateRoom() throws Exception {
        Date startDate = date("2026-07-01");

        // Create 3 orders and pay them (we have 3 single rooms)
        Order o1 = createUnpaidOrder("Guest1", SINGLE_TYPE_ID, startDate, 1);
        orderService.payOrder(o1.getOrderId());
        Order o2 = createUnpaidOrder("Guest2", SINGLE_TYPE_ID, startDate, 1);
        orderService.payOrder(o2.getOrderId());
        Order o3 = createUnpaidOrder("Guest3", SINGLE_TYPE_ID, startDate, 1);
        orderService.payOrder(o3.getOrderId());

        int threadCount = 3;
        int[] orderIds = {o1.getOrderId(), o2.getOrderId(), o3.getOrderId()};
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        Set<Integer> assignedRoomIds = java.util.Collections.synchronizedSet(new HashSet<>());
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CheckIn ci = new CheckIn();
                    ci.setOrderId(orderIds[idx]);
                    ci.setPeoCount(1);
                    ci.setPersons("Guest" + (idx + 1));
                    ci.setIds("ID" + (idx + 1));
                    Room room = checkInService.checkIn(ci);
                    if (room != null) {
                        assignedRoomIds.add(room.getRoomId());
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // fail silently
                } finally {
                    latch.countDown();
                }
            });
        }

        startGate.countDown();
        latch.await();
        executor.shutdown();

        assertEquals("all 3 check-ins should succeed", 3, successCount.get());
        assertEquals("all 3 rooms should be different", 3, assignedRoomIds.size());
    }

    /**
     * 取消释放库存：支付后取消，库存应完全恢复
     */
    @Test
    public void testCancelReleasesInventory_correctlyAfterPay() throws Exception {
        Date startDate = date("2026-07-01");

        // Pay an order
        Order order = createUnpaidOrder("CancelGuest", SINGLE_TYPE_ID, startDate, 1);
        orderService.payOrder(order.getOrderId());

        RoomInventory invAfterPay = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, startDate);
        assertNotNull(invAfterPay);
        assertEquals("ordered=1 after pay", 1, (int) invAfterPay.getOrdered());

        // Cancel
        orderService.cancelOrder(order.getOrderId());

        RoomInventory invAfterCancel = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, startDate);
        assertEquals("ordered=0 after cancel", 0, (int) invAfterCancel.getOrdered());

        // Now another order should be able to occupy the same slot
        Order order2 = createUnpaidOrder("NewGuest", SINGLE_TYPE_ID, startDate, 1);
        int result = orderService.payOrder(order2.getOrderId());
        assertEquals("new order after cancel should succeed", 1, result);
    }

    /**
     * 跨天入住：3晚住宿，每天的库存都应正确跟踪
     */
    @Test
    public void testCrossDayCheckIn_inventoryCorrect() throws Exception {
        Date startDate = date("2026-07-01");
        int days = 3;

        // Create and pay a 3-night order
        Order order = createUnpaidOrder("CrossDayGuest", SINGLE_TYPE_ID, startDate, days);
        int payResult = orderService.payOrder(order.getOrderId());
        assertEquals(1, payResult);

        // Check all 3 days have ordered=1
        for (int i = 0; i < days; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            Date dayDate = cal.getTime();
            // Normalize to midnight
            Calendar norm = Calendar.getInstance();
            norm.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            norm.set(Calendar.MILLISECOND, 0);
            dayDate = norm.getTime();

            RoomInventory inv = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, dayDate);
            assertNotNull("inventory for day " + i + " should exist", inv);
            assertEquals("day " + i + " ordered should be 1", 1, (int) inv.getOrdered());
            assertEquals("day " + i + " occupied should be 0", 0, (int) inv.getOccupied());
        }

        // Now check in
        CheckIn ci = new CheckIn();
        ci.setOrderId(order.getOrderId());
        ci.setPeoCount(1);
        ci.setPersons("CrossDayGuest");
        ci.setIds("110101199001011234");
        Room room = checkInService.checkIn(ci);
        assertNotNull("check-in should succeed", room);

        // After check-in: ordered=0, occupied=1 for all 3 days
        for (int i = 0; i < days; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            Calendar norm = Calendar.getInstance();
            norm.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            norm.set(Calendar.MILLISECOND, 0);
            Date dayDate = norm.getTime();

            RoomInventory inv = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, dayDate);
            assertNotNull(inv);
            assertEquals("day " + i + " ordered should be 0 after check-in", 0, (int) inv.getOrdered());
            assertEquals("day " + i + " occupied should be 1 after check-in", 1, (int) inv.getOccupied());
        }

        // Check out
        int coResult = checkInService.checkOut(room.getRoomNumber());
        assertTrue("check-out should succeed", coResult > 0);

        // After check-out: ordered=0, occupied=0 for all 3 days
        for (int i = 0; i < days; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            Calendar norm = Calendar.getInstance();
            norm.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            norm.set(Calendar.MILLISECOND, 0);
            Date dayDate = norm.getTime();

            RoomInventory inv = inventoryMapper.selectByTypeAndDate(SINGLE_TYPE_ID, dayDate);
            assertNotNull(inv);
            assertEquals("day " + i + " ordered should be 0 after checkout", 0, (int) inv.getOrdered());
            assertEquals("day " + i + " occupied should be 0 after checkout", 0, (int) inv.getOccupied());
        }
    }
}
