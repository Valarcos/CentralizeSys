package com.centralizesys.util;

public class Constants {
    // We prevent instantiation because this is just a utility holder
    private Constants() {}

    // ERROR MESSAGES (Centralized)
    public static final String ERR_DEBT_NOT_FOUND = "La deuda solicitada no existe en el sistema.";
    public static final String ERR_PAYMENT_NEGATIVE = "El monto del pago no puede ser negativo.";

    // PENDING SALE PAYMENT ERRORS
    public static final String ERR_PENDING_SALE_NOT_FOUND = "El pedido pendiente solicitado no existe.";
    public static final String ERR_PENDING_PAYMENT_EXCEEDS_BALANCE = "El monto del pago excede el saldo restante del pedido.";
    public static final String ERR_PENDING_FINALIZE_NO_PAYMENT = "No se puede finalizar un pedido sin al menos un pago registrado.";

    // DB CONSTANTS
    public static final String DB_DATE_FORMAT = "yyyy-MM-dd"; // ISO Standard for SQLite
}