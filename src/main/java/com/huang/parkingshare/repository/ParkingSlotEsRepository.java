package com.huang.parkingshare.repository;

import com.huang.parkingshare.document.ParkingSlotDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ParkingSlotEsRepository extends ElasticsearchRepository<ParkingSlotDocument, Long> {
    // 自定义查询：只返回 status=1 且在地理距离内的车位，并按距离排序
    @Query("{\"bool\": {\"must\": [{\"term\": {\"status\": 1}}], \"filter\": {\"geo_distance\": {\"distance\": \"?2\", \"location\": {\"lat\": ?0, \"lon\": ?1}}}}}")
    List<ParkingSlotDocument> findAvailableNearby(double lat, double lon, String distance);
}