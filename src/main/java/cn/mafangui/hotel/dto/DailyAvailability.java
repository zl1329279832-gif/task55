package cn.mafangui.hotel.dto;

import java.math.BigDecimal;

/**
 * 每日房态可用摘要
 */
public class DailyAvailability {
    private String date;          // yyyy-MM-dd
    private int totalRooms;
    private int availableRooms;   // 可订 = total - ordered - occupied - reserved - maintenance
    private int reservedRooms;
    private int maintenanceRooms;
    private int orderedRooms;
    private int occupiedRooms;
    private BigDecimal price;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getTotalRooms() {
        return totalRooms;
    }

    public void setTotalRooms(int totalRooms) {
        this.totalRooms = totalRooms;
    }

    public int getAvailableRooms() {
        return availableRooms;
    }

    public void setAvailableRooms(int availableRooms) {
        this.availableRooms = availableRooms;
    }

    public int getReservedRooms() {
        return reservedRooms;
    }

    public void setReservedRooms(int reservedRooms) {
        this.reservedRooms = reservedRooms;
    }

    public int getMaintenanceRooms() {
        return maintenanceRooms;
    }

    public void setMaintenanceRooms(int maintenanceRooms) {
        this.maintenanceRooms = maintenanceRooms;
    }

    public int getOrderedRooms() {
        return orderedRooms;
    }

    public void setOrderedRooms(int orderedRooms) {
        this.orderedRooms = orderedRooms;
    }

    public int getOccupiedRooms() {
        return occupiedRooms;
    }

    public void setOccupiedRooms(int occupiedRooms) {
        this.occupiedRooms = occupiedRooms;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
