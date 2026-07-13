import React, { useState } from 'react';
import './ConfirmationModal.css';

export default function FinalizeConfirmationModal({
    isOpen,
    onConfirm,
    onCancel,
    isSubmitting = false
}) {
    const [inputValue, setInputValue] = useState('');

    if (!isOpen) return null;

    const handleConfirm = () => {
        if (inputValue === 'FINALIZAR') {
            onConfirm();
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content warning-modal">
                <div className="modal-header">
                    <h2>⚠️ Confirmar Acción Destructiva</h2>
                    <button className="close-btn" onClick={onCancel} disabled={isSubmitting}>×</button>
                </div>
                <div className="modal-body">
                    <p style={{ fontWeight: 'bold' }}>
                        La acción de Finalizar Venta es final, y no puede deshacerse.
                    </p>
                    <p>
                        No se podrán registrar pagos ni editar los productos incluidos en el presupuesto.
                    </p>
                    <div style={{ marginTop: '1rem' }}>
                        <label>Escriba la palabra <strong>FINALIZAR</strong> para confirmar:</label>
                        <input
                            type="text"
                            value={inputValue}
                            onChange={(e) => setInputValue(e.target.value)}
                            style={{
                                width: '100%',
                                padding: '0.5rem',
                                marginTop: '0.5rem',
                                border: '1px solid #ccc',
                                borderRadius: '4px'
                            }}
                            placeholder="FINALIZAR"
                            autoFocus
                        />
                    </div>
                </div>
                <div className="modal-footer">
                    <button className="btn-secondary" onClick={onCancel} disabled={isSubmitting}>Volver</button>
                    <button
                        className="btn-danger"
                        onClick={handleConfirm}
                        disabled={isSubmitting || inputValue !== 'FINALIZAR'}
                    >
                        {isSubmitting ? 'Finalizando...' : 'Confirmar Venta'}
                    </button>
                </div>
            </div>
        </div>
    );
}
