import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../services/api';
import './LoginPage.css';

export default function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!email || !password) {
            toast.error('Complete todos los campos');
            return;
        }

        if (!email.includes('@')) {
            toast.error('Ingrese un email válido');
            return;
        }

        setLoading(true);

        try {
            const response = await api.post('/api/auth/login', { email, password });
            const { token, email: userEmail, nombre, rol } = response.data;

            localStorage.setItem('jwt', token);
            localStorage.setItem('userEmail', userEmail);
            localStorage.setItem('userName', nombre);
            localStorage.setItem('userRole', rol);

            toast.success(`¡Bienvenido, ${nombre}!`);
            navigate('/dashboard');
        } catch (error) {
            console.error('Login error:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>CentralizeSys</h1>
                <p className="subtitle">Sistema de Gestión</p>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="email">Email</label>
                        <input
                            id="email"
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            autoComplete="email"
                            disabled={loading}
                            placeholder="usuario@ejemplo.com"
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Contraseña</label>
                        <input
                            id="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoComplete="current-password"
                            disabled={loading}
                            placeholder="••••••••"
                        />
                    </div>

                    <button type="submit" className="primary" disabled={loading}>
                        {loading ? 'Ingresando...' : 'Ingresar'}
                    </button>
                </form>
            </div>
        </div>
    );
}
