package cn.mafangui.hotel.service.impl;

import cn.mafangui.hotel.dto.DailyAvailability;
import cn.mafangui.hotel.entity.Room;
import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.mapper.RoomInventoryMapper;
import cn.mafangui.hotel.mapper.RoomMapper;
import cn.mafangui.hotel.mapper.RoomTypeMapper;
import cn.mafangui.hotel.service.RoomInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class RoomInventoryServiceImpl implements RoomInventoryService {

    private static final int MAX_RETRY = 3;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private RoomInventoryMapper inventoryMapper;

    @Autowired
    private RoomTypeMapper roomTypeMapper;

    @Autowired
    private RoomMapper roomMapper;

    // ================================================================
    //  房态查询
    // ================================================================

    @Override
    public List<DailyAvailability> queryAvailability(int typeId, Date startDate, Date endDate) {
        RoomType roomType = roomTypeMapper.selectByPrimaryKey(typeId);
        if (roomType == null) {
            return new ArrayList<>();
        }

        int days = daysBetween(startDate, endDate);
        if (days <= 0) {
            return new ArrayList<>();
        }

        // 懒初始化：确保日期范围内每天都有库存行
        ensureInventoryRange(typeId, startDate, days);

        List<RoomInventory> rows = inventoryMapper.selectByTypeAndDateRange(typeId, startDate, endDate);

        List<DailyAvailability> result = new ArrayList<>();
        for (RoomInventory inv : rows) {
            DailyAvailability da = new DailyAvailability();
            da.setDate(DATE_FMT.format(inv.getInvDate()));
            da.setTotalRooms(inv.getTotal() != null ? inv.getTotal() : 0);
            da.setAvailableRooms(inv.getAvailable());
            da.setReservedRooms(inv.getReserved() != null ? inv.getReserved() : 0);
            da.setMaintenanceRooms(inv.getMaintenance() != null ? inv.getMaintenance() : 0);
            da.setOrderedRooms(inv.getOrdered() != null ? inv.getOrdered() : 0);
            da.setOccupiedRooms(inv.getOccupied() != null ? inv.getOccupied() : 0);
            // 优先使用日期特定价格，否则使用房型基础价
            if (inv.getPrice() != null) {
                da.setPrice(inv.getPrice());
            } else {
                da.setPrice(roomType.getPrice() != null ? BigDecimal.valueOf(roomType.getPrice()) : null);
            }
            result.add(da);
        }
        return result;
    }

    // ================================================================
    //  下单占用库存（关键并发路径）
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int occupyForOrder(int typeId, Date startDate, int days) {
        RoomType roomType = roomTypeMapper.selectByPrimaryKey(typeId);
        if (roomType == null) {
            return -1;
        }

        // Phase 1: 确保所有日期的库存行存在
        ensureInventoryRange(typeId, startDate, days);

        // Phase 2: 逐天尝试 increment ordered（乐观锁重试）
        for (int i = 0; i < days; i++) {
            Date date = addDays(startDate, i);
            boolean success = optimisticIncrementOrdered(typeId, date);
            if (!success) {
                // 回滚：释放已占用的天数
                for (int j = 0; j < i; j++) {
                    Date rollbackDate = addDays(startDate, j);
                    retryingDecrementOrdered(typeId, rollbackDate);
                }
                return -2; // 至少有一天售罄
            }
        }
        return 1;
    }

    /**
     * 乐观锁重试：increment ordered
     */
    private boolean optimisticIncrementOrdered(int typeId, Date date) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null) {
                ensureInventoryRow(typeId, date);
                inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            }
            if (inv == null || inv.getAvailable() < 1) {
                return false; // 真正售罄
            }
            int affected = inventoryMapper.incrementOrdered(typeId, date, inv.getVersion());
            if (affected == 1) {
                return true;
            }
            // affected == 0: 版本冲突，重试
        }
        return false;
    }

    // ================================================================
    //  取消订单释放库存
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseForCancel(int typeId, Date startDate, int days) {
        for (int i = 0; i < days; i++) {
            Date date = addDays(startDate, i);
            retryingDecrementOrdered(typeId, date);
        }
        return 1;
    }

    /**
     * 乐观锁重试：decrement ordered
     */
    private void retryingDecrementOrdered(int typeId, Date date) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null || inv.getOrdered() == null || inv.getOrdered() < 1) {
                return; // 无占用，幂等跳过
            }
            int affected = inventoryMapper.decrementOrdered(typeId, date, inv.getVersion());
            if (affected == 1) {
                return;
            }
        }
        // 重试耗尽，记录日志（此处简化）
    }

    // ================================================================
    //  入住：ordered -> occupied
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int occupyForCheckIn(int typeId, Date startDate, int days) {
        for (int i = 0; i < days; i++) {
            Date date = addDays(startDate, i);
            boolean success = false;
            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
                if (inv == null || inv.getOrdered() == null || inv.getOrdered() < 1) {
                    // 无库存行或无 ordered：旧订单（系统上线前）优雅跳过
                    success = true;
                    break;
                }
                int affected = inventoryMapper.convertOrderedToOccupied(typeId, date, inv.getVersion());
                if (affected == 1) {
                    success = true;
                    break;
                }
            }
            if (!success) {
                return -2;
            }
        }
        return 1;
    }

    // ================================================================
    //  退房：释放 occupied
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseForCheckOut(int typeId, Date startDate, int days) {
        for (int i = 0; i < days; i++) {
            Date date = addDays(startDate, i);
            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
                if (inv == null || inv.getOccupied() == null || inv.getOccupied() < 1) {
                    break; // 无占用（旧数据），优雅跳过
                }
                int affected = inventoryMapper.decrementOccupied(typeId, date, inv.getVersion());
                if (affected == 1) {
                    break;
                }
            }
        }
        return 1;
    }

    // ================================================================
    //  操作员：设置保留房量
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setReserved(int typeId, Date date, int count) {
        if (count < 0) return -1;
        ensureInventoryRow(typeId, date);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null) return -1;
            int affected = inventoryMapper.updateReserved(typeId, date, count, inv.getVersion());
            if (affected == 1) return 1;
        }
        return -2;
    }

    // ================================================================
    //  操作员：设置维修锁房
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setMaintenance(int typeId, Date date, int count) {
        if (count < 0) return -1;
        ensureInventoryRow(typeId, date);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null) return -1;
            int affected = inventoryMapper.updateMaintenance(typeId, date, count, inv.getVersion());
            if (affected == 1) return 1;
        }
        return -2;
    }

    // ================================================================
    //  操作员：释放保留房量
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseReserved(int typeId, Date date, int count) {
        ensureInventoryRow(typeId, date);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null) return -1;
            int currentReserved = inv.getReserved() != null ? inv.getReserved() : 0;
            int newReserved = Math.max(0, currentReserved - count);
            int affected = inventoryMapper.updateReserved(typeId, date, newReserved, inv.getVersion());
            if (affected == 1) return 1;
        }
        return -2;
    }

    // ================================================================
    //  操作员：设置当日价格覆盖
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setPriceOverride(int typeId, Date date, BigDecimal price) {
        ensureInventoryRow(typeId, date);
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
            if (inv == null) return -1;
            int affected = inventoryMapper.updatePrice(typeId, date, price, inv.getVersion());
            if (affected == 1) return 1;
        }
        return -2;
    }

    // ================================================================
    //  懒初始化工具方法
    // ================================================================

    /**
     * 确保指定房型+日期的库存行存在。
     * 使用 INSERT IGNORE 处理并发竞争（唯一键保证不会重复）。
     */
    private void ensureInventoryRow(int typeId, Date date) {
        RoomInventory existing = inventoryMapper.selectByTypeAndDate(typeId, date);
        if (existing == null) {
            // 通过 room_info 表统计该房型实际房间数
            List<Room> rooms = roomMapper.selectByType(typeId);
            int totalCount = (rooms != null) ? rooms.size() : 0;

            RoomInventory inv = new RoomInventory();
            inv.setTypeId(typeId);
            inv.setInvDate(date);
            inv.setTotal(totalCount);
            inv.setPrice(null);
            inventoryMapper.insertIgnore(inv);
        }
    }

    /**
     * 确保日期范围内每天都有库存行
     */
    private void ensureInventoryRange(int typeId, Date startDate, int days) {
        for (int i = 0; i < days; i++) {
            ensureInventoryRow(typeId, addDays(startDate, i));
        }
    }

    // ================================================================
    //  日期工具方法
    // ================================================================

    /**
     * 日期加天数
     */
    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        // 归一化到日期（清除时间部分）
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 计算两个日期之间的天数差
     */
    private int daysBetween(Date start, Date end) {
        long diff = end.getTime() - start.getTime();
        return (int) (diff / (1000 * 60 * 60 * 24));
    }
}
