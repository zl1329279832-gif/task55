package cn.mafangui.hotel.mapper;

import cn.mafangui.hotel.entity.RoomInventory;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public interface RoomInventoryMapper {

    int deleteByPrimaryKey(Integer inventoryId);

    int insert(RoomInventory record);

    int insertSelective(RoomInventory record);

    RoomInventory selectByPrimaryKey(Integer inventoryId);

    int updateByPrimaryKeySelective(RoomInventory record);

    int updateByPrimaryKey(RoomInventory record);

    List<RoomInventory> selectAll();

    RoomInventory selectByTypeAndDate(@Param("typeId") int typeId, @Param("date") Date date);

    List<RoomInventory> selectByTypeAndDateRange(@Param("typeId") int typeId,
                                                  @Param("startDate") Date startDate,
                                                  @Param("endDate") Date endDate);

    int incrementBookedCount(@Param("typeId") int typeId, @Param("date") Date date);

    int decrementBookedCount(@Param("typeId") int typeId, @Param("date") Date date);

    int updateReservedCount(@Param("typeId") int typeId,
                            @Param("date") Date date,
                            @Param("reservedCount") int reservedCount);

    int updateLockedCount(@Param("typeId") int typeId,
                          @Param("date") Date date,
                          @Param("lockedCount") int lockedCount);

    int updateTotalCount(@Param("typeId") int typeId,
                         @Param("date") Date date,
                         @Param("totalCount") int totalCount);

    int upsert(RoomInventory record);

    Integer countRoomsByType(@Param("typeId") int typeId);
}
