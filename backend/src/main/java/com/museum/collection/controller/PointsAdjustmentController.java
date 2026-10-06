package com.museum.collection.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.museum.collection.common.ApiResponse;
import com.museum.collection.entity.PointsAdjustment;
import com.museum.collection.service.PointsAdjustmentService;

/** 会员积分调整申请与复核接口 */
@RestController
@RequestMapping("/api/points-adjustments")
public class PointsAdjustmentController {

    private final PointsAdjustmentService adjustmentService;

    public PointsAdjustmentController(PointsAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @GetMapping
    public ApiResponse<List<PointsAdjustment>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long memberId) {
        return ApiResponse.success(adjustmentService.list(status, memberId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PointsAdjustment> get(@PathVariable Long id) {
        return ApiResponse.success(adjustmentService.get(id));
    }

    @PostMapping
    public ApiResponse<PointsAdjustment> create(@RequestBody PointsAdjustment input) {
        return ApiResponse.success("申请已提交，待复核", adjustmentService.create(input));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<PointsAdjustment> withdraw(@PathVariable Long id, @RequestParam String operator) {
        return ApiResponse.success("申请已撤回", adjustmentService.withdraw(id, operator));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PointsAdjustment> approve(@PathVariable Long id, @RequestParam String reviewer,
                                                 @RequestParam(required = false) String note) {
        return ApiResponse.success("复核通过，积分已更新", adjustmentService.approve(id, reviewer, note));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<PointsAdjustment> reject(@PathVariable Long id, @RequestParam String reviewer,
                                                @RequestParam String note) {
        return ApiResponse.success("已驳回", adjustmentService.reject(id, reviewer, note));
    }
}
