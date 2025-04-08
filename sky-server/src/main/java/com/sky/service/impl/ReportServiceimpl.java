package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceimpl implements ReportService {

    //要查询订单表，所以要注入订单的Mapper
    @Autowired
    private OrderMapper orderMapper;

    //要查询user表，所以注入user的Mapper
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间区间内的营业额数据
     *
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
/*        日期，以逗号分隔，例如：2022-10-01,2022-10-02,2022-10-03
        private String dateList;*/

        //创建一个集合用于存放从begin到end的所有天数
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            //日期从begin往后依次加一天 再加进集合中
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //这里是集合，但turnoverReportVO中存放的是字符串，所以需要对其进行转换 使用工具类，取出集合所有东西并且以逗号分割拼成字符串
        String datelists = StringUtils.join(dateList, ",");

        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询date日期对应的营业额数据，营业额是指：状态为“已完成” 5 的订单金额合计
            /*此处的难点是，order_time小于和大于什么时间  order_time是LocalDateTime的格式，也就是时分秒，而date的格式为
             * LocalDate 是时分，但我们一天的开始要从 0点0分0秒到23点59分59秒结束，所以需要将date格式进行转换一下
             * */
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            // select sum(amount) from orders where order_time > beginTime and order_time < endTime and status = 5
            // 这里需要传进三个参数order_time endTime  status 所以使用Map  返回的结果是amount 所以是double
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            /*
            当一天没有营业额时，Double turnover = orderMapper.sumByMap(map) 为空，空也被加入了，这样不合理，
            所以需要将null转成0
            */
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }


        //营业额集合转换为字符串
        String turnoverAll = StringUtils.join(turnoverList, ",");

        //对两个参数进行构造
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .turnoverList(turnoverAll)
                .dateList(datelists)
                .build();
        return turnoverReportVO;
    }

    /**
     * 统计指定时间区间内的用户数据
     *
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //创建一个集合用于存放从begin到end的所有天数
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            //日期从begin往后依次加一天 再加进集合中
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的新增用户数量 根据注册时间去查  select count(id) from user where create_time < ? and create_time > ?
        List<Integer> newuserList = new ArrayList<>();
        //存放每天的总用户数量  select count(id) from user where create_time < ?   写一个动态SQl去兼容上面的两个语句
        List<Integer> totaluserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap<>();
            //先把结束时间放进去，执行的是第二条SQL语句，做到了一个SQL语句两个使用的效果
            map.put("end", endTime);
            // 计算每天的总用户数量
            Integer totalUser = userMapper.countByMap(map);

            map.put("begin", beginTime);
            //新增用户数量
            Integer newUser = userMapper.countByMap(map);

            totaluserList.add(totalUser);
            newuserList.add(newUser);
        }

        //封装结果数据
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totaluserList, ","))
                .newUserList(StringUtils.join(newuserList, ","))
                .build();
    }


    /**
     * 统计指定时间区间内的订单数据
     *
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getordersStatistics(LocalDate begin, LocalDate end) {
        //创建一个集合用于存放从begin到end的所有天数
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            //日期从begin往后依次加一天 再加进集合中
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的订单总数
        List<Integer> orderCountList = new ArrayList<>();
        //存放每天的有效订单数
        List<Integer> validOrderList = new ArrayList<>();

        //遍历datelist集合，查询每天的有效订单数和订单总数
        for (LocalDate date : dateList) {
            // 查询每天的订单总数  select count(id) from orders where create_time < ? and create_time > ?
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            //查询每天的有效订单数 select count(id) from orders where create_time < ? and create_time > ? and status = 5
            Integer validorderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderList.add(validorderCount);
        }

        //计算时间区间内的订单总数量 reduce是合并
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //计算时间区间内的有效订单总数量
        Integer validOrderCount = validOrderList.stream().reduce(Integer::sum).get();

        //计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount !=0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }



        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderList, ","))
                .orderCompletionRate(orderCompletionRate)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .build();
    }

    /**
     * 根据条件统计订单数量
     *
     * @param begin
     * @param end
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);

        return orderMapper.countByMap(map);
    }

    /**
     * 统计指定时间区间内的销量排名前十
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        // order_detail表中有number字段，销售份额
        // 但order_detail表没有订单的状态字段，所以要连接orders这个数据库来查状态
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        //DTO转VO
        String nameList = StringUtils.join(salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()),",");
        String numberList = StringUtils.join(salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()),",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

}
