package cn.mafangui.hotel.service;

import cn.mafangui.hotel.dto.DailyAvailability;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface RoomInventoryService {

    /**
     * 查询某房型在日期范围内的每日可用摘要
     */
    List<DailyAvailability> queryAvailability(int typeId, Date startDate, Date endDate);

    /**
     * 设置保留房量（绝对值）。返回 1=成功, -1=参数错误, -2=容量不足
     */
    int setReserved(int typeId, Date date, int count);

    /**
     * 设置维修锁房（绝对值）。返回 1=成功, -1=参数错误, -2=容量不足
     */
    int setMaintenance(int typeId, Date date, int count);

    /**
     * 释放保留房量（减少 reserved）。返回 1=成功, -2=失败
     */
    int releaseReserved(int typeId, Date date, int count);

    /**
     * 设置当日价格覆盖。返回 1=成功, -2=失败
     */
    int setPriceOverride(int typeId, Date date, BigDecimal price);

    /**
     * 下单占用库存：对日期范围内每天 increment ordered。
     * 返回 1=成功, -1=房型不存在, -2=某天售罄
     */
    int occupyForOrder(int typeId, Date startDate, int days);

    /**
     * 取消订单释放库存：对日期范围内每天 decrement ordered。
     * 返回 1=成功
     */
    int releaseForCancel(int typeId, Date startDate, int days);

    /**
     * 入住转换 ordered->occupied：不改变可订量。
     * 返回 1=成功, -2=转换失败
     */
    int occupyForCheckIn(int typeId, Date startDate, int days);

    /**
     * 退房释放 occupied。返回 1=成功
     */
    int releaseForCheckOut(int typeId, Date startDate, int days);
}
