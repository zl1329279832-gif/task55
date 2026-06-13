package cn.mafangui.hotel.controller.worker;

import cn.mafangui.hotel.dto.DailyAvailability;
import cn.mafangui.hotel.dto.InventoryOperationRequest;
import cn.mafangui.hotel.response.AjaxResult;
import cn.mafangui.hotel.response.ResponseTool;
import cn.mafangui.hotel.service.RoomInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 操作员库存管理（需 operator/admin 角色）
 */
@RestController
@RequestMapping(value = "/op/inventory")
public class OpInventoryController {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private RoomInventoryService inventoryService;

    /**
     * 房态日历完整视图
     * GET /op/inventory/calendar?typeId=1&startDate=2026-07-01&endDate=2026-07-31
     */
    @RequestMapping(value = "/calendar")
    public AjaxResult getCalendar(
            @RequestParam int typeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {

        if (typeId <= 0) {
            return ResponseTool.failed("房型ID无效");
        }
        if (startDate == null || endDate == null) {
            return ResponseTool.failed("请提供开始日期和结束日期");
        }
        if (!startDate.before(endDate)) {
            return ResponseTool.failed("开始日期必须早于结束日期");
        }

        List<DailyAvailability> result = inventoryService.queryAvailability(typeId, startDate, endDate);
        return ResponseTool.success(result);
    }

    /**
     * 设置保留房量
     * POST /op/inventory/reserved
     * Body: { "typeId": 1, "date": "2026-07-15", "count": 3 }
     */
    @RequestMapping(value = "/reserved", method = RequestMethod.POST)
    public AjaxResult setReserved(@RequestBody InventoryOperationRequest req) {
        if (req.getTypeId() == null || req.getDate() == null || req.getCount() == null) {
            return ResponseTool.failed("typeId、date 和 count 为必填项");
        }
        Date date = parseDate(req.getDate());
        if (date == null) {
            return ResponseTool.failed("日期格式无效，请使用 yyyy-MM-dd");
        }
        int result = inventoryService.setReserved(req.getTypeId(), date, req.getCount());
        if (result == 1) {
            return ResponseTool.success("保留房量已更新");
        } else if (result == -2) {
            return ResponseTool.failed("无法保留：超出可用容量（已有订单冲突）");
        }
        return ResponseTool.failed("参数无效");
    }

    /**
     * 设置维修锁房
     * POST /op/inventory/maintenance
     * Body: { "typeId": 1, "date": "2026-07-15", "count": 2 }
     */
    @RequestMapping(value = "/maintenance", method = RequestMethod.POST)
    public AjaxResult setMaintenance(@RequestBody InventoryOperationRequest req) {
        if (req.getTypeId() == null || req.getDate() == null || req.getCount() == null) {
            return ResponseTool.failed("typeId、date 和 count 为必填项");
        }
        Date date = parseDate(req.getDate());
        if (date == null) {
            return ResponseTool.failed("日期格式无效，请使用 yyyy-MM-dd");
        }
        int result = inventoryService.setMaintenance(req.getTypeId(), date, req.getCount());
        if (result == 1) {
            return ResponseTool.success("维修锁房已更新");
        } else if (result == -2) {
            return ResponseTool.failed("无法锁房：超出可用容量");
        }
        return ResponseTool.failed("参数无效");
    }

    /**
     * 释放保留房量
     * POST /op/inventory/release
     * Body: { "typeId": 1, "date": "2026-07-15", "count": 2 }
     */
    @RequestMapping(value = "/release", method = RequestMethod.POST)
    public AjaxResult releaseReserved(@RequestBody InventoryOperationRequest req) {
        if (req.getTypeId() == null || req.getDate() == null || req.getCount() == null) {
            return ResponseTool.failed("typeId、date 和 count 为必填项");
        }
        Date date = parseDate(req.getDate());
        if (date == null) {
            return ResponseTool.failed("日期格式无效，请使用 yyyy-MM-dd");
        }
        int result = inventoryService.releaseReserved(req.getTypeId(), date, req.getCount());
        if (result == 1) {
            return ResponseTool.success("保留房量已释放");
        }
        return ResponseTool.failed("释放失败");
    }

    /**
     * 设置当日价格覆盖
     * POST /op/inventory/price
     * Body: { "typeId": 1, "date": "2026-07-15", "price": 599.00 }
     */
    @RequestMapping(value = "/price", method = RequestMethod.POST)
    public AjaxResult setPriceOverride(@RequestBody InventoryOperationRequest req) {
        if (req.getTypeId() == null || req.getDate() == null || req.getPrice() == null) {
            return ResponseTool.failed("typeId、date 和 price 为必填项");
        }
        Date date = parseDate(req.getDate());
        if (date == null) {
            return ResponseTool.failed("日期格式无效，请使用 yyyy-MM-dd");
        }
        int result = inventoryService.setPriceOverride(req.getTypeId(), date, req.getPrice());
        if (result == 1) {
            return ResponseTool.success("价格覆盖已设置");
        }
        return ResponseTool.failed("价格更新失败");
    }

    /**
     * 日期解析工具
     */
    private Date parseDate(String dateStr) {
        try {
            return DATE_FMT.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
}
