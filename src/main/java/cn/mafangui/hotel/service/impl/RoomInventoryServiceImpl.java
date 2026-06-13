package cn.mafangui.hotel.service.impl;

import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import cn.mafangui.hotel.service.RoomInventoryService;
import cn.mafangui.hotel.service.RoomTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class RoomInventoryServiceImpl implements RoomInventoryService {

    @Autowired
    private RoomInventoryMapper roomInventoryMapper;
    @Autowired
    private RoomTypeService roomTypeService;

    @Override
    public List<RoomInventory> getCalendar(int typeId, Date startDate, Date endDate) {
        return roomInventoryMapper.selectByTypeAndDateRange(typeId, startDate, endDate);
    }

    @Override
    public List<RoomInventory> getInventoryDetails(int typeId, Date startDate, Date endDate) {
        return roomInventoryMapper.selectByTypeAndDateRange(typeId, startDate, endDate);
    }

    @Override
    public int initInventory(int typeId, Date startDate, Date endDate) {
        Integer totalRooms = roomInventoryMapper.countRoomsByType(typeId);
        if (totalRooms == null) totalRooms = 0;

        RoomType rt = roomTypeService.selectById(typeId);
        Double basePrice = (rt != null) ? rt.getPrice() : 0.0;

        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        int count = 0;
        while (!cal.getTime().after(endDate)) {
            RoomInventory inv = new RoomInventory();
            inv.setTypeId(typeId);
            inv.setDate(cal.getTime());
            inv.setTotalCount(totalRooms);
            inv.setReservedCount(0);
            inv.setLockedCount(0);
            inv.setBookedCount(0);
            inv.setBasePrice(basePrice);
            count += roomInventoryMapper.upsert(inv);
            cal.add(Calendar.DATE, 1);
        }
        return count;
    }

    @Override
    public boolean consumeInventory(int typeId, Date orderDate, int orderDays) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(orderDate);
        for (int i = 0; i < orderDays; i++) {
            Date currentDate = cal.getTime();
            // Auto-init if no inventory row exists for this date
            if (roomInventoryMapper.selectByTypeAndDate(typeId, currentDate) == null) {
                Integer totalRooms = roomInventoryMapper.countRoomsByType(typeId);
                if (totalRooms == null) totalRooms = 0;
                RoomType rt = roomTypeService.selectById(typeId);
                Double basePrice = (rt != null) ? rt.getPrice() : 0.0;
                RoomInventory inv = new RoomInventory(typeId, currentDate, totalRooms, basePrice);
                roomInventoryMapper.upsert(inv);
            }
            int affected = roomInventoryMapper.incrementBookedCount(typeId, currentDate);
            if (affected == 0) {
                return false;
            }
            cal.add(Calendar.DATE, 1);
        }
        return true;
    }

    @Override
    public boolean releaseInventory(int typeId, Date orderDate, int orderDays) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(orderDate);
        for (int i = 0; i < orderDays; i++) {
            roomInventoryMapper.decrementBookedCount(typeId, cal.getTime());
            cal.add(Calendar.DATE, 1);
        }
        return true;
    }

    @Override
    public int setReservedCount(int typeId, Date date, int reservedCount) {
        return roomInventoryMapper.updateReservedCount(typeId, date, reservedCount);
    }

    @Override
    public int setLockedCount(int typeId, Date date, int lockedCount) {
        return roomInventoryMapper.updateLockedCount(typeId, date, lockedCount);
    }

    @Override
    public int setTotalCount(int typeId, Date date, int totalCount) {
        return roomInventoryMapper.updateTotalCount(typeId, date, totalCount);
    }
}
