package cn.mafangui.hotel.controller.worker;

import cn.mafangui.hotel.response.AjaxResult;
import cn.mafangui.hotel.response.ResponseTool;
import cn.mafangui.hotel.service.RoomInventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping(value = "/op/inventory")
public class OpInventoryController {

    @Autowired
    private RoomInventoryService roomInventoryService;

    /**
     * 查询库存详情
     */
    @RequestMapping(value = "")
    public AjaxResult getInventory(int typeId,
                                   @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                   @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return ResponseTool.success(
                roomInventoryService.getInventoryDetails(typeId, startDate, endDate));
    }

    /**
     * 初始化库存
     */
    @RequestMapping(method = RequestMethod.POST, value = "/init")
    public AjaxResult initInventory(int typeId,
                                    @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                    @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        int count = roomInventoryService.initInventory(typeId, startDate, endDate);
        if (count <= 0) return ResponseTool.failed("初始化失败");
        return ResponseTool.success("初始化成功，共" + count + "天");
    }

    /**
     * 设置预留房量
     */
    @RequestMapping(method = RequestMethod.POST, value = "/reserve")
    public AjaxResult setReserved(int typeId,
                                  @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
                                  int reservedCount) {
        int result = roomInventoryService.setReservedCount(typeId, date, reservedCount);
        if (result != 1) return ResponseTool.failed("设置预留失败，可能超出可用数量");
        return ResponseTool.success("设置预留成功");
    }

    /**
     * 维修锁房
     */
    @RequestMapping(method = RequestMethod.POST, value = "/lock")
    public AjaxResult setLocked(int typeId,
                                @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
                                int lockedCount) {
        int result = roomInventoryService.setLockedCount(typeId, date, lockedCount);
        if (result != 1) return ResponseTool.failed("锁定失败，可能超出可用数量");
        return ResponseTool.success("锁定成功");
    }

    /**
     * 临时放量
     */
    @RequestMapping(method = RequestMethod.POST, value = "/release")
    public AjaxResult adjustTotal(int typeId,
                                  @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
                                  int totalCount) {
        int result = roomInventoryService.setTotalCount(typeId, date, totalCount);
        if (result != 1) return ResponseTool.failed("调整失败");
        return ResponseTool.success("调整成功");
    }
}
