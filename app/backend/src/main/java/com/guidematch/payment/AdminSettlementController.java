package com.guidematch.payment;

import com.guidematch.payment.dto.PayoutRequest;
import com.guidematch.payment.dto.SettlementRow;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 정산 원장. /api/admin/** → hasRole("ADMIN") (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public List<SettlementRow> list() {
        return settlementService.listRows();
    }

    @PatchMapping("/{id}/payout")
    public void payout(@PathVariable Long id, @RequestBody(required = false) PayoutRequest request) {
        settlementService.markPaidOut(id, request != null ? request.adminMemo() : null);
    }
}
