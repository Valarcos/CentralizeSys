import api from './api';
import {
    generateReceipt,
    generateDebtorReceipt,
    generatePendingSaleReceipt
} from '../utils/pdfGenerator';
import {
    buildSaleReceiptData,
    buildPendingReceiptData,
    buildDebtorReceiptData
} from '../utils/receiptMapper';

/**
 * Receipt printing orchestration. Every receipt is built from ONE authoritative
 * server snapshot (`/receipt` endpoints) so PDFs can never mix data read at different instants.
 */

const resolveMethods = async (methods) => {
    if (methods && methods.length > 0) return methods;
    const res = await api.get('/ventas/metodos-pago');
    return res.data;
};

const fetchVentaReceipt = async (ventaId) => (await api.get(`/ventas/${ventaId}/receipt`)).data;

const fetchDeudaReceipt = async (deudaId) => (await api.get(`/deudores/${deudaId}/receipt`)).data;

/** Sale ticket (REMITO) for a finalized/active sale. */
export const printVentaReceipt = async (ventaId, { printItems = true, methods } = {}) => {
    const [receipt, resolvedMethods] = await Promise.all([fetchVentaReceipt(ventaId), resolveMethods(methods)]);
    generateReceipt(buildSaleReceiptData(receipt, resolvedMethods), { printItems });
};

/** Budget / payments summary for a PENDIENTE sale (pedido). */
export const printPendingReceipt = async (ventaId, { printItems = true, methods } = {}) => {
    const [receipt, resolvedMethods] = await Promise.all([fetchVentaReceipt(ventaId), resolveMethods(methods)]);
    generatePendingSaleReceipt(buildPendingReceiptData(receipt, resolvedMethods), { printItems });
};

/** Debt voucher for a debt id. */
export const printDeudaReceipt = async (deudaId, { printItems = true, methods } = {}) => {
    const [receipt, resolvedMethods] = await Promise.all([fetchDeudaReceipt(deudaId), resolveMethods(methods)]);
    generateDebtorReceipt(buildDebtorReceiptData(receipt, resolvedMethods), { printItems });
};

/** Prints the debt voucher when the sale generated a debt, the pending receipt if pending, otherwise the sale ticket. */
export const printVentaOrDeudaReceipt = async (ventaId, { printItems = true, methods } = {}) => {
    const receipt = await fetchVentaReceipt(ventaId);
    if (receipt.deuda) {
        await printDeudaReceipt(receipt.deuda.id, { printItems, methods });
        return;
    }
    const resolvedMethods = await resolveMethods(methods);
    if (receipt.venta?.estado === 'PENDIENTE') {
        generatePendingSaleReceipt(buildPendingReceiptData(receipt, resolvedMethods), { printItems });
        return;
    }
    generateReceipt(buildSaleReceiptData(receipt, resolvedMethods), { printItems });
};
