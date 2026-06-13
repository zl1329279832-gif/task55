package cn.mafangui.hotel.entity;

import java.math.BigDecimal;
import java.util.Date;

public class RoomInventory {
    private Integer id;
    private Integer typeId;
    private Date invDate;
    private Integer total;
    private Integer ordered;
    private Integer occupied;
    private Integer reserved;
    private Integer maintenance;
    private BigDecimal price;
    private Integer version;
    private Date createTime;
    private Date updateTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public Date getInvDate() {
        return invDate;
    }

    public void setInvDate(Date invDate) {
        this.invDate = invDate;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getOrdered() {
        return ordered;
    }

    public void setOrdered(Integer ordered) {
        this.ordered = ordered;
    }

    public Integer getOccupied() {
        return occupied;
    }

    public void setOccupied(Integer occupied) {
        this.occupied = occupied;
    }

    public Integer getReserved() {
        return reserved;
    }

    public void setReserved(Integer reserved) {
        this.reserved = reserved;
    }

    public Integer getMaintenance() {
        return maintenance;
    }

    public void setMaintenance(Integer maintenance) {
        this.maintenance = maintenance;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    /**
     * 计算当天可订量: total - ordered - occupied - reserved - maintenance
     */
    public int getAvailable() {
        int t = (total != null) ? total : 0;
        int o = (ordered != null) ? ordered : 0;
        int oc = (occupied != null) ? occupied : 0;
        int r = (reserved != null) ? reserved : 0;
        int m = (maintenance != null) ? maintenance : 0;
        return t - o - oc - r - m;
    }
}
