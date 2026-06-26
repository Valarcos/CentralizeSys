import React from 'react';
import './ConfirmationModal.css';

export default function CancellationModal({
                                              isOpen,
                                              title = 'Confirmar Cancelación',
                                              message = '¿Está seguro que desea cancelar esta operación?',
                                              onConfirm,
                                              onCancel,
                                              isSubmitting = false
                                          }) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content warning-modal">
                <div className="modal-header">
                    <h2>⚠️ {title}</h2>
                    <button className="close-btn" onClick={onCancel} disabled={isSubmitting}>×</button>
                </div>
                <div className="modal-body">
                    <p>{message}</p>
                </div>
                <div className="modal-footer">
                    <button className="btn-secondary" onClick={onCancel} disabled={isSubmitting}>Volver</button>
                    <button className="btn-danger" onClick={onConfirm} disabled={isSubmitting}>
                        {isSubmitting ? 'Cancelando...' : 'Confirmar Cancelación'}
                    </button>
                </div>
            </div>
        </div>
    );
}
