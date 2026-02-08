import { useEffect, useRef } from 'react';
import './DeleteUserModal.css';

export default function DeleteUserModal({ user, onConfirm, onCancel }) {
    const cancelButtonRef = useRef(null);

    useEffect(() => {
        // Focus on cancel button (safe option) when modal opens
        cancelButtonRef.current?.focus();
    }, []);

    if (!user) return null;

    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div
                className="delete-user-modal"
                onClick={(e) => e.stopPropagation()}
                role="alertdialog"
                aria-labelledby="delete-modal-title"
                aria-describedby="delete-modal-description"
            >
                <h2 id="delete-modal-title">⚠️ ¿Eliminar Usuario?</h2>

                <div id="delete-modal-description">
                    <p>Está a punto de eliminar:</p>
                    <p className="user-detail">
                        <strong>{user.nombre}</strong>
                    </p>
                    <p className="user-email">{user.email}</p>
                    <p className="warning">
                        ⚠️ Esta acción no se puede deshacer.
                    </p>
                </div>

                <div className="modal-actions">
                    <button
                        ref={cancelButtonRef}
                        onClick={onCancel}
                        className="secondary"
                        aria-label="Cancelar eliminación"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="danger"
                        aria-label="Confirmar eliminación de usuario"
                    >
                        Eliminar
                    </button>
                </div>
            </div>
        </div>
    );
}
