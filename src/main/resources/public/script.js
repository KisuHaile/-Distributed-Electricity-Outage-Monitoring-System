// Poll API every 2 seconds
const API_URL = "/api/nodes";

async function fetchData() {
  try {
    const response = await fetch(API_URL);
    const nodes = await response.json();
    updateDashboard(nodes);
  } catch (error) {
    console.error("Error fetching data:", error);
    document.getElementById("sys-status").textContent = "System Offline";
    document.getElementById("sys-status").style.color = "#ef4444";
    document.getElementById("sys-status").style.background =
      "rgba(239, 68, 68, 0.2)";
  }
}

async function fetchServerStats() {
  try {
    const response = await fetch("/api/stats");
    const stats = await response.json();
    
    // Update Header
    document.querySelector("header h1").innerHTML = 
      `⚡ Server #${stats.serverId} <small style="font-size: 0.5em; opacity: 0.7;">Grid Monitor</small>`;
    
    // Update Badge wording as requested: LEADER or FOLLOWER
    const statusBadge = document.getElementById("sys-status");
    if (stats.isLeader) {
      statusBadge.innerHTML = "● LEADER (Primary)";
      statusBadge.style.color = "#22c55e";
    } else {
      statusBadge.innerHTML = "● FOLLOWER (Backup)";
      statusBadge.style.color = "#f59e0b";
    }
  } catch (error) {
    console.error("Error fetching server stats:", error);
  }
}

// Modal Logic
let currentNodes = [];

function updateDashboard(nodes) {
  currentNodes = nodes;
  // Helper to calculate stats
  let totalNodes = nodes.length;
  let onlineCount = 0;
  let offlineCount = 0;
  let outageCount = 0;

  const grid = document.getElementById("node-grid");
  grid.innerHTML = ""; // Clear current cards

  nodes.forEach((node) => {
    // 1. Parsing (Move this to the top!)
    let rawInfo = node.transformer || "0.0V | Unknown";
    let parts = rawInfo.split("|");
    let voltageStr = parts[0].trim();
    let regionStr = node.region || (parts.length > 1 && parts[1].trim() !== "Unknown" ? parts[1].trim() : "West Addis Ababa");

    // 2. Stats
    if (node.status === "ONLINE") onlineCount++;
    else offlineCount++;
    let vVal_temp = parseFloat(voltageStr) || 0;
    if (
      node.power.toLowerCase() === "off" ||
      node.power.toLowerCase() === "outage" ||
      vVal_temp <= 10
    )
      outageCount++;

    // Create Card
    const card = document.createElement("div");
    card.className = "node-card";
    card.onclick = () => openModal(node, voltageStr, regionStr, vLabel);

    // Voltage Analysis
    let vVal = parseFloat(voltageStr) || 0;
    let vLabel = "Normal";
    let vClass = "status-online";

    if (vVal > 10 && vVal < 170) {
      vLabel = "Very Low";
      vClass = "status-very-low";
    } else if (vVal >= 170 && vVal < 200) {
      vLabel = "Low";
      vClass = "status-low";
    } else if (vVal <= 10) {
      vLabel = "OUTAGE";
      vClass = "status-outage";
    }

    // Status override if server says outage
    if (
      node.power.toLowerCase() === "outage" ||
      node.power.toLowerCase() === "off"
    ) {
      vLabel = "OUTAGE";
      vClass = "status-outage";
    }

    if (node.status !== "ONLINE") {
      vClass = "status-offline";
      // Only label as OFFLINE if we don't already have an OUTAGE identified
      if (vLabel !== "OUTAGE") {
        vLabel = "OFFLINE";
        card.classList.add("node-card-offline");
      }
    }

    // Add pulse effect to outages (Priority Visual)
    if (vLabel === "OUTAGE") {
      card.classList.add("pulse-outage");
      card.classList.remove("node-card-offline"); // Ensure red wins
    }

    // Card HTML
    card.innerHTML = `
            <div class="node-header">
                <div>
                    <div class="node-title">${node.id}</div>
                    <div class="node-subtitle">${regionStr}</div>
                </div>
                <div style="font-weight:700; color: ${
                  node.status !== "ONLINE"
                    ? "#3b82f6"
                    : getVoltageColor(voltageStr)
                }">${voltageStr}</div>
            </div>
            <div class="node-status">
                <span class="status-badge-card ${vClass}">${vLabel}</span>
                <span style="font-size:0.8rem; color:var(--text-secondary);">${formatTime(
                  node.lastSeen
                )}</span>
            </div>
        `;
    grid.appendChild(card);
  });

  // Update Stats Cards
  document.getElementById("node-count").textContent = totalNodes;
  document.getElementById("online-count").textContent = onlineCount;
  document.getElementById("offline-count").textContent = offlineCount;
  document.getElementById("outage-count").textContent = outageCount;

  // Status Badge
  const badge = document.getElementById("sys-status");
  badge.style.background = "rgba(34, 197, 94, 0.2)";
}

