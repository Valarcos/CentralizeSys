package com.centralizesys.util;

public class Constants {
    // We prevent instantiation because this is just a utility holder
    private Constants() {}

    // ERROR MESSAGES (Centralized)
    public static final String ERR_DEBT_NOT_FOUND = "La deuda solicitada no existe en el sistema.";
    public static final String ERR_PAYMENT_NEGATIVE = "El monto del pago no puede ser negativo.";

    // DB CONSTANTS
    public static final String DB_DATE_FORMAT = "yyyy-MM-dd"; // ISO Standard for SQLite
}