package cn.mafangui.hotel.entity;

import java.util.Date;

public class RoomInventory {
    private Integer inventoryId;

    private Integer typeId;

    private Date date;

    private Integer totalCount;

    private Integer reservedCount;

    private Integer lockedCount;

    private Integer bookedCount;

    private Double basePrice;

    private Date createTime;

    private Date updateTime;

    public RoomInventory() {
        this.totalCount = 0;
        this.reservedCount = 0;
        this.lockedCount = 0;
        this.bookedCount = 0;
    }

    public RoomInventory(Integer typeId, Date date, Integer totalCount, Double basePrice) {
        this.typeId = typeId;
        this.date = date;
        this.totalCount = totalCount;
        this.reservedCount = 0;
        this.lockedCount = 0;
        this.bookedCount = 0;
        this.basePrice = basePrice;
    }

    public Integer getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Integer inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getReservedCount() {
        return reservedCount;
    }

    public void setReservedCount(Integer reservedCount) {
        this.reservedCount = reservedCount;
    }

    public Integer getLockedCount() {
        return lockedCount;
    }

    public void setLockedCount(Integer lockedCount) {
        this.lockedCount = lockedCount;
    }

    public Integer getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(Integer bookedCount) {
        this.bookedCount = bookedCount;
    }

    public Double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Double basePrice) {
        this.basePrice = basePrice;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "RoomInventory{" +
                "inventoryId=" + inventoryId +
                ", typeId=" + typeId +
                ", date=" + date +
                ", totalCount=" + totalCount +
                ", reservedCount=" + reservedCount +
                ", lockedCount=" + lockedCount +
                ", bookedCount=" + bookedCount +
                ", basePrice=" + basePrice +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
