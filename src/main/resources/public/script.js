// Poll API every 2 seconds
const API_URL = '/api/nodes';

async function fetchData() {
    try {
        const response = await fetch(API_URL);
        const nodes = await response.json();
        updateDashboard(nodes);
    } catch (error) {
        console.error('Error fetching data:', error);
        document.getElementById('sys-status').textContent = 'System Offline';
        document.getElementById('sys-status').style.color = '#ef4444';
        document.getElementById('sys-status').style.background = 'rgba(239, 68, 68, 0.2)';
    }
}

function updateDashboard(nodes) {
    // Helper to calculate stats
    let totalNodes = nodes.length;
    let peakLoad = 0;
    let outageCount = 0;

    const tbody = document.getElementById('node-table-body');
    tbody.innerHTML = ''; // Clear current rows

    nodes.forEach(node => {
        // Stats
        if (node.load > peakLoad) peakLoad = node.load;
        if (node.power.toLowerCase() === 'off') outageCount++;

        // Render Row
        const tr = document.createElement('tr');

        // ID / District
        const tdId = document.createElement('td');
        tdId.textContent = node.id;
        tr.appendChild(tdId);

        // Status
        const tdStatus = document.createElement('td');
        const dot = document.createElement('span');
        dot.className = `status-dot ${node.status === 'ONLINE' ? 'status-on' : 'status-off'}`;
        tdStatus.appendChild(dot);
        tdStatus.appendChild(document.createTextNode(node.status));
        tr.appendChild(tdStatus);

        // Voltage (Previously Load)
        // We stored Voltage string in 'transformer' field in DB/JSON response
        const tdVoltage = document.createElement('td');
        tdVoltage.textContent = node.transformer || '0.0V';
        tdVoltage.style.fontWeight = 'bold';
        // Color code voltage
        let vVal = parseFloat(node.transformer) || 0;
        if (vVal < 190 && vVal > 10) tdVoltage.style.color = '#eab308'; // Low
        else if (vVal <= 10) tdVoltage.style.color = '#ef4444'; // Off/Very Low
        else tdVoltage.style.color = '#22c55e'; // Normal
        tr.appendChild(tdVoltage);

        // Power State
        const tdPower = document.createElement('td');
        tdPower.textContent = node.power.toUpperCase();
        if (node.power.toLowerCase() === 'normal' || node.power.toLowerCase() === 'on') {
            tdPower.style.color = '#22c55e';
            tdPower.textContent = 'NORMAL';
        } else if (node.power.toLowerCase() === 'low') {
            tdPower.style.color = '#eab308';
            tdPower.textContent = 'LOW VOLTAGE';
        } else {
            tdPower.style.color = '#ef4444';
            tdPower.textContent = 'OUTAGE';
        }
        tr.appendChild(tdPower);

        // Region (Static 'Addis Ababa' for demo or N/A)
        const tdRegion = document.createElement('td');
        tdRegion.textContent = "Addis Ababa";
        tr.appendChild(tdRegion);

        // Last Seen
        const tdSeen = document.createElement('td');
        tdSeen.textContent = node.lastSeen;
        tr.appendChild(tdSeen);

        tbody.appendChild(tr);
    });

    // Update Stats Cards
    document.getElementById('node-count').textContent = totalNodes;
    document.getElementById('peak-load').textContent = peakLoad + '%';
    document.getElementById('outage-count').textContent = outageCount;

    // Status Badge
    const badge = document.getElementById('sys-status');
    badge.textContent = 'System Online';
    badge.style.color = '#22c55e';
    badge.style.background = 'rgba(34, 197, 94, 0.2)';
}

// Start Polling
fetchData();
setInterval(fetchData, 2000);
