package cn.mafangui.hotel.mapper;

import cn.mafangui.hotel.entity.RoomInventory;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public interface RoomInventoryMapper {

    /**
     * 按房型+日期查询单条库存
     */
    RoomInventory selectByTypeAndDate(@Param("typeId") int typeId,
                                       @Param("invDate") Date invDate);

    /**
     * 按房型+日期范围查询库存列表
     */
    List<RoomInventory> selectByTypeAndDateRange(@Param("typeId") int typeId,
                                                  @Param("startDate") Date startDate,
                                                  @Param("endDate") Date endDate);

    /**
     * INSERT IGNORE 创建库存行（唯一键冲突时静默跳过）
     */
    int insertIgnore(RoomInventory inventory);

    /**
     * 增加 ordered（下单占用）。Guard: 可订量 >= 1 且 version 匹配
     */
    int incrementOrdered(@Param("typeId") int typeId,
                         @Param("invDate") Date invDate,
                         @Param("version") int version);

    /**
     * 减少 ordered（取消释放）。Guard: ordered >= 1 且 version 匹配
     */
    int decrementOrdered(@Param("typeId") int typeId,
                         @Param("invDate") Date invDate,
                         @Param("version") int version);

    /**
     * ordered -> occupied（入住转换）。Guard: ordered >= 1 且 version 匹配
     */
    int convertOrderedToOccupied(@Param("typeId") int typeId,
                                  @Param("invDate") Date invDate,
                                  @Param("version") int version);

    /**
     * 减少 occupied（退房释放）。Guard: occupied >= 1 且 version 匹配
     */
    int decrementOccupied(@Param("typeId") int typeId,
                          @Param("invDate") Date invDate,
                          @Param("version") int version);

    /**
     * 设置保留房量（绝对值）。Guard: 设置后仍可订量 >= 0
     */
    int updateReserved(@Param("typeId") int typeId,
                       @Param("invDate") Date invDate,
                       @Param("reserved") int reserved,
                       @Param("version") int version);

    /**
     * 设置维修锁房（绝对值）。Guard: 设置后仍可订量 >= 0
     */
    int updateMaintenance(@Param("typeId") int typeId,
                          @Param("invDate") Date invDate,
                          @Param("maintenance") int maintenance,
                          @Param("version") int version);

    /**
     * 设置当日价格覆盖
     */
    int updatePrice(@Param("typeId") int typeId,
                    @Param("invDate") Date invDate,
                    @Param("price") java.math.BigDecimal price,
                    @Param("version") int version);

    /**
     * 更新 total（房型房间数变动时同步）
     */
    int updateTotal(@Param("typeId") int typeId,
                    @Param("invDate") Date invDate,
                    @Param("total") int total,
                    @Param("version") int version);
}
