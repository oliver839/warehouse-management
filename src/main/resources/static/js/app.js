const MOCK_WAREHOUSES = [
    { id: 1, name: 'Zentrallager München', location: 'München', maxCapacity: 5000 },
    { id: 2, name: 'Außenlager Hamburg', location: 'Hamburg', maxCapacity: 2200 },
    { id: 3, name: 'Werkzeuglager Berlin', location: 'Berlin', maxCapacity: 850 }
];

const MOCK_ITEMS = [
    { id: 101, name: 'Akkubohrer 18V', quantityInStock: 24, type: 'Tool' },
    { id: 102, name: 'Schrauben 5mm', quantityInStock: 15000, type: 'ConsumableMaterial' },
    { id: 103, name: 'Kalibrierter Laser-Messgerät', quantityInStock: 6, type: 'Tool' }
];

const TYPE_LABELS = {
    Tool: 'Werkzeug',
    ConsumableMaterial: 'Verbrauchsmaterial'
};

let cachedWarehouses = [];

document.addEventListener('DOMContentLoaded', () => {
    loadWarehouses();
    loadItems();
    initBookItemForm();
});

async function loadWarehouses() {
    const tbody = document.getElementById('warehouses-table-body');

    try {
        const response = await fetch('/api/warehouses');

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const warehouses = await response.json();
        cachedWarehouses = warehouses;
        renderWarehouses(warehouses, false);
        populateWarehouseSelect(warehouses);
    } catch (error) {
        console.warn('Backend nicht erreichbar – Mock-Daten für Lager werden verwendet:', error);
        cachedWarehouses = MOCK_WAREHOUSES;
        renderWarehouses(MOCK_WAREHOUSES, true);
        populateWarehouseSelect(MOCK_WAREHOUSES);
    }
}

async function loadItems() {
    const tbody = document.getElementById('items-table-body');

    try {
        const response = await fetch('/api/items');

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const items = await response.json();
        renderItems(items, false);
    } catch (error) {
        console.warn('Backend nicht erreichbar – Mock-Daten für Bestand werden verwendet:', error);
        renderItems(MOCK_ITEMS, true);
    }
}

function renderWarehouses(warehouses, isMock) {
    const tbody = document.getElementById('warehouses-table-body');
    const section = document.getElementById('warehouses-section');

    removeMockBanner(section);

    if (isMock) {
        section.querySelector('.section-card').insertAdjacentHTML(
            'afterbegin',
            mockBannerHtml('Demo-Daten: Backend /api/warehouses noch nicht verfügbar.')
        );
    }

    if (!warehouses.length) {
        tbody.innerHTML = emptyRowHtml(4, 'Keine Lager vorhanden.');
        return;
    }

    tbody.innerHTML = warehouses.map(warehouse => `
        <tr>
            <td><span class="text-muted">#${warehouse.id}</span></td>
            <td class="fw-medium">${escapeHtml(warehouse.name)}</td>
            <td>${escapeHtml(warehouse.location)}</td>
            <td>${formatNumber(warehouse.maxCapacity)}</td>
        </tr>
    `).join('');
}

function renderItems(items, isMock) {
    const tbody = document.getElementById('items-table-body');
    const section = document.getElementById('items-section');

    removeMockBanner(section);

    if (isMock) {
        section.querySelector('.section-card').insertAdjacentHTML(
            'afterbegin',
            mockBannerHtml('Demo-Daten: Backend /api/items noch nicht verfügbar.')
        );
    }

    if (!items.length) {
        tbody.innerHTML = emptyRowHtml(4, 'Kein Bestand vorhanden.');
        return;
    }

    tbody.innerHTML = items.map(item => `
        <tr>
            <td><span class="text-muted">#${item.id}</span></td>
            <td class="fw-medium">${escapeHtml(item.name)}</td>
            <td>${formatNumber(item.quantityInStock ?? item.quantity ?? 0)}</td>
            <td>
                <span class="badge badge-type ${typeBadgeClass(item.type)}">
                    ${escapeHtml(formatType(item.type))}
                </span>
            </td>
        </tr>
    `).join('');
}

function populateWarehouseSelect(warehouses) {
    const select = document.getElementById('item-warehouse');
    if (!select) return;

    select.innerHTML = `
        <option value="" selected disabled>Bitte wählen …</option>
        ${warehouses.map(w => `
            <option value="${w.id}">${escapeHtml(w.name)}</option>
        `).join('')}
    `;
}

function initBookItemForm() {
    const form = document.getElementById('book-item-form');
    if (!form) return;

    form.addEventListener('submit', event => {
        event.preventDefault();

        const name = document.getElementById('item-name').value.trim();
        const quantity = parseInt(document.getElementById('item-quantity').value, 10);
        const type = document.getElementById('item-type').value;
        const warehouseId = document.getElementById('item-warehouse').value;

        const tbody = document.getElementById('items-table-body');
        const nextId = getNextItemId(tbody);

        const newRow = document.createElement('tr');
        newRow.innerHTML = `
            <td><span class="text-muted">#${nextId}</span></td>
            <td class="fw-medium">${escapeHtml(name)}</td>
            <td>${formatNumber(quantity)}</td>
            <td>
                <span class="badge badge-type ${typeBadgeClass(type)}">
                    ${escapeHtml(formatType(type))}
                </span>
            </td>
        `;
        tbody.appendChild(newRow);

        form.reset();
        bootstrap.Modal.getInstance(document.getElementById('bookItemModal')).hide();
    });
}

function getNextItemId(tbody) {
    const ids = Array.from(tbody.querySelectorAll('tr td:first-child'))
        .map(cell => parseInt(cell.textContent.replace(/\D/g, ''), 10))
        .filter(Number.isFinite);

    return ids.length ? Math.max(...ids) + 1 : 1;
}

function formatType(type) {
    return TYPE_LABELS[type] ?? type ?? 'Unbekannt';
}

function typeBadgeClass(type) {
    return type === 'Tool' ? 'text-bg-primary' : 'text-bg-success';
}

function formatNumber(value) {
    return new Intl.NumberFormat('de-DE').format(value);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
}

function emptyRowHtml(colspan, message) {
    return `<tr><td colspan="${colspan}" class="text-center text-muted py-4">${message}</td></tr>`;
}

function mockBannerHtml(message) {
    return `<div class="mock-banner mx-3 mt-3"><i class="fa-solid fa-circle-info me-1"></i>${message}</div>`;
}

function removeMockBanner(section) {
    section.querySelectorAll('.mock-banner').forEach(banner => banner.remove());
}
