import { useEffect, useRef } from 'react';
import './DeleteProductModal.css';

export default function DeleteProductModal({ product, onConfirm, onCancel }) {
    const cancelBtnRef = useRef(null);

    useEffect(() => {
        cancelBtnRef.current?.focus();
    }, []);

    if (!product) return null;

    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div
                className="delete-product-modal"
                onClick={(e) => e.stopPropagation()}
                role="alertdialog"
                aria-labelledby="delete-product-title"
                aria-describedby="delete-product-description"
            >
                <h2 id="delete-product-title">⚠️ ¿Eliminar Producto?</h2>

                <div id="delete-product-description">
                    <p>Está a punto de eliminar:</p>
                    <p className="product-detail">
                        <strong>{product.descripcion}</strong>
                    </p>
                    <p className="product-info">
                        Código: {product.codigo || 'Sin código'} | Stock actual: {product.cantidadStock} unidades
                    </p>
                    <p className="warning">
                        ⚠️ Esta acción no se puede deshacer.
                    </p>
                </div>

                <div className="modal-actions">
                    <button
                        ref={cancelBtnRef}
                        onClick={onCancel}
                        className="secondary"
                        aria-label="Cancelar eliminación"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="danger"
                        aria-label="Confirmar eliminación de producto"
                    >
                        Eliminar
                    </button>
                </div>
            </div>
        </div>
    );
}
