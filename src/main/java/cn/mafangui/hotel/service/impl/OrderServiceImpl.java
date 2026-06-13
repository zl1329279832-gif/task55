package cn.mafangui.hotel.service.impl;

import cn.mafangui.hotel.entity.Order;
import cn.mafangui.hotel.entity.Room;
import cn.mafangui.hotel.entity.RoomType;
import cn.mafangui.hotel.enums.OrderStatus;
import cn.mafangui.hotel.mapper.OrderMapper;
import cn.mafangui.hotel.mapper.RoomMapper;
import cn.mafangui.hotel.mapper.RoomTypeMapper;
import cn.mafangui.hotel.service.OrderService;
import cn.mafangui.hotel.service.RoomInventoryService;
import cn.mafangui.hotel.service.RoomService;
import cn.mafangui.hotel.service.RoomTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RoomTypeService roomTypeService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomInventoryService roomInventoryService;

    @Override
    public int insert(Order order) {
        return orderMapper.insert(order);
    }

    @Override
    public int addOrder(Order order) {
        return orderMapper.insertSelective(order);
    }

    @Override
    public int delete(Integer orderId) {
        return orderMapper.deleteByPrimaryKey(orderId);
    }

    @Override
    public Order selectById(Integer orderId) {
        return orderMapper.selectByPrimaryKey(orderId);
    }

    @Override
    public Order selectByNameAndPhone(String name, String phone) {
        Order order = new Order();
        order.setName(name);
        order.setPhone(phone);
        order.setOrderStatus(OrderStatus.PAID.getCode());
        return orderMapper.selectByNameAndPhone(order);
    }

    @Override
    public int update(Order order) {
        return orderMapper.updateByPrimaryKeySelective(order);
    }

    /**
     * 订单支付
     *  1.原子更新订单状态 UNPAID→PAID（CAS，防止并发重复支付）
     *  2.占用日期库存
     *  3.修改房型余量
     * @param orderId
     * @return 1=成功, -2=库存不足, -3=订单不存在或状态非法
     */
    @Override
    @Transactional
    public int payOrder(int orderId) {
        // 先读取订单用于后续库存操作
        Order order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null || order.getOrderStatus() != OrderStatus.UNPAID.getCode()) {
            return -3;
        }
        // 原子 CAS：UNPAID→PAID，并发时只有一个线程能成功
        int casResult = orderMapper.casUpdateStatus(orderId, OrderStatus.UNPAID.getCode(), OrderStatus.PAID.getCode());
        if (casResult != 1) {
            return -3;
        }
        // 按日期占用房态库存（跨天入住逐天占用）
        if (order.getOrderDate() != null && order.getOrderDays() != null && order.getOrderDays() > 0) {
            int invResult = roomInventoryService.occupyForOrder(order.getRoomTypeId(), order.getOrderDate(), order.getOrderDays());
            if (invResult != 1) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return -2;
            }
        }
        if (roomTypeService.updateRest(order.getRoomTypeId(),-1) != 1){
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return -2;
        }
        return 1;
    }

    /**
     * 取消订单
     * 仅允许从 UNPAID 或 PAID 状态取消。
     * PAID 订单取消时释放日期库存和房型余量；UNPAID 订单仅改状态。
     * @param orderId
     * @return 1=成功, -2=更新失败, -3=订单不存在或状态不允许取消
     */
    @Override
    @Transactional
    public int cancelOrder(int orderId) {
        Order order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null) return -3;
        // 仅 UNPAID 或 PAID 订单可取消
        int status = order.getOrderStatus();
        if (status != OrderStatus.UNPAID.getCode() && status != OrderStatus.PAID.getCode()) {
            return -3;
        }
        // 仅 PAID 订单才需要释放库存（UNPAID 从未占用过库存）
        if (status == OrderStatus.PAID.getCode()) {
            if (order.getOrderDate() != null && order.getOrderDays() != null && order.getOrderDays() > 0) {
                roomInventoryService.releaseForCancel(order.getRoomTypeId(), order.getOrderDate(), order.getOrderDays());
            }
            if (roomTypeService.updateRest(order.getRoomTypeId(), 1) != 1) {
                return -2;
            }
        }
        order.setOrderStatus(OrderStatus.WAS_CANCELED.getCode());
        return orderMapper.updateByPrimaryKeySelective(order);
    }

    @Override
    public Integer getOrderCount() {
        return orderMapper.getOrderCount();
    }

    @Override
    public List<Order> selectByUserId(int userId) {
        return orderMapper.selectByUserId(userId);
    }

    @Override
    public List<Order> AllOrders() {
        return orderMapper.selectAll();
    }

    @Override
    public List<Order> UsersAllOrders(int userId) {
        return orderMapper.selectAllByUser(userId, OrderStatus.WAS_DELETED.getCode());
    }


}
