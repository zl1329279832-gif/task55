package cn.mafangui.hotel.mapper;

import cn.mafangui.hotel.entity.CheckIn;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface CheckInMapper {
    Integer getCount();
    int deleteByPrimaryKey(Integer checkInId);

    int insert(CheckIn record);

    int insertSelective(CheckIn record);

    CheckIn selectByPrimaryKey(Integer checkInId);

    CheckIn selectLatestByRoomNumber(String roomNumber);

    CheckIn selectActiveByOrderId(@Param("orderId") Integer orderId);

    int updateByRoomNumber(String roomNumber);

    int checkOut(Integer checkInId);

    int updateByPrimaryKeySelective(CheckIn record);

    int updateByPrimaryKey(CheckIn record);

    List<CheckIn> selectAll();

}
