package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sky.utils.WeChatPayUtil;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceimpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional  //order订单表和orderdetail订单明细表应该是同一事物，所以需要进行事务注解
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //1、处理各种异常业务（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart = new ShoppingCart();

        //查询当前用户id，检查购物车数据是否为空
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartslist = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartslist == null || shoppingCartslist.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2、向订单表中插入一条数据
        Orders orders = new Orders();
        //属性拷贝
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));//使用时间戳作为订单号
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailLists = new ArrayList<>();
        //3、向订单明细表中插入n条数据  这里会使用到上面的订单实体类，所以上面的insert方法需要返回主键id
        //对购物车进行遍历
        for (ShoppingCart cart : shoppingCartslist) {
            OrderDetail orderDetail = new OrderDetail(); //订单明细
            BeanUtils.copyProperties(cart, orderDetail); //将购物车中的东西拷贝给订单明细
            orderDetail.setOrderId(orders.getId()); //设置当前订单明细关联的订单id

            //插入数据
            orderDetailLists.add(orderDetail); //将上面的数据插入列表中，可以省时
        }

        orderDetailMapper.insertBatch(orderDetailLists);

        //4、清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //5、封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
/*        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID; //支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单

        //发现没有将支付时间 check_out属性赋值，所以在这里更新
        LocalDateTime check_out_time = LocalDateTime.now();

        //获取订单号码
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        log.info("调用updateStatus，用于替换微信支付更新数据库状态的问题");
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orderNumber);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 查询历史订单
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQueryUser(int pageNum, int pageSize, Integer status) {
        /* 代码设计逻辑：
            1、页面肯定是要有分页的
            2、先获取userId再通过调用Mapper层的订单查询，来查询数据库中订单表，查询出该用户所有的订单罗列在主页面上，
            这里是将所有订单封装放在一个Page<Orders>泛型中
            3、建立一个泛型List<OrderVO>，订单视图对象泛型 用于存储订单明细
            4、将订单明细放在泛型中，作为当前页面的数据集合
            5、PageResult对象中有总记录数和数据集合两个参数，进行封装然后返回PageResult这个对象
        */
        //设置分页
        PageHelper.startPage(pageNum, pageSize);

        //封装该订单的所有人和状态
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);  //设置状态
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId()); //获取用户Id

        //分页条件查询  这里查询出所有的订单
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);  // 这里通过userId进行查询了

        List<OrderVO> list = new ArrayList<>();

        //查询出订单明细，使用OrderVO进行响应
        if (page != null && page.size() > 0) {
            //遍历主页面的订单列表
            for (Orders orders : page) {
                Long orderId = orders.getId();  // 获取列表中的订单Id

                //通过订单Id来查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);  //把订单中的属性复制到订单VO对象中
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO); //将订单封装对象添加到orderVO泛型中
            }
        }

        PageResult pageResult = new PageResult(page.getTotal(), list);

        return pageResult;
    }

    /**
     * 查询订单详情
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderVO queryOrder(Long orderId) {
/*  显示订单的所有信息  OrderVO中有订单菜品信息以及订单详情，
    用到Orders这个对象，因为它存储了很多的东西包括备注，餐具等*/

        //根据订单id查询订单
        Orders orders = orderMapper.getById(orderId);

        //查询该订单对应的菜品/套餐信息
        List<OrderDetail> orderDetailslist = orderDetailMapper.getByOrderId(orders.getId());

        //将获取到的东西封装到OrderVO进行返回  前端需要获得订单的基础信息以及订单中的细节信息
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailslist);

        return orderVO;
    }

    /* *//**
     * @param id
     * @return
     *//*
    public void userCancelById(Long id) throws Exception {
        //根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        //健壮性检查，检查订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //检查订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消  3往后的不允许取消订单
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        //订单处于待接单状态下取消，需要进行退款
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            //调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    ordersDB.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额

            //支付状态修改为 退款
            orders.setStatus(Orders.REFUND);
        }

        //更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setOrderTime(LocalDateTime.now());
        orderMapper.update(orders);
    }*/

    /**
     * 用户再来一单
     *
     * @param id
     * @return
     */
    @Override
    public void repetition(Long id) {
        //查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 通过id获取订单
        Orders orders = orderMapper.getById(id);

        //根据订单id获取订单详情
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());

        //将订单对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetails.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            //将原订单详情里面的菜品或套餐信息重新复制到购物车对象之中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        //将购物车对象批量插入数据库
        orderMapper.insertBatch(shoppingCartList);
    }

    /**
     * 管理端订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQueryAdmin(OrdersPageQueryDTO ordersPageQueryDTO) {
        //设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        //获取全部的订单存储在页面中
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //部分订单信息，需要额外返回订单菜品信息，所以将Orders转换为orderVO
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            //对获取到的列表结果进行遍历，再复制进视图对象
            for (Orders orders : ordersList) {
                //共同字段复制进视图对象
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                //设置orderVO中的订单菜品信息
                String orderDishes = getOrderDishesStr(orders);

                //将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        PageResult pageResult = new PageResult(page.getTotal(), orderVOList);
        return pageResult;

    }

    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 通过订单号查询数据库中菜品详情信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        //将订单对应的所有菜品信息拼接到一起
        return String.join("", orderDishList);
    }


    /**
     * 各状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //生成一个订单状态数量视图对象
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();

        //根据状态分别查询出待接单，待派送，派送中的订单数量
        Integer toBeConfirmed = orderMapper.status(Orders.TO_BE_CONFIRMED);
        //待派送数量
        Integer confirmed = orderMapper.status(Orders.CONFIRMED);
        //派送中数量
        Integer deliveryInProgress = orderMapper.status(Orders.DELIVERY_IN_PROGRESS);

        //将这些数量添加到orderStatisticsVO实例对象中
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO queryOrderDetail(Long id) {
        //根据订单id查询订单
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();

        //将订单的东西复制给订单视图对象
        BeanUtils.copyProperties(orders, orderVO);
        //根据订单id查询订单详情  这里查询的是所有的订单详情信息
        //List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderVO.getId());

        //根据订单id查询订单详情  匹配订单的那个id
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        orderVO.setOrderDetailList(orderDetails);

        return orderVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     * @return
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //判断接收到的信息不为空
        if (ordersConfirmDTO == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //根据id查询订单
        Orders orders = orderMapper.getById(ordersConfirmDTO.getId());
        //修改订单的状态  传过来的订单是待接单 2
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            orders.setStatus(Orders.CONFIRMED);  //设置成已接单
        }
        //写入到数据库订单表中
        orderMapper.update(orders);

    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO){
        //根据Id获取到具体订单
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());

        //将订单的拒单原因复制到订单信息之中
        BeanUtils.copyProperties(ordersRejectionDTO,orders);

        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            orders.setStatus(Orders.CANCELLED);
        }

        //更新订单id，拒单时间以及原因
        orders.setCancelTime(LocalDateTime.now());
        orders.setId(ordersRejectionDTO.getId());
        orders.setCancelReason(ordersRejectionDTO.getRejectionReason());

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO){
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
/*        if (payStatus == 1) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
        }*/

        // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间  只有显式赋值时才能回显
        Orders orders = new Orders();
        orders.setId(ordersCancelDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }


    /**
     * 派送订单
     *
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }


    /**
     * 完成订单
     *
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }
}

