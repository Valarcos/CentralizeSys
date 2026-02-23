import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../services/api';
import './BackupPage.css'; // We will create this

export default function BackupPage() {
    const [backups, setBackups] = useState([]);
    const [groupedBackups, setGroupedBackups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [userRole, setUserRole] = useState('');

    // Modal State
    const [selectedBackup, setSelectedBackup] = useState(null);
    const [uploadFile, setUploadFile] = useState(null);
    const [showRestoreWarning, setShowRestoreWarning] = useState(false);
    const [showRestoreConfirm, setShowRestoreConfirm] = useState(false);
    const [restoreConfirmationInput, setRestoreConfirmationInput] = useState('');

    const navigate = useNavigate();

    useEffect(() => {
        setUserRole(localStorage.getItem('userRole') || '');
        fetchBackups();
    }, []);

    const fetchBackups = async () => {
        try {
            setLoading(true);
            const response = await api.get('/api/backups');
            setBackups(response.data);
            processGrouping(response.data);
        } catch (error) {
            console.error('Error fetching backups:', error);
            toast.error('Error al cargar la lista de respaldos');
        } finally {
            setLoading(false);
        }
    };

    const processGrouping = (backupList) => {
        const groups = {};

        backupList.forEach(file => {
            // Filename format: centralizesys_daily_20231025_1430.db
            // We want to group by the timestamp part: 20231025_1430
            // Regex to extract timestamp: _(\d{8}_\d{4})\.
            const match = file.filename.match(/_(\d{8}_\d{4})\./);
            const timestamp = match ? match[1] : 'unknown';
            const type = file.type.includes('DAILY') ? 'Automático' : 'Manual';

            const key = `${type}_${timestamp}`;

            if (!groups[key]) {
                groups[key] = {
                    id: key,
                    date: file.date,
                    dateFormatted: new Date(file.date).toLocaleDateString(),
                    timeFormatted: new Date(file.date).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                    typeDisplay: type,
                    dbFile: null,
                    excelFile: null
                };
            }

            if (file.filename.endsWith('.db')) {
                groups[key].dbFile = file.filename;
            } else if (file.filename.endsWith('.xlsx')) {
                groups[key].excelFile = file.filename;
            }
        });

        // Convert to array and sort by date desc
        const sortedGroups = Object.values(groups).sort((a, b) => new Date(b.date) - new Date(a.date));
        setGroupedBackups(sortedGroups);
    };

    // ... (handleCreateBackup remains same) ...


    const handleCreateBackup = async () => {
        try {
            await api.post('/api/backups/create');
            toast.success('Respaldo creado correctamente');
            fetchBackups();
        } catch (error) {
            console.error('Error creating backup:', error);
            toast.error('Error al crear respaldo');
        }
    };

    const handleDownload = async (filename) => {
        try {
            const response = await api.get(`/api/backups/download/${filename}`, {
                responseType: 'blob'
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (error) {
            console.error('Error downloading backup:', error);
            toast.error('Error al descargar archivo');
        }
    };

    const handleDownloadBoth = async (dbFile, excelFile) => {
        toast.promise(
            Promise.all([
                handleDownload(dbFile),
                new Promise(resolve => setTimeout(() => resolve(handleDownload(excelFile)), 1000)) // Small delay to prevent browser block
            ]),
            {
                loading: 'Iniciando descarga de ambos archivos...',
                success: 'Descargas iniciadas',
                error: 'Error al descargar algunos archivos'
            }
        );
    };

    // --- Restore Logic ---

    const initiateRestore = (backupFilename) => {
        setSelectedBackup(backupFilename);
        setUploadFile(null); // Ensure we are not in upload mode
        setShowRestoreWarning(true);
    };

    const initiateUploadRestore = (file) => {
        if (!file) return;
        if (!file.name.endsWith('.db')) {
            toast.error('Solo se permiten archivos .db');
            return;
        }
        setUploadFile(file);
        setSelectedBackup(file.name); // Just for display
        setShowRestoreWarning(true);
    };

    const handleWarningContinue = () => {
        setShowRestoreWarning(false);
        setShowRestoreConfirm(true);
        setRestoreConfirmationInput('');
    };

    const handleRestoreExecute = async () => {
        if (restoreConfirmationInput !== 'RESTAURAR') {
            toast.error('Debe escribir RESTAURAR para confirmar');
            return;
        }

        try {
            const loadingToast = toast.loading('Restaurando base de datos...');
            setShowRestoreConfirm(false);

            if (uploadFile) {
                // Upload Restore
                const formData = new FormData();
                formData.append('file', uploadFile);
                await api.post('/api/backups/upload-restore', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
            } else {
                // List Restore
                await api.post(`/api/backups/restore/${selectedBackup}`);
            }

            toast.dismiss(loadingToast);
            toast.success('Restauración completada. El sistema se reiniciará.');
            // Force logout or reload
            setTimeout(() => {
                window.location.href = '/';
            }, 2000);

        } catch (error) {
            console.error('Error restoring database:', error);
            toast.dismiss();
            toast.error(error.response?.data?.message || 'Error al restaurar base de datos');
        }
    };

    // --- File Input Helper ---
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            initiateUploadRestore(e.target.files[0]);
        }
    };



    return (
        <div className="backup-page container">
            <header className="page-header">
                <h1>💾 Gestión de Respaldos</h1>
                <div className="header-actions">
                    {userRole === 'ADMIN' && (
                        <>
                            <input
                                type="file"
                                id="upload-backup"
                                accept=".db"
                                style={{ display: 'none' }}
                                onChange={handleFileChange}
                            />
                            <label htmlFor="upload-backup" className="button secondary">
                                📤 Subir Respaldo
                            </label>
                        </>
                    )}
                    <button onClick={handleCreateBackup} className="button primary">
                        + Nuevo Respaldo
                    </button>
                    <button onClick={() => navigate('/')} className="button tertiary">
                        Volver
                    </button>
                </div>
            </header>

            <div className="backups-list-container">
                {loading ? (
                    <p>Cargando respaldos...</p>
                ) : groupedBackups.length === 0 ? (
                    <div className="empty-state">No hay respaldos disponibles.</div>
                ) : (
                    <table className="backups-table">
                        <thead>
                        <tr>
                            <th>Fecha</th>
                            <th>Tipo</th>
                            <th>Descargas Disponibles</th>
                            {userRole === 'ADMIN' && <th>Admin</th>}
                        </tr>
                        </thead>
                        <tbody>
                        {groupedBackups.map((group) => (
                            <tr key={group.id}>
                                <td data-label="Fecha">
                                    <strong>{group.dateFormatted}</strong>
                                    <div style={{ fontSize: '0.9em', color: '#666' }}>
                                        {group.timeFormatted}
                                    </div>
                                </td>
                                <td data-label="Tipo">
                                    {group.typeDisplay}
                                </td>
                                <td data-label="Descargas">
                                    <div className="download-buttons-group">
                                        {/* Download Both */}
                                        {group.dbFile && group.excelFile && (
                                            <button
                                                onClick={() => handleDownloadBoth(group.dbFile, group.excelFile)}
                                                className="btn-large btn-download-all"
                                                title="Descargar ambos archivos"
                                            >
                                                ⬇️ DESCARGAR TODO
                                            </button>
                                        )}

                                        {/* Excel Button */}
                                        {group.excelFile && (
                                            <button
                                                onClick={() => handleDownload(group.excelFile)}
                                                className="btn-large btn-excel"
                                                title="Descargar Excel"
                                            >
                                                📊 EXCEL
                                            </button>
                                        )}

                                        {/* DB Button */}
                                        {group.dbFile && (
                                            <button
                                                onClick={() => handleDownload(group.dbFile)}
                                                className="btn-large btn-db"
                                                title="Descargar Base de Datos"
                                            >
                                                🗄️ BD
                                            </button>
                                        )}
                                    </div>
                                </td>
                                {userRole === 'ADMIN' && (
                                    <td data-label="Admin">
                                        {group.dbFile && (
                                            <button
                                                onClick={() => initiateRestore(group.dbFile)}
                                                className="btn-large btn-restore"
                                                title="Restaurar sistema a este punto"
                                            >
                                                🔄 Restaurar
                                            </button>
                                        )}
                                    </td>
                                )}
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Warning Modal */}
            {showRestoreWarning && (
                <div className="modal-overlay">
                    <div className="modal-content warning">
                        <h2>⚠️ Advertencia Crítica</h2>
                        <p>
                            Está a punto de restaurar la base de datos desde: <strong>{selectedBackup}</strong>
                        </p>
                        <p className="highlight-danger">
                            {uploadFile
                                ? "Esto reemplazará la base de datos actual con el archivo subido."
                                : "Esto borrará los datos actuales y los reemplazará con esta copia de seguridad."}
                        </p>
                        <p>Esta acción <strong>NO</strong> se puede deshacer.</p>

                        <div className="modal-actions">
                            <button onClick={() => setShowRestoreWarning(false)} className="button secondary">
                                Cancelar
                            </button>
                            <button onClick={handleWarningContinue} className="button danger">
                                Continuar
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Confirmation Modal */}
            {showRestoreConfirm && (
                <div className="modal-overlay">
                    <div className="modal-content danger">
                        <h2>🔒 Confirmación de Seguridad</h2>
                        <p>
                            ¿Está seguro que desea restaurar desde <strong>{selectedBackup}</strong>?
                        </p>
                        <p>
                            Para confirmar, escriba <strong>RESTAURAR</strong> en el campo de abajo:
                        </p>
                        <input
                            type="text"
                            value={restoreConfirmationInput}
                            onChange={(e) => setRestoreConfirmationInput(e.target.value)}
                            placeholder="RESTAURAR"
                            className="confirm-input"
                        />
                        <div className="modal-actions">
                            <button onClick={() => setShowRestoreConfirm(false)} className="button secondary">
                                Cancelar
                            </button>
                            <button
                                onClick={handleRestoreExecute}
                                className="button danger"
                                disabled={restoreConfirmationInput !== 'RESTAURAR'}
                            >
                                EJECUTAR RESTAURACIÓN
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
