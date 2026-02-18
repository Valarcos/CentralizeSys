import { useEffect, useRef } from 'react';
import './DeleteProductModal.css';

export default function DeleteProductModal({ product, onConfirm, onCancel, loading }) {
    const cancelBtnRef = useRef(null);

    useEffect(() => {
        cancelBtnRef.current?.focus();
    }, []);

    if (!product) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content delete-modal">
                <div className="modal-header delete-header">
                    <h2>⚠️ Confirmar Eliminación</h2>
                </div>

                <div className="modal-body">
                    <p>¿Está seguro de que desea eliminar el producto?</p>
                    <div className="product-summary">
                        <p><strong>{product.descripcion}</strong></p>
                        <p className="code-text">Código: {product.codigo}</p>
                    </div>
                    <p className="warning-text">
                        Esta acción es irreversible y podría afectar el historial de ventas si no se gestiona correctamente.
                    </p>
                </div>

                <div className="modal-actions">
                    <button
                        ref={cancelBtnRef}
                        onClick={onCancel}
                        className="cancel-btn"
                        disabled={loading}
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="confirm-delete-btn"
                        disabled={loading}
                    >
                        {loading ? 'Eliminando...' : 'Sí, Eliminar'}
                    </button>
                </div>
            </div>
        </div>
    );
}
