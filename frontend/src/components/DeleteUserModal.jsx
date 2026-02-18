import { useEffect, useRef } from 'react';
import './DeleteUserModal.css';
import './DeleteProductModal.css'; // Reuse styles

export default function DeleteUserModal({ user, onConfirm, onCancel, loading }) {
    const cancelButtonRef = useRef(null);

    useEffect(() => {
        // Focus on cancel button (safe option) when modal opens
        cancelButtonRef.current?.focus();
    }, []);

    if (!user) return null;

    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div
                className="delete-user-modal modal-content delete-modal"
                onClick={(e) => e.stopPropagation()}
                role="alertdialog"
                aria-labelledby="delete-modal-title"
                aria-describedby="delete-modal-description"
            >
                <div className="modal-header delete-header">
                    <h2 id="delete-modal-title">⚠️ Confirmar Eliminación</h2>
                </div>

                <div className="modal-body" id="delete-modal-description">
                    <p>¿Está seguro de que desea eliminar al usuario?</p>
                    <div className="product-summary">
                        <p className="user-detail">
                            <strong>{user.nombre}</strong>
                        </p>
                        <p className="code-text" style={{ fontSize: '0.9rem' }}>{user.email}</p>
                    </div>
                    <p className="warning-text">
                        ⚠️ Esta acción revocará el acceso inmediatamente y no se puede deshacer.
                    </p>
                </div>

                <div className="modal-actions">
                    <button
                        ref={cancelButtonRef}
                        onClick={onCancel}
                        className="cancel-btn"
                        disabled={loading}
                        aria-label="Cancelar eliminación"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="confirm-delete-btn"
                        disabled={loading}
                        aria-label="Confirmar eliminación de usuario"
                    >
                        {loading ? 'Eliminando...' : 'Sí, Eliminar'}
                    </button>
                </div>
            </div>
        </div>
    );
}
