import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { formatDate } from '../utils/format';
import toast from 'react-hot-toast';
import ConfirmationModal from './ConfirmationModal';
import './CobrarChequesModal.css';

/**
 * CobrarChequesModal — Blueprint §4 & §5 "The Custom Pago Modal".
 *
 * Displays all individual cheque installments (alertas_cheques) for a given venta.
 * - PENDIENTE installments can be collected (POST /alertas/cheques/{id}/cobrar) or annulled (DELETE /alertas/cheques/{id}).
 * - COBRADO installments allow reversing the collection back to PENDIENTE (POST /alertas/cheques/{id}/cancelar-cobro)
 *   or annulling the cheque completely (DELETE /alertas/cheques/{id}).
 *
 * @param {boolean} isOpen
 * @param {function} onClose
 * @param {function} onSuccess  - Called after any successful mutation so the parent can refresh
 * @param {object}  ventaItem   - The row from CobrosYPedidosPage (needs venta_id, cliente_nombre)
 * @param {Array}   paymentMethods  - Pre-loaded list of { id, descripcion }
 */
export default function CobrarChequesModal({ isOpen, onClose, onSuccess, ventaItem, paymentMethods }) {
    const [installments, setInstallments] = useState([]);
    const [loading, setLoading] = useState(false);

    // Per-installment state: maps cheque id → selected metodoPagoId; isSubmitting/isSubmittingRef lock concurrent actions
    const [cobrandoId, setCobrandoId] = useState(null);
    const [selectedMethods, setSelectedMethods] = useState({});
    const [confirmConfig, setConfirmConfig] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const isSubmittingRef = useRef(false);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        if (isOpen && ventaItem?.venta_id) {
            fetchInstallments();
        } else if (!isOpen) {
            setConfirmConfig(null);
        }
        return () => { isMounted.current = false; };
    }, [isOpen, ventaItem]);

    const fetchInstallments = async () => {
        setLoading(true);
        try {
            const res = await api.get(`/alertas/cheques/venta/${ventaItem.venta_id}`);
            if (!isMounted.current) return;
            setInstallments(res.data);
            // Pre-initialize method selectors
            const methods = {};
            res.data.forEach(i => {
                if (i.estado === 'PENDIENTE') methods[i.id] = '';
            });
            setSelectedMethods(methods);
        } catch (err) {
            console.error('Error loading installments:', err);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    // Shared runner enforces system-standard synchronous (ref) and async (state) double-submit protection.
    const runInstallmentAction = async (chequeId, actionFn, successMsg, errorContext) => {
        if (isSubmittingRef.current || isSubmitting) return;
        try {
            isSubmittingRef.current = true;
            if (isMounted.current) {
                setIsSubmitting(true);
                setCobrandoId(chequeId);
            }
            await actionFn();
            toast.success(successMsg);
            await fetchInstallments();
            if (onSuccess) onSuccess();
        } catch (err) {
            console.error(errorContext, err);
            // Vector 2: Force re-fetch on failure
            await fetchInstallments();
        } finally {
            isSubmittingRef.current = false;
            if (isMounted.current) {
                setIsSubmitting(false);
                setCobrandoId(null);
            }
        }
    };

    const handleCobrar = async (cheque) => {
        if (isSubmittingRef.current || isSubmitting) return;
        const metodoPagoId = selectedMethods[cheque.id];
        if (!metodoPagoId) {
            toast.error('Seleccione un método de pago para cobrar esta cuota.');
            return;
        }

        await runInstallmentAction(
            cheque.id,
            () => api.post(`/alertas/cheques/${cheque.id}/cobrar`, null, {
                params: { metodoPagoId }
            }),
            `Cuota de $${cheque.monto.toFixed(2)} cobrada exitosamente.`,
            'Error al cobrar cuota:'
        );
    };

    const handleCancelarCobro = (cheque) => {
        if (isSubmittingRef.current || isSubmitting) return;
        setConfirmConfig({
            title: 'Deshacer Cobro de Cheque',
            message: `¿Está seguro de que desea deshacer el cobro de esta cuota de $${cheque.monto.toFixed(2)} y revertirla a estado pendiente?`,
            confirmText: 'Deshacer Cobro',
            cancelText: 'Cancelar',
            isWarning: true,
            onConfirm: async () => {
                if (isSubmittingRef.current || isSubmitting) return;
                setConfirmConfig(null);
                await runInstallmentAction(
                    cheque.id,
                    () => api.post(`/alertas/cheques/${cheque.id}/cancelar-cobro`),
                    'Cobro revertido exitosamente a pendiente.',
                    'Error al revertir cobro:'
                );
            },
            onCancel: () => setConfirmConfig(null)
        });
    };

    const handleAnularCheque = (cheque) => {
        if (isSubmittingRef.current || isSubmitting) return;
        const isCobrado = cheque.estado === 'COBRADO';
        setConfirmConfig({
            title: isCobrado ? 'Anular Cheque Cobrado' : 'Anular Cheque',
            message: isCobrado
                ? `¿Está seguro de que desea anular este cheque cobrado de $${cheque.monto.toFixed(2)}? Se revertirá el pago asociado y, si corresponde a una venta con cliente o deuda, el saldo quedará registrado como deuda pendiente.`
                : `¿Está seguro de que desea anular permanentemente este cheque de $${cheque.monto.toFixed(2)}? Si corresponde a una venta con cliente o deuda, el monto volverá a figurar como deuda pendiente.`,
            confirmText: isCobrado ? 'Anular Cheque y Pago' : 'Anular Cheque',
            cancelText: 'Cancelar',
            isWarning: true,
            onConfirm: async () => {
                if (isSubmittingRef.current || isSubmitting) return;
                setConfirmConfig(null);
                await runInstallmentAction(
                    cheque.id,
                    () => api.delete(`/alertas/cheques/${cheque.id}`),
                    isCobrado ? 'Cheque anulado y cobro revertido exitosamente.' : 'Cheque anulado exitosamente.',
                    'Error al anular cheque:'
                );
            },
            onCancel: () => setConfirmConfig(null)
        });
    };

    const handleSafeClose = () => {
        if (isSubmittingRef.current || isSubmitting) return;
        setConfirmConfig(null);
        onClose();
    };

    const pendingTotal = (installments || [])
        .filter(i => i.estado === 'PENDIENTE')
        .reduce((sum, i) => sum + i.monto, 0);

    const cobradoTotal = (installments || [])
        .filter(i => i.estado === 'COBRADO')
        .reduce((sum, i) => sum + i.monto, 0);

    if (!isOpen) return null;

    return (
        <>
            <div className="modal-overlay" onClick={handleSafeClose}>
                <div
                    className="ccm-modal-content"
                    onClick={e => e.stopPropagation()}
                    role="dialog"
                    aria-modal="true"
                    aria-label="Cobrar Cuotas de Cheque"
                >
                    {/* Header */}
                    <div className="ccm-header">
                        <div className="ccm-header-info">
                            <h2>💳 Cobro de Cheques</h2>
                            <p>{ventaItem?.cliente_nombre}</p>
                        </div>
                        <button
                            className="ccm-close-btn"
                            onClick={handleSafeClose}
                            disabled={isSubmitting || cobrandoId !== null}
                            aria-label="Cerrar"
                        >
                            ✕
                        </button>
                    </div>

                    {/* Summary row */}
                    <div className="ccm-summary-row">
                        <div className="ccm-summary-box ccm-summary-pending">
                            <span className="ccm-summary-label">Pendiente de cobro</span>
                            <span className="ccm-summary-amount">${pendingTotal.toFixed(2)}</span>
                        </div>
                        <div className="ccm-summary-box ccm-summary-paid">
                            <span className="ccm-summary-label">Ya cobrado</span>
                            <span className="ccm-summary-amount">${cobradoTotal.toFixed(2)}</span>
                        </div>
                    </div>

                    {/* Installments list */}
                    <div className="ccm-installments">
                        {loading ? (
                            <div className="ccm-loading">Cargando cuotas...</div>
                        ) : (installments || []).length === 0 ? (
                            <div className="ccm-empty">No se encontraron cuotas para esta venta.</div>
                        ) : (
                            (installments || []).map((cheque) => {
                                const isPending = cheque.estado === 'PENDIENTE';
                                const isAnulada = cheque.estado === 'ANULADA';
                                const isOverdue = isPending && new Date(cheque.fechaCobro) <= new Date();
                                const isCobrandoThis = cobrandoId === cheque.id;
                                const rowClass = isPending
                                    ? (isOverdue ? 'ccm-row-overdue' : 'ccm-row-pending')
                                    : (isAnulada ? 'ccm-row-anulada' : 'ccm-row-paid');
                                const dateLabel = isAnulada
                                    ? '❌ Anulado'
                                    : isOverdue ? '⚠️ Vencido' : isPending ? '📅 Vence' : '✅ Cobrado';

                                return (
                                    <div
                                        key={cheque.id}
                                        className={`ccm-installment-row ${rowClass}`}
                                    >
                                        <div className="ccm-installment-info">
                                            <div className="ccm-installment-date">
                                                <span className="ccm-date-label">
                                                    {dateLabel}
                                                </span>
                                                <strong>{formatDate(cheque.fechaCobro)}</strong>
                                            </div>
                                            <div className="ccm-installment-amount">
                                                ${cheque.monto.toFixed(2)}
                                            </div>
                                            <span className={`ccm-badge ccm-badge-${cheque.estado.toLowerCase()}`}>
                                                {cheque.estado}
                                            </span>
                                        </div>

                                        {isPending && (
                                            <div className="ccm-installment-action">
                                                <select
                                                    value={selectedMethods[cheque.id] || ''}
                                                    onChange={e => setSelectedMethods(prev => ({
                                                        ...prev,
                                                        [cheque.id]: e.target.value
                                                    }))}
                                                    className="ccm-method-select"
                                                    disabled={isSubmitting || isCobrandoThis || cobrandoId !== null}
                                                    aria-label={`Método de pago para cuota del ${formatDate(cheque.fechaCobro)}`}
                                                >
                                                    <option value="">Método de pago...</option>
                                                    {(paymentMethods || [])
                                                        .filter(m => {
                                                            const desc = (m.descripcion || '').toLowerCase();
                                                            return !desc.includes('cheque')
                                                                && !desc.includes('e-check')
                                                                && !desc.includes('echeck')
                                                                && m.activo !== false;
                                                        })
                                                        .map(m => (
                                                            <option key={m.id} value={m.id}>{m.descripcion}</option>
                                                        ))}
                                                </select>
                                                <button
                                                    className="ccm-cobrar-btn"
                                                    onClick={() => handleCobrar(cheque)}
                                                    disabled={!selectedMethods[cheque.id] || isSubmitting || isCobrandoThis || cobrandoId !== null}
                                                    aria-label={`Cobrar cuota de $${cheque.monto.toFixed(2)}`}
                                                >
                                                    {isCobrandoThis ? 'Cobrando...' : '💰 Cobrar'}
                                                </button>
                                                <button
                                                    className="ccm-anular-btn"
                                                    onClick={() => handleAnularCheque(cheque)}
                                                    disabled={isSubmitting || cobrandoId !== null}
                                                    title="Anular cheque permanentemente"
                                                    aria-label={`Anular cheque de $${cheque.monto.toFixed(2)}`}
                                                >
                                                    {isCobrandoThis ? '...' : 'Anular'}
                                                </button>
                                            </div>
                                        )}

                                        {!isPending && cheque.estado === 'COBRADO' && (
                                            <div className="ccm-installment-action ccm-installment-action--cobrado">
                                                <span className="ccm-cobrado-method">
                                                    Método: {cheque.metodoPagoNombre || 'Desconocido'}
                                                </span>
                                                <div className="ccm-cobrado-actions">
                                                    <button
                                                        className="ccm-undo-btn"
                                                        onClick={() => handleCancelarCobro(cheque)}
                                                        disabled={isSubmitting || cobrandoId !== null}
                                                        title="Revertir cobro y volver a estado Pendiente"
                                                        aria-label={`Deshacer cobro de $${cheque.monto.toFixed(2)}`}
                                                    >
                                                        {isCobrandoThis ? 'Revirtiendo...' : '↩️ Deshacer'}
                                                    </button>
                                                    <button
                                                        className="ccm-anular-btn"
                                                        onClick={() => handleAnularCheque(cheque)}
                                                        disabled={isSubmitting || cobrandoId !== null}
                                                        title="Anular cheque y revertir su cobro asociado"
                                                        aria-label={`Anular cheque de $${cheque.monto.toFixed(2)}`}
                                                    >
                                                        {isCobrandoThis ? 'Anulando...' : 'Anular'}
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })
                        )}
                    </div>

                    {/* Footer */}
                    <div className="ccm-footer">
                        <button
                            className="secondary ccm-cancel-btn"
                            onClick={handleSafeClose}
                            disabled={isSubmitting || cobrandoId !== null}
                        >
                            Cerrar
                        </button>
                    </div>
                </div>
            </div>

            {confirmConfig && (
                <ConfirmationModal
                    title={confirmConfig.title}
                    message={confirmConfig.message}
                    confirmText={confirmConfig.confirmText}
                    cancelText={confirmConfig.cancelText}
                    isWarning={confirmConfig.isWarning}
                    onConfirm={confirmConfig.onConfirm}
                    onCancel={confirmConfig.onCancel}
                />
            )}
        </>
    );
}