function getVoltageColor(vStr) {
  let vVal = parseFloat(vStr) || 0;
  if (vVal >= 200) return "#22c55e"; // Green
  if (vVal >= 170) return "#eab308"; // Yellow
  if (vVal > 10) return "#ef4444"; // Red
  return "#64748b"; // Gray for outage
}

function formatTime(ts) {
  if (!ts) return "";
  return ts.split(" ")[1] || ts; // Just show time part if possible
}

// Modal Functions
function openModal(node, voltage, region, vLabel) {
  const modal = document.getElementById("detail-modal");
  const details = document.getElementById("modal-details");
  // document.getElementById('modal-title').textContent = node.id + " Details"; // Optional

  details.innerHTML = `
        <div class="detail-row">
            <span class="detail-label">District ID</span>
            <span class="detail-value">${node.id}</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Region (Zone)</span>
            <span class="detail-value">${region}</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Connection Status</span>
            <span class="detail-value" style="color:${
              node.status === "ONLINE" ? "#22c55e" : "#ef4444"
            }">${node.status}</span>
        </div>
         <div class="detail-row">
            <span class="detail-label">Power State</span>
            <span class="detail-value">${node.power}</span>
        </div>
         <div class="detail-row">
            <span class="detail-label">Grid Voltage</span>
            <span class="detail-value" style="color:${getVoltageColor(
              voltage
            )}">${voltage}</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Last Seen</span>
            <span class="detail-value">${node.lastSeen}</span>
        </div>
        <div class="detail-row">
             <span class="detail-label">Verification</span>
             <span class="detail-value" style="color: ${node.verificationStatus === 'PENDING' ? '#f59e0b' : '#94a3b8'}">
                ${node.verificationStatus || 'NONE'}
             </span>
        </div>
        ${
          (node.power !== "NORMAL" ||
          vLabel !== "Normal" ||
          node.status !== "ONLINE") && node.verificationStatus !== 'CONFIRMED'
            ? `
        <div style="margin-top: 1.5rem;">
            ${node.verificationStatus === 'PENDING' 
                ? `<button disabled class="btn-action" style="width: 100%; padding: 0.8rem; border-radius: 8px; background: #475569; color: #94a3b8; cursor: not-allowed;">⏳ Verification Pending...</button>`
                : `<button onclick="verifyNode('${node.id}')" class="btn-action" style="width: 100%; padding: 0.8rem; border-radius: 8px; background: #eab308; color: #000;">Check Again & Confirm</button>`
            }
        </div>
        `
            : ""
        }
    `;
  modal.style.display = "flex";
}

async function verifyNode(id) {
  try {
    const response = await fetch(`/api/verify?id=${id}`);
    const result = await response.json();
    if (result.status === "sent" || result.status === "queued_web") {
      alert(`Verification request sent to ${id}. Waiting for client report...`);
    } else {
      alert(`Error: ${result.error || "Failed to send request"}`);
    }
  } catch (e) {
    alert("Server communication failed.");
  }
}

function closeModal() {
  document.getElementById("detail-modal").style.display = "none";
}

// Close on outside click
window.onclick = function (event) {
  const modal = document.getElementById("detail-modal");
  if (event.target == modal) {
    modal.style.display = "none";
  }
};

// Start Polling
fetchData();
fetchServerStats();
setInterval(() => {
  fetchData();
  fetchServerStats();
}, 2000);

