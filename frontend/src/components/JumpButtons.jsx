import React from 'react';
import './JumpButtons.css';

export default function JumpButtons({ targetTopSelector = '.main-content', targetBottomSelector = null }) {
    const scrollToTop = () => {
        const el = document.querySelector(targetTopSelector) || document.body;
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

    const scrollToBottom = () => {
        if (targetBottomSelector) {
            const el = document.querySelector(targetBottomSelector);
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'end' });
                return;
            }
        }

        const el = document.querySelector(targetTopSelector) || document.body;
        el.scrollIntoView({ behavior: 'smooth', block: 'end' });
    };

    return (
        <div className="jump-buttons">
            <button onClick={scrollToTop} className="jump-btn" aria-label="Ir arriba" title="Ir arriba">↑</button>
            <button onClick={scrollToBottom} className="jump-btn" aria-label="Ir abajo" title="Ir abajo">↓</button>
        </div>
    );
}
