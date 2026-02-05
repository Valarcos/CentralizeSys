import { useEffect, useState } from 'react';
import './DashboardPage.css';

export default function DashboardPage() {
    const [userName, setUserName] = useState('');

    useEffect(() => {
        const name = localStorage.getItem('userName') || 'Usuario';
        setUserName(name);
    }, []);

    return (
        <div className="dashboard">
            <h1>Bienvenido, {userName}</h1>
            <p className="dashboard-subtitle">Sistema operativo y listo</p>

            <div className="dashboard-grid">
                <div className="dashboard-card">
                    <h3>🏠 Inicio</h3>
                    <p>Panel principal del sistema</p>
                </div>

                <div className="dashboard-card">
                    <h3>💰 Punto de Venta</h3>
                    <p>Próximamente en Sprint 3</p>
                </div>

                <div className="dashboard-card">
                    <h3>📦 Gestión de Stock</h3>
                    <p>Próximamente en Sprint 3</p>
                </div>

                <div className="dashboard-card">
                    <h3>👤 Mi Cuenta</h3>
                    <p>Próximamente en Sprint 3</p>
                </div>
            </div>
        </div>
    );
}
