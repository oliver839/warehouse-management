let selectedWarehouseId = null;

document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    fetchWarehouses();
    fetchItems();
    fetchProjects();
    document.getElementById('warehouseForm').addEventListener('submit', createWarehouse);
    document.getElementById('itemForm').addEventListener('submit', createItem);
    document.getElementById('projectForm').addEventListener('submit', createProject);
    document.getElementById('allocationForm').addEventListener('submit', createAllocation);
    document.getElementById('resetFilterBtn').addEventListener('click', resetWarehouseFilter);
    document.getElementById('themeToggle').addEventListener('click', toggleTheme);
});

function initializeTheme() {
    const savedTheme = localStorage.getItem('warehouse-theme');
    const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    setTheme(savedTheme || systemTheme);
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-bs-theme');
    setTheme(currentTheme === 'dark' ? 'light' : 'dark');
}

function setTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    localStorage.setItem('warehouse-theme', theme);
    const icon = document.getElementById('themeToggleIcon');
    if (icon) {
        icon.className = theme === 'dark' ? 'bi bi-sun-fill' : 'bi bi-moon-stars-fill';
    }
}

function fetchWarehouses() {
    const list = document.getElementById('warehouse-list');
    return fetch('/api/warehouses').then(ensureSuccessfulResponse).then(warehouses => {
        populateWarehouseSelect(warehouses);
        if (!warehouses.length) return showEmptyMessage(list, 5);
        list.innerHTML = warehouses.map(warehouse => `
            <tr class="warehouse-row ${warehouse.id === selectedWarehouseId ? 'table-active' : ''}" data-id="${warehouse.id}" data-name="${escapeHtml(warehouse.name)}">
                <td>${warehouse.id}</td><td>${escapeHtml(warehouse.name)}</td><td>${escapeHtml(warehouse.location)}</td>
                <td>Max: ${formatVolume(warehouse.maxSpace)} m³</td>
                <td><button class="btn btn-danger btn-sm delete-warehouse" data-id="${warehouse.id}">Löschen</button></td>
            </tr>`).join('');
        list.querySelectorAll('.warehouse-row').forEach(row => row.addEventListener('click', () => activateWarehouseFilter(row.dataset.id, row.dataset.name)));
        list.querySelectorAll('.delete-warehouse').forEach(button => button.addEventListener('click', event => {
            event.stopPropagation();
            deleteWarehouse(button.dataset.id);
        }));
    }).catch(() => showEmptyMessage(list, 5));
}

function fetchItems() {
    return fetch('/api/items').then(ensureSuccessfulResponse).then(items => {
        populateAllocationItemSelect(items);
        renderItems(items);
    }).catch(() => showEmptyMessage(document.getElementById('item-list'), 5));
}

function fetchItemsByWarehouse(warehouseId) {
    return fetch(`/api/warehouses/${warehouseId}/items`).then(ensureSuccessfulResponse).then(renderItems)
        .catch(() => showEmptyMessage(document.getElementById('item-list'), 5));
}

function renderItems(items) {
    const list = document.getElementById('item-list');
    if (!items.length) return showEmptyMessage(list, 5);
    list.innerHTML = items.map(item => {
        const available = item.availableQuantity ?? item.quantityInStock;
        const lowStock = item.quantityInStock > 0 && available / item.quantityInStock < 0.2;
        return `<tr>
            <td>${item.id}</td><td>${escapeHtml(item.name)}</td>
            <td class="${lowStock ? 'text-danger fw-bold' : ''}">${available} / ${item.quantityInStock}</td>
            <td>${escapeHtml(item.warehouse?.name || 'Nicht zugewiesen')}</td>
            <td><button class="btn btn-danger btn-sm delete-item" data-id="${item.id}">Löschen</button></td>
        </tr>`;
    }).join('');
    list.querySelectorAll('.delete-item').forEach(button => button.addEventListener('click', () => deleteItem(button.dataset.id)));
}

