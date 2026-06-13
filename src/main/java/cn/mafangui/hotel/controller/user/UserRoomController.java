package cn.mafangui.hotel.controller.user;

import cn.mafangui.hotel.entity.RoomInventory;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.response.AjaxResult;
import cn.mafangui.hotel.response.ResponseTool;
import cn.mafangui.hotel.service.RoomInventoryService;
import cn.mafangui.hotel.service.RoomTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping(value = "/hotel/room")
public class UserRoomController {

    @Autowired
    private RoomTypeService roomTypeService;
    @Autowired
    private RoomInventoryService roomInventoryService;

    /**
     * 所有房型
     * @return
     */
    @RequestMapping(value = "")
    public AjaxResult getAllRoomType(){
        List<RoomType> rooms = roomTypeService.findAllType();
        return ResponseTool.success(rooms);
    }

    /**
     * 查找有余量的房型
     * @return
     */
    @RequestMapping(value = "/rest")
    public AjaxResult findAllRestRoomType(){
        return ResponseTool.success(roomTypeService.findAllRestType());
    }

    /**
     * 根据id查找房型
     * @param typeId
     * @return
     */
    @RequestMapping(value = "/{typeId}")
    public AjaxResult getById(@PathVariable int typeId){
        return ResponseTool.success(roomTypeService.selectById(typeId));
    }

    /**
     * 房态日历：按房型和日期范围查询每日可订数量和价格
     */
    @RequestMapping(value = "/calendar")
    public AjaxResult getCalendar(int typeId,
                                  @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                                  @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<RoomInventory> inventoryList =
                roomInventoryService.getCalendar(typeId, startDate, endDate);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RoomInventory inv : inventoryList) {
            Map<String, Object> day = new HashMap<>();
            day.put("date", inv.getDate());
            day.put("available", inv.getTotalCount() - inv.getReservedCount()
                    - inv.getLockedCount() - inv.getBookedCount());
            day.put("price", inv.getBasePrice());
            result.add(day);
        }
        return ResponseTool.success(result);
    }


}
