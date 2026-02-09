import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';

export const generateReceipt = (saleData) => {
    try {
        // saleData: {
        //   id: number,
        //   date: string/Date,
        //   client: string,
        //   user: string,
        //   saleType: string,
        //   items: [{ codigo, descripcion, quantity, unitPrice, subtotal }],
        //   payments: [{ name, amount }],
        //   total: number
        // }

        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.width;

        // --- HEADER ---
        doc.setFontSize(18);
        doc.text("TICKET DE VENTA", pageWidth / 2, 15, { align: 'center' });

        doc.setFontSize(10);
        doc.text(`Venta ID: #${saleData.id}`, 14, 25);
        doc.text(`Fecha: ${new Date().toLocaleString()}`, 14, 30);
        doc.text(`Cliente: ${saleData.client || 'Consumidor Final'}`, 14, 35);
        doc.text(`Vendedor: ${saleData.user || 'Sistema'}`, 14, 40);
        doc.text(`Tipo Venta: ${saleData.saleType}`, 14, 45);

        // --- TABLE 1: ITEMS ---
        const itemsBody = saleData.items.map(item => {
            const unitPrice = item.unitPrice || 0;
            const discount = item.discount || 0;
            const finalUnit = Math.max(0, unitPrice - discount);
            const subtotal = finalUnit * item.quantity;

            return [
                item.codigo || 'N/A',
                item.descripcion,
                item.quantity,
                `$${unitPrice.toFixed(2)}`,
                discount > 0 ? `-$${discount.toFixed(2)}` : '-',
                `$${subtotal.toFixed(2)}`
            ];
        });

        autoTable(doc, {
            startY: 50,
            head: [['Código', 'Descripción', 'Cant.', 'Precio', 'Desc.', 'Subtotal']],
            body: itemsBody,
            theme: 'grid',
            headStyles: { fillColor: [0, 0, 0], textColor: 255 }, // Black Header
            columnStyles: {
                0: { cellWidth: 20 }, // Code
                1: { cellWidth: 'auto' }, // Desc
                2: { cellWidth: 12, halign: 'center' }, // Qty
                3: { cellWidth: 20, halign: 'right' }, // Unit Price
                4: { cellWidth: 20, halign: 'right', textColor: [200, 0, 0] }, // Discount
                5: { cellWidth: 25, halign: 'right' }  // Subtotal
            },
            styles: { fontSize: 9, cellPadding: 2 },
        });

        // --- TABLE 2: PAYMENTS & TOTALS ---
        let finalY = doc.lastAutoTable.finalY + 10;

        const paymentsBody = saleData.payments.map(p => [
            p.name,
            `$${p.amount.toFixed(2)}`
        ]);

        // Add Global Discount if exists
        if (saleData.globalDiscount > 0) {
            paymentsBody.push(['Descuento Global', `-$${saleData.globalDiscount.toFixed(2)}`]);
        }

        // Add Total Row to Payment Table
        paymentsBody.push(['TOTAL VENTA', `$${(saleData.total).toFixed(2)}`]);

        // Save
        doc.save(`Ticket_Venta_${saleData.id}.pdf`);
    } catch (error) {
        console.error("Error generating PDF:", error);
        alert("Error al generar el PDF. Revise la consola.");
    }
};
