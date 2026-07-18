package com.guidematch.admin;

import org.springframework.web.bind.annotation.*;

/** 관리자 예약 조회 API. /api/admin/** → hasRole("ADMIN"). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(AdminBookingService adminBookingService) {
        this.adminBookingService = adminBookingService;
    }

    @GetMapping
    public AdminUserService.PageResult<AdminBookingService.BookingRow> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminBookingService.list(status, page, size);
    }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) { adminBookingService.cancel(id); }
}
