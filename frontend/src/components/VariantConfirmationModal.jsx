import './ProductFormModal.css'; // Reuse modal styles

export default function VariantConfirmationModal({ currentCost, newCost, onConfirmVariant, onCorrect, onClose }) {
    return (
        <div className="modal-overlay" style={{ zIndex: 1100 }}> {/* Higher z-index than StockEntry? */}
            <div className="modal-content" style={{ maxWidth: '500px' }}>
                <div className="modal-header" style={{ marginBottom: '0.5rem' }}>
                    <h2 style={{ color: '#d97706' }}>⚠️ Diferencia de Costo Detectada</h2>
                </div>

                <div className="modal-body" style={{ padding: '1rem 0' }}>
                    <p>El costo ingresado (<strong>${newCost}</strong>) es diferente al registrado en el sistema (<strong>${currentCost}</strong>).</p>
                    <p style={{ marginTop: '0.5rem' }}>
                        ¿Desea crear una <strong>Nueva Variante</strong> con este precio?
                    </p>
                    <p style={{ fontSize: '0.85rem', color: '#666', marginTop: '0.5rem' }}>
                        Si elige "No, corregir", deberá ingresar el costo correcto para continuar.
                    </p>
                </div>

                <div className="form-actions">
                    <button
                        onClick={onCorrect}
                        className="secondary"
                    >
                        No, corregir
                    </button>
                    <button
                        onClick={onConfirmVariant}
                        className="primary"
                        autoFocus
                    >
                        Sí, Crear Variante
                    </button>
                </div>
            </div>
        </div>
    );
}
