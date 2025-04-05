package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceimpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加商品到购物车
     *
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断当前加入到购物车中的商品是否已经存在了
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        // shoppingCartDTO没有userId ,所以需要将拦截器中的userId给拿过来
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //如果已经存在了，只需要将数量加一
        if (list != null && list.size() > 0) {
            //通过上面的条件只有可能查到一条数据，所以直接取唯一的一条数据
            ShoppingCart shoppingCart1 = list.get(0);
            //数量加一  update shopping_cart set number =? where id =?
            shoppingCart1.setNumber(shoppingCart1.getNumber() + 1);
            shoppingCartMapper.updateNumberById(shoppingCart1);
        } else {
            //如果不存在，需要插入一条购物车数据

            //判断本次添加到购物车的是菜品还是套餐
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {
                //本次添加到购物车的是菜品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());

            } else {
                //本次添加到购物车的是套餐
                Long setmealId = shoppingCartDTO.getSetmealId();

                Setmeal setmeal = setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());

            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }


    /**
     * 查看购物车
     *
     * @return
     */
    public List<ShoppingCart> showShoppingCart() {
        //获取到当前微信用户的id
        Long userId = BaseContext.getCurrentId();
        //构造一个购物车对象
        ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }

    /**
     * 清空购物车
     */
    public void cleanShoppingCart() {
        //获取当前微信用户的id
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * 删除购物车中的一个商品
     *
     * @param shoppingCartDTO
     * @return
     */
    public void removeCartItem(ShoppingCartDTO shoppingCartDTO) {
        // ShoppingCartDTO接收前端发送会的数据，当删除菜品数据时，接收到dishId

        //创建一个空的购物车对象，用于接收shoppingCartDTO的数据
        ShoppingCart shoppingCart = new ShoppingCart();
        //将ShoppingCartDTO中的参数拷贝到ShoppingCart中  避免直接操作DTO对象
        /*
        DTO: 传输层对象，适配接口参数
        Entity: 持久层对象，对应数据库结构
        */
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        //设置查询条件，查询当前登录客户的购物车数据
        shoppingCart.setUserId(BaseContext.getCurrentId());

        //查询获取当前购物车中的商品数据
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //判断当前购物车中是否为空
        if (list != null && list.size() > 0) {
            //获取购物车中的商品数据，这个商品根据 dishId UserId的限制，所以它是唯一的，所以只查第一个就行了 list中也只有唯一的数据
            shoppingCart = list.get(0);

            //获取当前购物车中的要删除商品的数量
            Integer number = shoppingCart.getNumber();
            if (number == 1) {
                //当前商品在购物车中的分数为1，直接删除当前记录
                shoppingCartMapper.deleteById(shoppingCart.getId());
            } else {
                //当前商品在购物车中的份数不为1，修改份数即可
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }
        }

    }
}
