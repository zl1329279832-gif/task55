package cn.mafangui.hotel.controller.common;

import cn.mafangui.hotel.dto.DailyAvailability;
import cn.mafangui.hotel.response.AjaxResult;
import cn.mafangui.hotel.response.ResponseTool;
import cn.mafangui.hotel.service.RoomInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 房态可用查询（公开接口，无需登录）
 */
@RestController
@RequestMapping(value = "/hotel/room")
public class AvailabilityController {

    @Autowired
    private RoomInventoryService inventoryService;

    /**
     * 按房型+日期范围查询每日可订量与价格摘要
     * GET /hotel/room/availability?typeId=1&startDate=2026-07-01&endDate=2026-07-10
     */
    @RequestMapping(value = "/availability")
    public AjaxResult queryAvailability(
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
}
