package com.huang.parkingshare.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.entity.OwnerEarning;
import com.huang.parkingshare.mapper.OwnerEarningMapper;
import com.huang.parkingshare.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerEarningSettleTask {

    private final OwnerEarningMapper ownerEarningMapper;
    private final UserMapper userMapper;

    /**
     * 每天凌晨 2 点执行一次，结算所有待结算的业主收益
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "settleOwnerEarningLock", lockAtLeastFor = "5m", lockAtMostFor = "10m")
    public void settleOwnerEarnings() {
        log.info("开始执行业主收益结算定时任务");

        // 查询所有待结算记录（status = 0）
        LambdaQueryWrapper<OwnerEarning> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OwnerEarning::getStatus, 0);
        List<OwnerEarning> earnings = ownerEarningMapper.selectList(wrapper);

        if (earnings.isEmpty()) {
            log.info("没有待结算的业主收益记录");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (OwnerEarning earning : earnings) {
            try {
                // 原子增加业主余额（使用数据库行锁或乐观更新，直接加法即可）
                int marked = ownerEarningMapper.markAsSettled(earning.getId(), LocalDate.now());
                if (marked == 0) {
                    // 已被其他线程结算，跳过
                    continue;
                }
                // 再增加余额（此时状态已为1，即使余额增加失败也不会重试，但可记录异常手动处理）
                userMapper.addBalance(earning.getOwnerId(), earning.getAmount());

                successCount++;
            } catch (Exception e) {
                log.error("结算业主收益失败，收益记录 ID：{}", earning.getId(), e);
                failCount++;
            }
        }

        log.info("业主收益结算完成，成功：{}，失败：{}", successCount, failCount);
    }
}