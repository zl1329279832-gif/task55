package cn.mafangui.hotel.service;

import cn.mafangui.hotel.entity.CheckIn;
import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.Room;
import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.enums.RoomStatus;
import cn.mafangui.hotel.mapper.OrderMapper;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import cn.mafangui.hotel.mapper.RoomMapper;
import cn.mafangui.hotel.mapper.RoomTypeMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Sql(scripts = {"classpath:test-schema.sql", "classpath:test-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class ConcurrencyTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private CheckInService checkInService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RoomMapper roomMapper;
    @Autowired
    private RoomTypeMapper roomTypeMapper;
    @Autowired
    private RoomInventoryMapper inventoryMapper;

    private Date orderDate;

    @Before
    public void setUp() throws Exception {
        orderDate = new SimpleDateFormat("yyyy-MM-dd").parse("2026-10-01");
    }

    private Order createUnpaidOrder(int roomTypeId, int days) {
        Order order = new Order();
        order.setOrderTypeId(2);
        order.setOrderType("online");
        order.setUserId(1);
        order.setName("TestUser");
        order.setPhone("13800000001");
        order.setRoomTypeId(roomTypeId);
        order.setRoomType(roomTypeId == 1 ? "single" : "double");
        order.setOrderDate(orderDate);
        order.setOrderDays(days);
        order.setOrderStatus(OrderStatus.UNPAID.getCode());
        order.setOrderCost(200.0 * days);
        orderMapper.insertSelective(order);
        return order;
    }

    /**
     * Test: concurrent pay for orders when only limited rooms available.
     * With 3 single rooms, only 3 pays should succeed out of 5 concurrent attempts.
     */
    @Test
    public void testConcurrentPayOrder_onlyOneSucceeds() throws Exception {
        // Create 5 unpaid orders for single room type (only 3 rooms available)
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(createUnpaidOrder(1, 1));
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (Order order : orders) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    int result = orderService.payOrder(order.getOrderId());
                    if (result == 1) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        latch.countDown(); // release all threads simultaneously
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // At most 3 should succeed (3 single rooms)
        assertTrue("At most 3 pays should succeed, got " + successCount.get(),
                successCount.get() <= 3);
        assertEquals("Total should be 5", 5, successCount.get() + failCount.get());

        // Verify rest count is consistent
        int rest = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals("rest should equal initial - successes",
                10 - successCount.get(), rest);
    }

    /**
     * Test: concurrent check-ins should never assign the same physical room.
     */
    @Test
    public void testConcurrentCheckIn_noDuplicateRoom() throws Exception {
        // Create and pay 3 orders for single rooms
        List<Order> paidOrders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Order order = createUnpaidOrder(1, 1);
            orderService.payOrder(order.getOrderId());
            paidOrders.add(orderMapper.selectByPrimaryKey(order.getOrderId()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(1);
        ConcurrentHashMap<Integer, Room> assignedRooms = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (Order order : paidOrders) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    CheckIn checkIn = new CheckIn();
                    checkIn.setOrderId(order.getOrderId());
                    checkIn.setPeoCount(1);
                    checkIn.setPersons("Guest");
                    checkIn.setIds("ID" + order.getOrderId());
                    Room room = checkInService.checkIn(checkIn);
                    if (room != null) {
                        assignedRooms.put(order.getOrderId(), room);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // All 3 should succeed (3 rooms available)
        assertEquals("All 3 check-ins should succeed", 3, successCount.get());

        // No two orders should get the same room
        Set<Integer> roomIds = new HashSet<>();
        for (Room r : assignedRooms.values()) {
            assertTrue("Duplicate room assignment detected: room " + r.getRoomId(),
                    roomIds.add(r.getRoomId()));
        }
    }

    /**
     * Test: cross-day check-in correctly manages inventory across multiple days.
     */
    @Test
    public void testCrossDayCheckIn_inventoryCorrect() throws Exception {
        // Create a 3-day order for single room
        Order order = createUnpaidOrder(1, 3);
        orderService.payOrder(order.getOrderId());

        // Verify inventory was created for each day
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < 3; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(orderDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date day = cal.getTime();
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(1, day);
            assertNotNull("Inventory should exist for day " + sdf.format(day), inv);
            assertEquals("ordered should be 1 for day " + sdf.format(day),
                    Integer.valueOf(1), inv.getOrdered());
        }

        // Check in
        CheckIn checkIn = new CheckIn();
        checkIn.setOrderId(order.getOrderId());
        checkIn.setPeoCount(1);
        checkIn.setPersons("Guest");
        checkIn.setIds("ID123");
        Room room = checkInService.checkIn(checkIn);
        assertNotNull(room);

        // After check-in: ordered->0, occupied->1 for each day
        for (int i = 0; i < 3; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(orderDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date day = cal.getTime();
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(1, day);
            assertEquals("ordered should be 0 after check-in for day " + sdf.format(day),
                    Integer.valueOf(0), inv.getOrdered());
            assertEquals("occupied should be 1 after check-in for day " + sdf.format(day),
                    Integer.valueOf(1), inv.getOccupied());
        }

        // Check out
        checkInService.checkOut(room.getRoomNumber());

        // After check-out: occupied->0 for each day
        for (int i = 0; i < 3; i++) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(orderDate);
            cal.add(Calendar.DAY_OF_MONTH, i);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date day = cal.getTime();
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(1, day);
            assertEquals("occupied should be 0 after check-out for day " + sdf.format(day),
                    Integer.valueOf(0), inv.getOccupied());
        }
    }

    /**
     * Test: cancel after pay correctly releases inventory for all days.
     */
    @Test
    public void testCancelReleasesInventory_correctlyAfterPay() throws Exception {
        int restBefore = roomTypeMapper.selectByPrimaryKey(1).getRest();

        // Create and pay a 2-day order
        Order order = createUnpaidOrder(1, 2);
        orderService.payOrder(order.getOrderId());

        // rest should be decremented
        int restAfterPay = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals(restBefore - 1, restAfterPay);

        // Inventory should show ordered=1 for both days
        Calendar cal = Calendar.getInstance();
        cal.setTime(orderDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date day1 = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date day2 = cal.getTime();

        assertEquals(Integer.valueOf(1), inventoryMapper.selectByTypeAndDate(1, day1).getOrdered());
        assertEquals(Integer.valueOf(1), inventoryMapper.selectByTypeAndDate(1, day2).getOrdered());

        // Cancel the order
        int result = orderService.cancelOrder(order.getOrderId());
        assertEquals(1, result);

        // rest should be restored
        int restAfterCancel = roomTypeMapper.selectByPrimaryKey(1).getRest();
        assertEquals(restBefore, restAfterCancel);

        // Inventory should show ordered=0 for both days
        assertEquals(Integer.valueOf(0), inventoryMapper.selectByTypeAndDate(1, day1).getOrdered());
        assertEquals(Integer.valueOf(0), inventoryMapper.selectByTypeAndDate(1, day2).getOrdered());
    }
}
