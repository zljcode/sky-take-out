package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Slf4j
public class ReportServiceimpl implements ReportService {

    //要查询订单表，所以要注入订单的Mapper
    @Autowired
    private OrderMapper orderMapper;

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
           turnover = turnover == null ? 0.0 :turnover;
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
}
