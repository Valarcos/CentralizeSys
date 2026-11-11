import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import CobrosYPedidosPage from './CobrosYPedidosPage';
import api from '../services/api';

vi.mock('../services/api', () => ({
    default: {
        get: vi.fn(),
        post: vi.fn(),
        delete: vi.fn()
    }
}));

vi.mock('react-hot-toast', () => ({
    default: {
        success: vi.fn(),
        error: vi.fn(),
        loading: vi.fn()
    }
}));

vi.mock('../services/receiptService', () => ({
    printDeudaReceipt: vi.fn(),
    printPendingReceipt: vi.fn(),
    printVentaReceipt: vi.fn()
}));

const pendingOrderItem = {
    tipo: 'PEDIDO',
    id_referencia: 634,
    venta_id: 634,
    cliente_id: 1,
    cliente_nombre: 'ADA',
    fecha_creacion: '2026-11-10T20:40:00',
    monto_total: 130800,
    monto_pagado: 130800,
    saldo_restante: 0,
    has_cheque: true
};

const renderPage = () => {
    const router = createMemoryRouter(
        [{ path: '/', element: <CobrosYPedidosPage /> }],
        { initialEntries: ['/'] }
    );
    return render(<RouterProvider router={router} />);
};

const setupDefaultMocks = ({ cheques = [], pagos = [] } = {}) => {
    api.get.mockImplementation((url) => {
        if (url === '/cobros-y-pedidos') return Promise.resolve({ data: [pendingOrderItem] });
        if (url === '/ventas/metodos-pago') {
            return Promise.resolve({
                data: [
                    { id: 1, descripcion: 'Efectivo', acronimo: 'EFEC', activo: true },
                    { id: 2, descripcion: 'Transferencia L. E.', acronimo: 'TBLE', activo: true },
                    { id: 5, descripcion: 'Cheque', acronimo: 'CHQ', activo: true }
                ]
            });
        }
        if (url === '/ventas/634/pagos') return Promise.resolve({ data: pagos });
        if (url === '/alertas/cheques/venta/634') return Promise.resolve({ data: cheques });
        return Promise.resolve({ data: [] });
    });
};

const getChequeRowMethodSelect = () => {
    const chequesSection = screen.getByText('💳 Cheques Asociados:').closest('.cheques-list');
    return within(chequesSection).getByDisplayValue('Seleccione Método...');
};

describe('CobrosYPedidosPage - PAGO Modal Cheque Undo Batching', () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    it('queues Deshacer Cobro and cancels all changes cleanly when Cancelar is clicked', async () => {
        setupDefaultMocks({
            cheques: [
                { id: 81, monto: 30800, fechaCobro: '2026-11-10', estado: 'COBRADO', metodoPagoNombre: 'Transferencia L. E.', pagoVentaId: 1118 }
            ],
            pagos: [
                { id: 1118, monto: 30800, metodo_pago_id: 2, fecha_pago: '2026-11-10T20:40:30', anulado: false }
            ]
        });

        renderPage();

        const pagoBtn = await screen.findByRole('button', { name: /Pago/i });
        fireEvent.click(pagoBtn);

        const undoBtn = await screen.findByRole('button', { name: /Deshacer/i });
        expect(screen.getByText('Cobro Cheque')).toBeInTheDocument();

        // Confirmar button starts disabled when no changes queued
        const confirmModalBtn = screen.getByRole('button', { name: 'Confirmar' });
        expect(confirmModalBtn).toBeDisabled();

        // Click Deshacer -> opens ConfirmationModal instead of firing API immediately
        fireEvent.click(undoBtn);
        expect(api.post).not.toHaveBeenCalled();

        // Accept confirmation modal -> queues locally and updates optimistic state
        fireEvent.click(screen.getByRole('button', { name: 'Deshacer Cobro' }));
        expect(api.post).not.toHaveBeenCalled();
        expect(screen.getByText('PENDIENTE')).toBeInTheDocument();
        expect(screen.queryByText('Cobro Cheque')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled();

        // Press Cancelar on the PAGO modal -> must discard without backend mutation
        fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
        expect(api.post).not.toHaveBeenCalled();
    });

    it('executes POST /cancelar-cobro only when Confirmar is pressed in PAGO modal', async () => {
        setupDefaultMocks({
            cheques: [
                { id: 81, monto: 30800, fechaCobro: '2026-11-10', estado: 'COBRADO', metodoPagoNombre: 'Transferencia L. E.', pagoVentaId: 1118 }
            ],
            pagos: [
                { id: 1118, monto: 30800, metodo_pago_id: 2, fecha_pago: '2026-11-10T20:40:30', anulado: false }
            ]
        });
        api.post.mockResolvedValue({});

        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: /Pago/i }));
        fireEvent.click(await screen.findByRole('button', { name: /Deshacer/i }));
        fireEvent.click(screen.getByRole('button', { name: 'Deshacer Cobro' }));

        expect(api.post).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

        await waitFor(() =>
            expect(api.post).toHaveBeenCalledWith('/alertas/cheques/81/cancelar-cobro')
        );
    });

    it('cancels out queued Cobrar + Deshacer locally without calling backend when confirmed/nothing else queued', async () => {
        setupDefaultMocks({
            cheques: [
                { id: 90, monto: 20000, fechaCobro: '2026-11-10', estado: 'PENDIENTE' }
            ]
        });

        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: /Pago/i }));
        await screen.findByText('PENDIENTE');

        // Select method inside Cheques Asociados and queue Cobrar locally
        fireEvent.change(getChequeRowMethodSelect(), { target: { value: '1' } });
        fireEvent.click(screen.getByRole('button', { name: /Cobrar/i }));

        expect(screen.getByText('COBRADO')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled();

        // Undo the queued local Cobrar before confirming
        fireEvent.click(screen.getByRole('button', { name: /Deshacer/i }));
        fireEvent.click(screen.getByRole('button', { name: 'Deshacer Cobro' }));

        expect(screen.getByText('PENDIENTE')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Confirmar' })).toBeDisabled();
        expect(api.post).not.toHaveBeenCalled();
    });

    it('runs cancelar-cobro before cobro when a COBRADO cheque is undone and re-cashed in the same batch', async () => {
        const callOrder = [];
        setupDefaultMocks({
            cheques: [
                { id: 81, monto: 30800, fechaCobro: '2026-11-10', estado: 'COBRADO', metodoPagoNombre: 'Transferencia L. E.', pagoVentaId: 1118 }
            ],
            pagos: [
                { id: 1118, monto: 30800, metodo_pago_id: 2, fecha_pago: '2026-11-10T20:40:30', anulado: false }
            ]
        });
        api.post.mockImplementation((url) => {
            callOrder.push(url);
            return Promise.resolve({});
        });

        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: /Pago/i }));
        fireEvent.click(await screen.findByRole('button', { name: /Deshacer/i }));
        fireEvent.click(screen.getByRole('button', { name: 'Deshacer Cobro' }));

        fireEvent.change(getChequeRowMethodSelect(), { target: { value: '1' } });
        fireEvent.click(screen.getByRole('button', { name: /Cobrar/i }));

        fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

        await waitFor(() => expect(callOrder).toEqual([
            '/alertas/cheques/81/cancelar-cobro',
            '/alertas/cheques/81/cobrar'
        ]));
        expect(api.post).toHaveBeenNthCalledWith(
            2,
            '/alertas/cheques/81/cobrar',
            null,
            { params: { metodoPagoId: 1 } }
        );
    });
});
