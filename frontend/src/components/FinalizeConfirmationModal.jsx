import React from 'react';
import './ConfirmationModal.css';

export default function FinalizeConfirmationModal({
                                                      isOpen,
                                                      onConfirm,
                                                      onCancel,
                                                      isSubmitting = false,
                                                      error = null,
                                                      clientName = null
                                                  }) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content warning-modal">
                <div className="modal-header">
                    <h2>⚠️ Confirmar Acción Destructiva</h2>
                    <button className="close-btn" onClick={onCancel} disabled={isSubmitting}>×</button>
                </div>
                <div className="modal-body">
                    {error && (
                        <div className="modal-error-banner" role="alert" style={{ marginBottom: '1rem' }}>
                            <strong>⚠️ Error al procesar:</strong> {error}
                        </div>
                    )}
                    {clientName && (
                        <p>
                            Pedido de <strong>{clientName}</strong>: se registrará como una venta definitiva.
                        </p>
                    )}
                    <p style={{ fontWeight: 'bold' }}>
                        La acción de Finalizar Venta es final, y no puede deshacerse.
                    </p>
                    <p>
                        No se podrán registrar pagos ni editar los productos incluidos en el presupuesto.
                    </p>
                </div>
                <div className="modal-footer">
                    <button className="btn-secondary" onClick={onCancel} disabled={isSubmitting}>Volver</button>
                    <button
                        className="btn-danger"
                        onClick={onConfirm}
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? 'Finalizando...' : 'Confirmar Venta'}
                    </button>
                </div>
            </div>
        </div>
    );
}
