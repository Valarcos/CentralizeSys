import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CobrarChequesModal from './CobrarChequesModal';
import api from '../services/api';
import toast from 'react-hot-toast';

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
        error: vi.fn()
    }
}));

const paymentMethods = [
    { id: 1, descripcion: 'Efectivo', activo: true },
    { id: 2, descripcion: 'Transferencia L. E.', activo: true },
    { id: 5, descripcion: 'Cheque', activo: true }
];

const ventaItem = {
    venta_id: 635,
    cliente_nombre: 'ABUELA NICOLLE'
};

describe('CobrarChequesModal', () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    it('renders PENDIENTE, COBRADO, and ANULADA installments with distinct undo and annul actions', async () => {
        api.get.mockResolvedValueOnce({
            data: [
                { id: 82, monto: 100000, fechaCobro: '2099-11-20', estado: 'PENDIENTE' },
                { id: 81, monto: 30800, fechaCobro: '2026-11-01', estado: 'COBRADO', metodoPagoNombre: 'Efectivo' },
                { id: 80, monto: 15000, fechaCobro: '2026-10-15', estado: 'ANULADA' }
            ]
        });

        render(
            <CobrarChequesModal
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={vi.fn()}
                ventaItem={ventaItem}
                paymentMethods={paymentMethods}
            />
        );

        await waitFor(() => expect(screen.getByText('PENDIENTE')).toBeInTheDocument());

        expect(screen.getByText('COBRADO')).toBeInTheDocument();
        expect(screen.getByText('ANULADA')).toBeInTheDocument();
        expect(screen.getByText('❌ Anulado')).toBeInTheDocument();
        expect(screen.getByText('Método: Efectivo')).toBeInTheDocument();

        // Pending row exposes method selector, Cobrar, and Anular
        expect(screen.getByRole('button', { name: /Cobrar cuota de \$100000\.00/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Anular cheque de \$100000\.00/i })).toBeInTheDocument();

        // Cobrado row exposes Deshacer (not mislabeled "Anular") plus full cheque Anular
        expect(screen.getByRole('button', { name: /Deshacer cobro de \$30800\.00/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Anular cheque de \$30800\.00/i })).toBeInTheDocument();

        // Cheque method itself should not appear in collection destination dropdown
        expect(screen.queryByRole('option', { name: 'Cheque' })).not.toBeInTheDocument();
    });

    it('annuls a PENDIENTE cheque only after confirmation and triggers parent refresh', async () => {
        const onSuccess = vi.fn();
        api.get
            .mockResolvedValueOnce({
                data: [{ id: 82, monto: 100000, fechaCobro: '2099-11-20', estado: 'PENDIENTE' }]
            })
            .mockResolvedValueOnce({
                data: [{ id: 82, monto: 100000, fechaCobro: '2099-11-20', estado: 'ANULADA' }]
            });
        api.delete.mockResolvedValueOnce({});

        render(
            <CobrarChequesModal
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={onSuccess}
                ventaItem={ventaItem}
                paymentMethods={paymentMethods}
            />
        );

        const annulBtn = await screen.findByRole('button', { name: /Anular cheque de \$100000\.00/i });
        fireEvent.click(annulBtn);

        expect(screen.getByRole('heading', { name: 'Anular Cheque' })).toBeInTheDocument();

        // Cancelling confirmation performs zero DELETE calls
        fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
        expect(api.delete).not.toHaveBeenCalled();

        // Confirming executes DELETE /alertas/cheques/82 and refreshes
        fireEvent.click(annulBtn);
        fireEvent.click(screen.getByRole('button', { name: 'Anular Cheque' }));

        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/alertas/cheques/82'));
        await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
        expect(toast.success).toHaveBeenCalledWith('Cheque anulado exitosamente.');
        await waitFor(() => expect(screen.getByText('ANULADA')).toBeInTheDocument());
    });

    it('reverts a COBRADO cheque via Deshacer and supports direct annulment of COBRADO cheques', async () => {
        const onSuccess = vi.fn();
        api.get
            .mockResolvedValueOnce({
                data: [{ id: 81, monto: 30800, fechaCobro: '2026-11-01', estado: 'COBRADO', metodoPagoNombre: 'Efectivo' }]
            })
            .mockResolvedValueOnce({
                data: [{ id: 81, monto: 30800, fechaCobro: '2026-11-01', estado: 'PENDIENTE' }]
            })
            .mockResolvedValueOnce({
                data: [{ id: 81, monto: 30800, fechaCobro: '2026-11-01', estado: 'ANULADA' }]
            });
        api.post.mockResolvedValueOnce({});
        api.delete.mockResolvedValueOnce({});

        render(
            <CobrarChequesModal
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={onSuccess}
                ventaItem={ventaItem}
                paymentMethods={paymentMethods}
            />
        );

        // Step 1: Deshacer on COBRADO cheque -> POST /cancelar-cobro
        const undoBtn = await screen.findByRole('button', { name: /Deshacer cobro de \$30800\.00/i });
        fireEvent.click(undoBtn);
        expect(screen.getByRole('heading', { name: 'Deshacer Cobro de Cheque' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Deshacer Cobro' }));

        await waitFor(() => expect(api.post).toHaveBeenCalledWith('/alertas/cheques/81/cancelar-cobro'));
        await waitFor(() => expect(screen.getByText('PENDIENTE')).toBeInTheDocument());

        // Step 2: Annul the reverted cheque -> DELETE /alertas/cheques/81
        fireEvent.click(screen.getByRole('button', { name: /Anular cheque de \$30800\.00/i }));
        fireEvent.click(screen.getByRole('button', { name: 'Anular Cheque' }));

        await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/alertas/cheques/81'));
        expect(onSuccess).toHaveBeenCalledTimes(2);
    });

    it('annuls a COBRADO cheque directly with payment-reversal warning and double-submit guard', async () => {
        let resolveDelete;
        api.get
            .mockResolvedValueOnce({
                data: [{ id: 83, monto: 30800, fechaCobro: '2026-11-01', estado: 'COBRADO', metodoPagoNombre: 'Efectivo' }]
            })
            .mockResolvedValueOnce({
                data: [{ id: 83, monto: 30800, fechaCobro: '2026-11-01', estado: 'ANULADA' }]
            });
        api.delete.mockImplementationOnce(() => new Promise(resolve => { resolveDelete = resolve; }));

        render(
            <CobrarChequesModal
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={vi.fn()}
                ventaItem={ventaItem}
                paymentMethods={paymentMethods}
            />
        );

        const annulBtn = await screen.findByRole('button', { name: /Anular cheque de \$30800\.00/i });
        fireEvent.click(annulBtn);

        expect(screen.getByRole('heading', { name: 'Anular Cheque Cobrado' })).toBeInTheDocument();
        const confirmAnnulBtn = screen.getByRole('button', { name: 'Anular Cheque y Pago' });
        fireEvent.click(confirmAnnulBtn);
        fireEvent.click(confirmAnnulBtn);

        await waitFor(() => expect(api.delete).toHaveBeenCalledTimes(1));
        screen.getAllByRole('button', { name: 'Cerrar' }).forEach(btn => expect(btn).toBeDisabled());
        expect(screen.getByRole('button', { name: /Anular cheque de \$30800\.00/i })).toBeDisabled();

        resolveDelete({});
        await waitFor(() => expect(screen.getByText('ANULADA')).toBeInTheDocument());
    });

    it('blocks rapid synchronous double-click on Cobrar via isSubmittingRef before async state disables UI', async () => {
        let resolvePost;
        api.get
            .mockResolvedValueOnce({
                data: [{ id: 84, monto: 50000, fechaCobro: '2099-11-20', estado: 'PENDIENTE' }]
            })
            .mockResolvedValueOnce({
                data: [{ id: 84, monto: 50000, fechaCobro: '2099-11-20', estado: 'COBRADO', metodoPagoNombre: 'Efectivo' }]
            });
        api.post.mockImplementationOnce(() => new Promise(resolve => { resolvePost = resolve; }));

        render(
            <CobrarChequesModal
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={vi.fn()}
                ventaItem={ventaItem}
                paymentMethods={paymentMethods}
            />
        );

        const select = await screen.findByLabelText(/Método de pago para cuota del/i);
        fireEvent.change(select, { target: { value: '1' } });

        const cobrarBtn = screen.getByRole('button', { name: /Cobrar cuota de \$50000\.00/i });
        fireEvent.click(cobrarBtn);
        fireEvent.click(cobrarBtn);

        await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
        expect(cobrarBtn).toBeDisabled();
        expect(screen.getByRole('button', { name: /Anular cheque de \$50000\.00/i })).toBeDisabled();

        resolvePost({});
        await waitFor(() => expect(screen.getByText('COBRADO')).toBeInTheDocument());
    });
});