function fetchProjects() {
    const list = document.getElementById('project-list');
    return fetch('/api/projects').then(ensureSuccessfulResponse).then(projects => {
        populateAllocationProjectSelect(projects);
        if (!projects.length) return showEmptyMessage(list, 6);
        list.innerHTML = projects.map(project => `
            <tr>
                <td>${project.id}</td><td>${escapeHtml(project.name)}</td><td>${escapeHtml(project.description)}</td>
                <td>${renderResources(project.allocations)}</td>
                <td>${statusBadge(project.status)}</td>
                <td>${renderProjectActions(project)}</td>
            </tr>`).join('');
        list.querySelectorAll('[data-project-status]').forEach(button => button.addEventListener('click', () =>
            changeProjectStatus(button.dataset.projectId, button.dataset.projectStatus)));
    }).catch(() => showEmptyMessage(list, 6));
}

function renderResources(allocations = []) {
    if (!allocations.length) return '<span class="text-muted">Keine Ressourcen</span>';
    return allocations.map(allocation => `<span class="badge text-bg-light border text-dark me-1">${escapeHtml(allocation.inventoryItem?.name || 'Item')} × ${allocation.allocatedQuantity}</span>`).join('');
}

function statusBadge(status) {
    const badgeClasses = {
        PENDING: 'text-bg-warning',
        APPROVED: 'text-bg-primary',
        IN_PROGRESS: 'text-bg-primary',
        COMPLETED: 'text-bg-success',
        REJECTED: 'text-bg-danger'
    };
    return `<span class="badge rounded-pill ${badgeClasses[status] || 'text-bg-secondary'} px-3 py-2">${escapeHtml(status)}</span>`;
}

function renderProjectActions(project) {
    if (project.status === 'PENDING') {
        return statusButton(project.id, 'APPROVED', 'Genehmigen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger');
    }
    if (project.status === 'APPROVED') {
        return statusButton(project.id, 'IN_PROGRESS', 'Starten', 'btn-primary') + statusButton(project.id, 'COMPLETED', 'Abschließen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger');
    }
    if (project.status === 'IN_PROGRESS') {
        return statusButton(project.id, 'COMPLETED', 'Abschließen', 'btn-success') + statusButton(project.id, 'REJECTED', 'Ablehnen', 'btn-outline-danger');
    }
    return '<span class="text-muted">Keine Aktionen</span>';
}

function statusButton(projectId, status, label, cssClass) {
    return `<button class="btn ${cssClass} btn-sm rounded-pill me-1 mb-1" data-project-id="${projectId}" data-project-status="${status}">${label}</button>`;
}

function changeProjectStatus(projectId, newStatus) {
    fetch(`/api/projects/${projectId}/status?newStatus=${newStatus}`, { method: 'PATCH' })
        .then(ensureSuccessfulResponse)
        .then(() => Promise.all([fetchProjects(), refreshVisibleItems()]))
        .catch(error => alert(error.message || 'Der Auftragsstatus konnte nicht geändert werden.'));
}

function activateWarehouseFilter(warehouseId, warehouseName) {
    selectedWarehouseId = Number(warehouseId);
    document.querySelectorAll('.warehouse-row').forEach(row => row.classList.toggle('table-active', Number(row.dataset.id) === selectedWarehouseId));
    document.getElementById('inventoryTitle').textContent = `Inventar (Lager: ${warehouseName})`;
    document.getElementById('resetFilterBtn').classList.remove('d-none');
    fetchItemsByWarehouse(selectedWarehouseId);
}

function resetWarehouseFilter() {
    selectedWarehouseId = null;
    document.getElementById('inventoryTitle').textContent = 'Inventar';
    document.getElementById('resetFilterBtn').classList.add('d-none');
    document.querySelectorAll('.warehouse-row').forEach(row => row.classList.remove('table-active'));
    fetchItems();
}

function refreshVisibleItems() {
    return selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : fetchItems();
}

function populateWarehouseSelect(warehouses) {
    const select = document.getElementById('warehouseSelect');
    select.innerHTML = warehouses.length ? warehouses.map(warehouse => `<option value="${warehouse.id}">${escapeHtml(warehouse.name)}</option>`).join('') : '<option value="" disabled selected>Keine Lager verfügbar</option>';
}

function populateAllocationItemSelect(items) {
    const select = document.getElementById('allocationItemSelect');
    select.innerHTML = items.length ? items.map(item => `<option value="${item.id}">${escapeHtml(item.name)} (${item.availableQuantity ?? item.quantityInStock} verfügbar)</option>`).join('') : '<option value="" disabled selected>Keine Items verfügbar</option>';
}

