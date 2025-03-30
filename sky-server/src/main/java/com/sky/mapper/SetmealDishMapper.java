package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品Id查询对应的菜品id
     * @param dishIds
     * @return
     */
    // select setmeal_id from setmeal_dish where dish_id in (1，2，3，4)
    List<Long> getSetmealIdsByDishId(List<Long> dishIds);
}
