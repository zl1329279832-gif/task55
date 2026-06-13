package cn.mafangui.hotel.service.impl;

import cn.mafangui.hotel.entity.CheckIn;
import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.Room;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.enums.RoomStatus;
import cn.mafangui.hotel.mapper.CheckInMapper;
import cn.mafangui.hotel.service.CheckInService;
import cn.mafangui.hotel.service.OrderService;
import cn.mafangui.hotel.service.RoomInventoryService;
import cn.mafangui.hotel.service.RoomService;
import cn.mafangui.hotel.service.RoomTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
public class CheckInServiceImpl implements CheckInService {
    @Autowired
    private CheckInMapper checkInMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RoomTypeService roomTypeService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomInventoryService roomInventoryService;

    @Override
    public int insert(CheckIn checkIn) {
        return checkInMapper.insert(checkIn);
    }

    /**
     * 入住登记
     * @param checkIn
     * 1. 验证订单存在且状态为 PAID（待入住）
     * 2. 防止重复入住（已有未退房的 check_in 记录）
     * 3. 库存转换：ordered → occupied
     * 4. 分配物理房间（FOR UPDATE 锁防止并发重复分配）
     * 5. 更新订单状态为 CHECK_IN
     * @return 分配的房间，若验证失败返回 null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Room checkIn(CheckIn checkIn) {
        Order order = orderService.selectById(checkIn.getOrderId());
        if (order == null || order.getOrderStatus() != OrderStatus.PAID.getCode()) {
            return null;
        }
        // 防止重复入住：该订单是否已有未退房的入住记录
        CheckIn existing = checkInMapper.selectActiveByOrderId(order.getOrderId());
        if (existing != null) {
            return null;
        }
        RoomType rt = roomTypeService.selectById(order.getRoomTypeId());
        // 入住：将 ordered 转为 occupied（不额外消耗库存）
        if (order.getOrderDate() != null && order.getOrderDays() != null && order.getOrderDays() > 0) {
            int invResult = roomInventoryService.occupyForCheckIn(order.getRoomTypeId(), order.getOrderDate(), order.getOrderDays());
            if (invResult != 1) {
                return null;
            }
        }
        int roomId = roomService.inRoom(order.getRoomTypeId());
        if (roomId <= 0) {
            return null;
        }
        Room r = roomService.selectById(roomId);
        if (r == null) {
            return null;
        }
        checkIn.setRoomId(r.getRoomId());
        checkIn.setRoomNumber(r.getRoomNumber());
        roomTypeService.updateRest(rt.getTypeId(), -1);
        order.setOrderStatus(OrderStatus.CHECK_IN.getCode());
        orderService.update(order);
        checkInMapper.insert(checkIn);
        return r;
    }

    /**
     * 退房登记
     * 1. 根据房号查找房间和最新入住记录
     * 2. 释放 occupied 库存
     * 3. 恢复房间状态为 AVAILABLE
     * 4. 更新订单状态为 CHECK_OUT
     * @param roomNumber
     * @return >0 成功, <0 失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int checkOut(String roomNumber) {
        Room r = roomService.selectByNumber(roomNumber);
        if (r == null) return -3;
        CheckIn checkIn = checkInMapper.selectLatestByRoomNumber(roomNumber);
        if (checkIn == null) return -3;
        // 退房：释放 occupied 库存
        if (checkIn.getOrderId() != null) {
            Order order = orderService.selectById(checkIn.getOrderId());
            if (order != null && order.getOrderDate() != null && order.getOrderDays() != null && order.getOrderDays() > 0) {
                roomInventoryService.releaseForCheckOut(order.getRoomTypeId(), order.getOrderDate(), order.getOrderDays());
            }
            // 更新订单状态为已退房
            if (order != null) {
                order.setOrderStatus(OrderStatus.CHECK_OUT.getCode());
                orderService.update(order);
            }
        }
        RoomType ty = roomTypeService.selectById(r.getTypeId());
        r.setRoomStatus(RoomStatus.AVAILABLE.getCode());
        if (roomService.update(r) <= 0) return -3;
        if (roomTypeService.updateRest(ty.getTypeId(), 1) <= 0) return -2;
        return checkInMapper.checkOut(checkIn.getCheckInId());
    }

    @Override
    public int delete(int checkInId) {
        return checkInMapper.deleteByPrimaryKey(checkInId);
    }

    @Override
    public int update(CheckIn checkIn) {
        return checkInMapper.updateByPrimaryKeySelective(checkIn);
    }



    @Override
    public int updateByRoomNumber(String roomNumber) {
        return checkInMapper.updateByRoomNumber(roomNumber);
    }

    @Override
    public CheckIn selectById(int checkInId) {
        return checkInMapper.selectByPrimaryKey(checkInId);
    }

    @Override
    public List<CheckIn> selectAll() {
        return checkInMapper.selectAll();
    }
}
