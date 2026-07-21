import { useState, useRef, useEffect } from 'react';
import './CheckoutChequeModal.css';
import { blockNonNumericKeys, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';

export default function CheckoutChequeModal({ isOpen, onClose, onConfirm, totalAmount, clientName }) {
    const [cheques, setCheques] = useState([{ monto: '', fechaCobro: '' }]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const listRef = useRef(null);

    if (!isOpen) return null;

    const handleAddCheque = () => {
        setCheques([...cheques, { monto: '', fechaCobro: '' }]);
    };

    useEffect(() => {
        if (listRef.current) {
            listRef.current.scrollTop = listRef.current.scrollHeight;
        }
    }, [cheques.length]);

    const handleRemoveCheque = (index) => {
        const newCheques = [...cheques];
        newCheques.splice(index, 1);
        setCheques(newCheques);
    };

    const handleChequeChange = (index, field, value) => {
        const newCheques = [...cheques];
        newCheques[index][field] = value;
        setCheques(newCheques);
    };

    const totalIngresado = cheques.reduce((sum, c) => sum + (parseFloat(c.monto) || 0), 0);
    const restante = Math.max(0, totalAmount - totalIngresado);
    const validCheques = cheques.every(c => parseFloat(c.monto) > 0 && c.fechaCobro);
    const isTotalValid = Math.abs(totalAmount - totalIngresado) < 0.01;

    const handleMontoFocus = (e, index) => {
        e.target.select(); // Highlight the whole content

        // Auto calculate if empty
        if (!cheques[index].monto && restante > 0) {
            handleChequeChange(index, 'monto', restante.toFixed(2));
        }
    };

    const handleConfirm = async () => {
        if (!isTotalValid) return;
        if (!validCheques) return;

        setIsSubmitting(true);
        try {
            await onConfirm(cheques);
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content cheque-modal-content">
                <div className="modal-header-cheque">
                    <h2>Cobro con Cheques</h2>
                </div>

                <div className="cheque-modal-body">
                    <div className="cheque-info-box">
                        <div className="info-row">
                            <div className="info-label"><strong>Cliente:</strong></div>
                            <div className="info-value">{clientName}</div>
                        </div>
                        <div className="info-row">
                            <div className="info-label"><strong>Total Venta:</strong></div>
                            <div className="info-value">${totalAmount.toFixed(2)}</div>
                        </div>
                        <div className="info-row">
                            <div className="info-label"><strong>Falta Asignar:</strong></div>
                            <div className="info-value">
                               <span style={{ color: restante > 0 ? '#dc2626' : '#16a34a' }}>
                                   ${restante.toFixed(2)}
                               </span>
                            </div>
                        </div>
                    </div>

                    <div className="cheques-list" ref={listRef}>
                        {cheques.map((cheque, index) => (
                            <div key={index} className="cheque-row">
                                <label>Monto</label>
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    placeholder="$"
                                    value={cheque.monto}
                                    onChange={(e) => handleChequeChange(index, 'monto', enforceMoneyFormat(e.target.value))}
                                    onFocus={(e) => handleMontoFocus(e, index)}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                />
                                <label>Fecha de Cobro</label>
                                <input
                                    type="date"
                                    value={cheque.fechaCobro}
                                    onChange={(e) => handleChequeChange(index, 'fechaCobro', e.target.value)}
                                    min={new Date().toISOString().split('T')[0]} // Prevents past dates based on client's local TZ
                                />
                                {cheques.length > 1 ? (
                                    <button
                                        className="remove-cheque-btn"
                                        onClick={() => handleRemoveCheque(index)}
                                    >
                                        ×
                                    </button>
                                ) : (
                                    <div className="remove-cheque-placeholder"></div>
                                )}
                            </div>
                        ))}
                    </div>

                    <button
                        className="add-cheque-btn"
                        onClick={handleAddCheque}
                        disabled={restante <= 0}
                    >
                        + Agregar otro cheque
                    </button>
                </div>

                <div className="modal-actions-cheque">
                    <button
                        className="cancel-btn"
                        onClick={onClose}
                        disabled={isSubmitting}
                    >
                        Cancelar
                    </button>
                    <button
                        className="confirm-btn"
                        onClick={handleConfirm}
                        disabled={!isTotalValid || !validCheques || isSubmitting}
                    >
                        {isSubmitting ? 'Procesando...' : 'Confirmar Venta'}
                    </button>
                </div>
            </div>
        </div>
    );
}
