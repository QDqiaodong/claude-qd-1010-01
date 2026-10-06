package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.PointsAdjustment;

public interface PointsAdjustmentRepository extends JpaRepository<PointsAdjustment, Long> {

    List<PointsAdjustment> findByStatusOrderByIdDesc(String status);

    List<PointsAdjustment> findByMemberIdOrderByIdDesc(Long memberId);

    /** 悲观写锁：复核 / 撤回前锁定申请行，并发复核只有先到者生效，后到者看到明确的已处理结果 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointsAdjustment a where a.id = :id")
    Optional<PointsAdjustment> findByIdForUpdate(@Param("id") Long id);
}
