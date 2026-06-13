package cn.mafangui.hotel.service;

import cn.mafangui.hotel.entity.RoomInventory;

import java.util.Date;
import java.util.List;

public interface RoomInventoryService {

    List<RoomInventory> getCalendar(int typeId, Date startDate, Date endDate);

    List<RoomInventory> getInventoryDetails(int typeId, Date startDate, Date endDate);

    int initInventory(int typeId, Date startDate, Date endDate);

    boolean consumeInventory(int typeId, Date orderDate, int orderDays);

    boolean releaseInventory(int typeId, Date orderDate, int orderDays);

    int setReservedCount(int typeId, Date date, int reservedCount);

    int setLockedCount(int typeId, Date date, int lockedCount);

    int setTotalCount(int typeId, Date date, int totalCount);
}
