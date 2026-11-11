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
    public static final String ERR_CANNOT_DELETE_CASHED_CHEQUE_PAYMENT = "No se puede eliminar el pago porque proviene del cobro de un cheque. Para cancelarlo, anule el cobro del cheque desde el módulo de Cobros y Pedidos.";
    public static final String ERR_VENTA_CONCURRENT_MODIFICATION = "La venta no se encuentra activa o pendiente, o fue modificada concurrentemente.";

    // DB CONSTANTS
    public static final String DB_DATE_FORMAT = "yyyy-MM-dd"; // ISO Standard for SQLite

    // SECURITY ERRORS
    public static final String ERR_UNAUTHORIZED = "Su sesión ha expirado o es inválida. Por favor, inicie sesión nuevamente.";
    public static final String ERR_ACCESS_DENIED = "Acceso denegado: No tiene permiso para realizar esta acción.";
}