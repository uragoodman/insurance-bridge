package com.itheima.bolee.insurance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal status returned to the local payment result page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusVO {

    private String paymentStatus;
    private String message;

    public static PaymentStatusVO of(String status, String message) {
        return new PaymentStatusVO(status, message);
    }
}
