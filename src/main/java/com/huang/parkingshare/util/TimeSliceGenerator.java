package com.huang.parkingshare.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TimeSliceGenerator {

    /**
     * 生成某一天的所有时间片（15分钟粒度）
     * @param startTime 每日开始时间，如 08:00
     * @param endTime   每日结束时间，如 20:00
     * @return 时间片列表，每个元素为 int[]{startMinute, endMinute}
     */
    public static List<int[]> generateTimeSlices(LocalTime startTime, LocalTime endTime) {
        List<int[]> slices = new ArrayList<>();
        int startMinuteOfDay = startTime.getHour() * 60 + startTime.getMinute();
        int endMinuteOfDay = endTime.getHour() * 60 + endTime.getMinute();

        for (int minute = startMinuteOfDay; minute < endMinuteOfDay; minute += 15) {
            int sliceStart = minute;
            int sliceEnd = Math.min(minute + 15, endMinuteOfDay);
            slices.add(new int[]{sliceStart, sliceEnd});
        }
        return slices;
    }

    /**
     * 生成从起始日期开始的连续 N 天的日期列表
     * @param startDate 起始日期（包含）
     * @param days      天数
     * @return 日期列表
     */
    public static List<LocalDate> getDateRange(LocalDate startDate, int days) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dates.add(startDate.plusDays(i));
        }
        return dates;
    }

    public static List<Integer> getRequiredStartMinutes(LocalDateTime start, LocalDateTime end) {
        List<Integer> minutesList = new ArrayList<>();
        LocalDateTime current = start;
        while (current.isBefore(end)) {
            int minuteOfDay = current.getHour() * 60 + current.getMinute();
            int alignedStart = (minuteOfDay / 15) * 15;
            minutesList.add(alignedStart);
            current = current.plusMinutes(15);
        }
        return minutesList.stream().distinct().sorted().collect(Collectors.toList());
    }
}