function populateAllocationProjectSelect(projects) {
    const select = document.getElementById('allocationProjectSelect');
    const pendingProjects = projects.filter(project => project.status === 'PENDING');
    select.innerHTML = pendingProjects.length ? pendingProjects.map(project => `<option value="${project.id}">${escapeHtml(project.name)}</option>`).join('') : '<option value="" disabled selected>Keine ausstehenden Aufträge</option>';
}

function createWarehouse(event) {
    event.preventDefault();
    const payload = { name: document.getElementById('warehouseName').value.trim(), location: document.getElementById('warehouseLocation').value.trim(), maxSpace: Number(document.getElementById('maxSpace').value) };
    sendJson('/api/warehouses', 'POST', payload).then(() => closeAndReset('warehouseModal', event.target)).then(fetchWarehouses)
        .catch(error => alert(error.message || 'Das Lager konnte nicht gespeichert werden.'));
}

function createItem(event) {
    event.preventDefault();
    const payload = { name: document.getElementById('itemName').value.trim(), quantityInStock: Number(document.getElementById('itemQuantityInStock').value), spacePerUnit: Number(document.getElementById('spacePerUnit').value), warehouseId: Number(document.getElementById('warehouseSelect').value) };
    sendJson('/api/items', 'POST', payload).then(() => closeAndReset('itemModal', event.target)).then(() => {
        return fetchItems().then(() => selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : null);
    }).catch(error => alert(error.message || 'Das Item konnte nicht gespeichert werden.'));
}

function createProject(event) {
    event.preventDefault();
    const payload = { name: document.getElementById('projectName').value.trim(), description: document.getElementById('projectDescription').value.trim() };
    sendJson('/api/projects', 'POST', payload).then(() => closeAndReset('projectModal', event.target)).then(fetchProjects)
        .catch(error => alert(error.message || 'Der Auftrag konnte nicht gespeichert werden.'));
}

function createAllocation(event) {
    event.preventDefault();
    const projectId = document.getElementById('allocationProjectSelect').value;
    const payload = { inventoryItemId: Number(document.getElementById('allocationItemSelect').value), allocatedQuantity: Number(document.getElementById('allocationQuantity').value) };
    sendJson(`/api/projects/${projectId}/allocations`, 'POST', payload).then(() => closeAndReset('allocationModal', event.target)).then(fetchProjects)
        .catch(error => alert(error.message || 'Die Ressource konnte nicht zugewiesen werden.'));
}

function deleteWarehouse(id) {
    fetch(`/api/warehouses/${id}`, { method: 'DELETE' }).then(ensureSuccessfulResponse).then(() => {
        if (Number(id) === selectedWarehouseId) resetWarehouseFilter();
        return fetchWarehouses();
    }).catch(() => alert('Das Lager konnte nicht gelöscht werden. Lösche zuerst die zugeordneten Items.'));
}

function deleteItem(id) {
    fetch(`/api/items/${id}`, { method: 'DELETE' }).then(ensureSuccessfulResponse).then(() => {
        return fetchItems().then(() => selectedWarehouseId ? fetchItemsByWarehouse(selectedWarehouseId) : null);
    }).catch(() => alert('Das Item konnte nicht gelöscht werden.'));
}

function closeAndReset(modalId, form) {
    bootstrap.Modal.getInstance(document.getElementById(modalId)).hide();
    form.reset();
}

function sendJson(url, method, payload) {
    return fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }).then(ensureSuccessfulResponse);
}

function ensureSuccessfulResponse(response) {
    if (!response.ok) return response.text().then(message => Promise.reject(new Error(message || `HTTP ${response.status}`)));
    return response.status === 204 ? null : response.json();
}

function showEmptyMessage(tableBody, columnCount) {
    tableBody.innerHTML = `<tr><td colspan="${columnCount}" class="text-center text-muted">Keine Daten verfügbar</td></tr>`;
}

function formatVolume(value) {
    return new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 }).format(value ?? 0);
}

function escapeHtml(value) {
    const element = document.createElement('div');
    element.textContent = value ?? '';
    return element.innerHTML;
}
