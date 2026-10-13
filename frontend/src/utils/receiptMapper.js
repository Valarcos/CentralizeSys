/**
 * Pure mappers that translate the authoritative backend receipt snapshots
 * (GET /ventas/{id}/receipt and GET /deudores/{id}/receipt) into the view-models
 * consumed by pdfGenerator.js. No I/O happens here.
 */

const UNKNOWN_METHOD_LABEL = 'Desconocido';
const DEFAULT_SELLER = 'Sistema';
const DEFAULT_SALE_TYPE = 'ESTÁNDAR';

const mapItems = (items) => (items || []).map(d => ({
    codigo: d.productoCodigo || d.codigoSnapshot,
    descripcion: d.productoNombre || d.descripcionSnapshot,
    quantity: d.cantidad,
    returnedQuantity: d.cantidadDevuelta || 0,
    unitPrice: d.precioLista || (d.precioUnitario + (d.descuentoValor || 0)),
    discount: d.descuentoValor || 0,
    reason: d.razonDescuento || null,
    subtotal: d.subtotal
}));

const mapSalePayments = (pagos, methods) => (pagos || []).map(p => {
    const method = (methods || []).find(m => m.id === p.metodoPagoId);
    return {
        name: (method && method.descripcion) ? method.descripcion : UNKNOWN_METHOD_LABEL,
        amount: p.monto,
        pagoId: p.id,
        date: p.fechaPago
    };
});

const nonNegative = (value) => Math.max(0, Number(value) || 0);

/**
 * A "cheque sale" is one whose payments were made exclusively through cheques:
 * there is at least one cheque and every registered payment is the cashing of one of them.
 */
const isChequeOnlySale = (pagos, cheques) => {
    if (!cheques || cheques.length === 0) return false;
    return (pagos || []).every(p => cheques.some(c => c.pagoVentaId && c.pagoVentaId === p.id));
};

/** Shape expected by generateReceipt (sale ticket). */
export const buildSaleReceiptData = (receipt, methods) => {
    const { venta, cheques } = receipt;
    return {
        id: venta.id,
        date: venta.fecha,
        client: venta.clienteNombre,
        vendedor: venta.vendedorNombre || DEFAULT_SELLER,
        saleType: venta.tipoVenta || DEFAULT_SALE_TYPE,
        items: mapItems(receipt.items),
        payments: mapSalePayments(receipt.pagos, methods),
        cheques: cheques || [],
        isCheque: isChequeOnlySale(receipt.pagos, cheques),
        total: receipt.totalVenta,
        globalDiscount: nonNegative(venta.descuentoGlobal),
        globalSurcharge: nonNegative(venta.recargoGlobal),
        saldoGenerado: nonNegative(venta.saldoGenerado)
    };
};

/** Shape expected by generatePendingSaleReceipt. Paid/remaining amounts come from the server. */
export const buildPendingReceiptData = (receipt, methods) => {
    const { venta } = receipt;
    return {
        id: venta.id,
        clienteNombre: venta.clienteNombre,
        fechaCreacion: venta.fecha,
        estado: venta.estado,
        montoTotal: receipt.totalVenta,
        montoPagado: receipt.totalPagado,
        saldoRestante: receipt.saldoRestante,
        vendedor: venta.vendedorNombre || DEFAULT_SELLER,
        items: mapItems(receipt.items),
        pagos: mapSalePayments(receipt.pagos, methods),
        cheques: receipt.cheques || [],
        globalDiscount: nonNegative(venta.descuentoGlobal),
        globalSurcharge: nonNegative(venta.recargoGlobal),
        descuentoGlobal: nonNegative(venta.descuentoGlobal),
        recargoGlobal: nonNegative(venta.recargoGlobal),
        saldoGenerado: nonNegative(venta.saldoGenerado)
    };
};

/** Shape expected by generateDebtorReceipt, built from a DeudaReceiptDto. */
export const buildDebtorReceiptData = (receipt, methods) => {
    const { deuda, venta } = receipt;
    return {
        id: deuda.id,
        ventaId: venta?.id ?? deuda.ventaId ?? null,
        clienteNombre: deuda.clienteNombre,
        fechaDeuda: deuda.fechaDeuda,
        estado: deuda.estado,
        montoOriginal: deuda.montoOriginal,
        montoDeuda: deuda.montoDeuda,
        saleDate: venta?.fecha ?? deuda.fechaDeuda,
        user: venta?.vendedorNombre || DEFAULT_SELLER,
        saleType: venta?.tipoVenta || DEFAULT_SALE_TYPE,
        items: mapItems(receipt.items),
        pagosDeuda: receipt.pagosDeuda || [],
        salePayments: mapSalePayments(venta?.pagos, methods),
        cheques: receipt.cheques || [],
        globalDiscount: nonNegative(venta?.descuentoGlobal),
        globalSurcharge: nonNegative(venta?.recargoGlobal),
        saldoGenerado: nonNegative(venta?.saldoGenerado)
    };
};
