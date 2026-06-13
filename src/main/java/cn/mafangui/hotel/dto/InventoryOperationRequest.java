package cn.mafangui.hotel.dto;

import java.math.BigDecimal;

/**
 * 操作员库存操作请求参数
 */
public class InventoryOperationRequest {
    private Integer typeId;
    private String date;        // yyyy-MM-dd（单日操作）
    private String startDate;   // yyyy-MM-dd（范围操作）
    private String endDate;     // yyyy-MM-dd（范围操作）
    private Integer count;      // 保留/维修数量
    private BigDecimal price;   // 价格覆盖

    public Integer getTypeId() {
        return typeId;
    }

    public void setTypeId(Integer typeId) {
        this.typeId = typeId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
