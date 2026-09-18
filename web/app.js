/**
 * GRT Exam Hall Allocation - Live Web Application
 * Connected directly to Firebase: grt-exam-hall-allocation
 */

// Firebase Configuration from google-services.json
const firebaseConfig = {
  apiKey: "",
  authDomain: "grt-exam-hall-allocation.firebaseapp.com",
  projectId: "grt-exam-hall-allocation",
  storageBucket: "grt-exam-hall-allocation.firebasestorage.app",
  messagingSenderId: "765326107655",
  appId: "1:765326107655:android:8fe81ec576c122d56836a2"
};

// Initialize Firebase
let app, auth, db;
try {
  app = firebase.initializeApp(firebaseConfig);
  auth = firebase.auth();
  db = firebase.firestore();
} catch (e) {
  console.warn("Firebase init error (running in local offline mode):", e);
}

// In-Memory State
let currentUser = null;
let allStudents = [];
let allTeachers = [];
let allHalls = [];
let allExams = [];
let currentFilterYear = 2; // Default to Second Year (25)
let activeSelectedStudent = null;

// Initial Physical Halls (The 12 college rooms from GRT allocation sheet)
const DEFAULT_HALLS = [
  { id: "hall_a212", roomNumber: "A212", block: "A", floor: 2, capacity: 30, active: true },
  { id: "hall_a213", roomNumber: "A213", block: "A", floor: 2, capacity: 30, active: true },
  { id: "hall_a214", roomNumber: "A214", block: "A", floor: 2, capacity: 30, active: true },
  { id: "hall_a306", roomNumber: "A306", block: "A", floor: 3, capacity: 30, active: true },
  { id: "hall_a307", roomNumber: "A307", block: "A", floor: 3, capacity: 30, active: true },
  { id: "hall_a308", roomNumber: "A308", block: "A", floor: 3, capacity: 30, active: true },
  { id: "hall_b205", roomNumber: "B205", block: "B", floor: 2, capacity: 30, active: true },
  { id: "hall_b206", roomNumber: "B206", block: "B", floor: 2, capacity: 30, active: true },
  { id: "hall_b207", roomNumber: "B207", block: "B", floor: 2, capacity: 30, active: true },
  { id: "hall_b213", roomNumber: "B213", block: "B", floor: 2, capacity: 30, active: true },
  { id: "hall_b214", roomNumber: "B214", block: "B", floor: 2, capacity: 30, active: true },
  { id: "hall_b215", roomNumber: "B215", block: "B", floor: 2, capacity: 30, active: true },
];

// DOM Elements
const loginSection = document.getElementById("loginSection");
const appSection = document.getElementById("appSection");
const loginForm = document.getElementById("loginForm");
const loginUsername = document.getElementById("loginUsername");
const loginPassword = document.getElementById("loginPassword");
const loginError = document.getElementById("loginError");
const btnLoginSubmit = document.getElementById("btnLoginSubmit");
const btnLogout = document.getElementById("btnLogout");

// Navigation Tabs
document.querySelectorAll(".nav-item").forEach(item => {
  item.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
    document.querySelectorAll(".tab-pane").forEach(t => t.style.display = "none");
    item.classList.add("active");
    const target = item.getAttribute("data-tab");
    if (document.getElementById(target)) {
      document.getElementById(target).style.display = "block";
    }
  });
});

// -------------------------------------------------------------
// Authentication
// -------------------------------------------------------------
loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  loginError.style.display = "none";
  btnLoginSubmit.disabled = true;
  btnLoginSubmit.textContent = "Signing In...";

  let username = loginUsername.value.trim().toLowerCase();
  const password = loginPassword.value;
  const email = username.includes("@") ? username : `${username}@grt.edu.in`;

  try {
    if (auth) {
      const userCred = await auth.signInWithEmailAndPassword(email, password);
      currentUser = userCred.user;
      showApp();
      showToast("Signed in successfully as Admin");
    } else {
      // Offline Demo fallback
      currentUser = { email };
      showApp();
      showToast("Signed in (Offline Mode)");
    }
  } catch (err) {
    console.error("Login failed:", err);
    loginError.textContent = err.message || "Invalid email or password. Please verify in Firebase Console.";
    loginError.style.display = "block";
  } finally {
    btnLoginSubmit.disabled = false;
    btnLoginSubmit.textContent = "Sign In to Portal";
  }
});

btnLogout.addEventListener("click", async () => {
  if (auth) await auth.signOut();
  currentUser = null;
  appSection.style.display = "none";
  loginSection.style.display = "flex";
  showToast("Logged out successfully");
});

function showApp() {
  loginSection.style.display = "none";
  appSection.style.display = "flex";
  initRealtimeData();
}

// -------------------------------------------------------------
// Realtime Firestore Sync
// -------------------------------------------------------------
function initRealtimeData() {
  if (!db) {
    renderStudents();
    renderHalls();
    return;
  }

  // 1. Listen to Students
  db.collection("students").onSnapshot(snapshot => {
    allStudents = [];
    snapshot.forEach(doc => {
      allStudents.push({ id: doc.id, ...doc.data() });
    });
    updateStudentStats();
    renderStudents();
  }, err => console.error("Students sync error:", err));

  // 2. Listen to Halls
  db.collection("halls").onSnapshot(snapshot => {
    allHalls = [];
    snapshot.forEach(doc => allHalls.push({ id: doc.id, ...doc.data() }));
    if (allHalls.length === 0) {
      // Initialize default halls in Firestore
      DEFAULT_HALLS.forEach(h => db.collection("halls").doc(h.id).set(h));
      allHalls = DEFAULT_HALLS;
    }
    renderHalls();
  }, err => console.error("Halls sync error:", err));

  // 3. Listen to Teachers
  db.collection("teachers").onSnapshot(snapshot => {
    allTeachers = [];
    snapshot.forEach(doc => allTeachers.push({ id: doc.id, ...doc.data() }));
    renderTeachers();
  }, err => console.error("Teachers sync error:", err));
}

// -------------------------------------------------------------
// Student Management (Star Feature)
// -------------------------------------------------------------
const yearFilterGroup = document.getElementById("yearFilterGroup");
const studentSearchInput = document.getElementById("studentSearchInput");
const studentBoxesGrid = document.getElementById("studentBoxesGrid");
const emptyStudentsState = document.getElementById("emptyStudentsState");

// Filter year tabs
yearFilterGroup.querySelectorAll(".chip-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    yearFilterGroup.querySelectorAll(".chip-btn").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    currentFilterYear = parseInt(btn.getAttribute("data-year"));
    renderStudents();
  });
});

studentSearchInput.addEventListener("input", () => renderStudents());

function updateStudentStats() {
  document.getElementById("statTotalStudents").textContent = allStudents.filter(s => s.active !== false).length;
  document.getElementById("statYear2Count").textContent = allStudents.filter(s => s.year === 2 && s.active !== false).length;
  document.getElementById("statYear3Count").textContent = allStudents.filter(s => s.year === 3 && s.active !== false).length;
  document.getElementById("statYear4Count").textContent = allStudents.filter(s => s.year === 4 && s.active !== false).length;
}

function renderStudents() {
  studentBoxesGrid.innerHTML = "";
  const query = studentSearchInput.value.trim().toLowerCase();

  const filtered = allStudents.filter(student => {
    const matchesYear = student.year === currentFilterYear;
    const matchesQuery = !query || 
      (student.registerNumber && student.registerNumber.toLowerCase().includes(query)) ||
      (student.name && student.name.toLowerCase().includes(query));
    return matchesYear && matchesQuery;
  });

  // Sort by position or roll number
  filtered.sort((a, b) => (a.position || 0) - (b.position || 0));

  if (filtered.length === 0) {
    studentBoxesGrid.style.display = "none";
    emptyStudentsState.style.display = "block";
    return;
  }

  studentBoxesGrid.style.display = "grid";
  emptyStudentsState.style.display = "none";

  filtered.forEach(student => {
    const card = document.createElement("div");
    card.className = "roll-box";
    const posFormatted = `#${String(student.position || 1).padStart(3, '0')}`;
    const isActive = student.active !== false;

    card.innerHTML = `
      <div class="roll-header">
        <span class="pos-badge">${posFormatted}</span>
        <span class="status-indicator ${isActive ? 'active' : 'inactive'}">
          ${isActive ? 'ACTIVE' : 'INACTIVE'}
        </span>
      </div>
      <div class="roll-number">${student.registerNumber}</div>
      <div class="roll-subtitle">Sec ${student.section || 'A'} · CSE</div>
    `;

    // Clicking a roll number box opens the clean modal details!
    card.addEventListener("click", () => openStudentDetailsModal(student));
    studentBoxesGrid.appendChild(card);
  });
}

// -------------------------------------------------------------
// Details Modal (Tap on Box)
// -------------------------------------------------------------
function openStudentDetailsModal(student) {
  activeSelectedStudent = student;
  document.getElementById("modalDetailName").textContent = student.name || "Student Name";
  document.getElementById("modalDetailRoll").textContent = student.registerNumber;
  
  const yearLabels = { 2: "Second Year (25)", 3: "Third Year (24)", 4: "Final Year (23)" };
  document.getElementById("modalDetailYear").textContent = yearLabels[student.year] || `${student.year}th Year`;
  document.getElementById("modalDetailSec").textContent = `CSE · Sec ${student.section || 'A'}`;
  document.getElementById("modalDetailPos").textContent = `#${String(student.position || 1).padStart(3, '0')}`;
  
  const statusEl = document.getElementById("modalDetailStatus");
  if (student.active !== false) {
    statusEl.textContent = "ACTIVE ELIGIBLE";
    statusEl.style.color = "var(--green-text)";
  } else {
    statusEl.textContent = "INACTIVE / DEBARRED";
    statusEl.style.color = "var(--red-text)";
  }

  openModal("modalStudentDetails");
}

document.getElementById("btnModalModifyStudent").addEventListener("click", () => {
  closeModal("modalStudentDetails");
  if (activeSelectedStudent) {
    openAddEditStudentModal(activeSelectedStudent);
  }
});

document.getElementById("btnModalDeleteStudent").addEventListener("click", async () => {
  if (!activeSelectedStudent) return;
  if (!confirm(`Are you sure you want to remove roll number ${activeSelectedStudent.registerNumber}?`)) return;

  try {
    if (db) {
      await db.collection("students").doc(activeSelectedStudent.id).delete();
    } else {
      allStudents = allStudents.filter(s => s.id !== activeSelectedStudent.id);
      renderStudents();
    }
    closeModal("modalStudentDetails");
    showToast(`Roll number ${activeSelectedStudent.registerNumber} deleted`);
  } catch (err) {
    alert("Error deleting student: " + err.message);
  }
});

// -------------------------------------------------------------
// Add / Modify Modal
// -------------------------------------------------------------
const btnOpenAddStudent = document.getElementById("btnOpenAddStudent");
const modalAddEditStudent = document.getElementById("modalAddEditStudent");
const modalAddEditTitle = document.getElementById("modalAddEditTitle");
const formAddEditStudent = document.getElementById("formAddEditStudent");
const editStudentId = document.getElementById("editStudentId");
const dialogYearGroup = document.getElementById("dialogYearGroup");
const inputRollNumber = document.getElementById("inputRollNumber");
const inputStudentName = document.getElementById("inputStudentName");
const inputSection = document.getElementById("inputSection");
const inputPosition = document.getElementById("inputPosition");
const inputActive = document.getElementById("inputActive");

let dialogSelectedYear = 2;

btnOpenAddStudent.addEventListener("click", () => openAddEditStudentModal(null));

function openAddEditStudentModal(existing) {
  dialogSelectedYear = existing ? existing.year : currentFilterYear;
  
  // Set dialog chip
  dialogYearGroup.querySelectorAll(".chip-btn").forEach(btn => {
    btn.classList.toggle("active", parseInt(btn.getAttribute("data-year")) === dialogSelectedYear);
  });

  const prefixes = { 2: "110325104", 3: "110324104", 4: "110323104" };

  if (existing) {
    modalAddEditTitle.textContent = "Modify Roll Number";
    editStudentId.value = existing.id;
    inputRollNumber.value = existing.registerNumber;
    inputStudentName.value = existing.name;
    inputSection.value = existing.section || "A";
    inputPosition.value = existing.position || "";
    inputActive.checked = existing.active !== false;
  } else {
    modalAddEditTitle.textContent = "Add Roll Number";
    editStudentId.value = "";
    inputRollNumber.value = prefixes[dialogSelectedYear];
    inputStudentName.value = "";
    inputSection.value = "A";
    
    // Suggest position
    const currentMax = allStudents.filter(s => s.year === dialogSelectedYear).reduce((max, s) => Math.max(max, s.position || 0), 0);
    inputPosition.value = currentMax + 1;
    inputActive.checked = true;
  }

  openModal("modalAddEditStudent");
  inputRollNumber.focus();
}

// Switching year inside modal auto-swaps prefix
dialogYearGroup.querySelectorAll(".chip-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    dialogYearGroup.querySelectorAll(".chip-btn").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    dialogSelectedYear = parseInt(btn.getAttribute("data-year"));

    const prefixes = { 2: "110325104", 3: "110324104", 4: "110323104" };
    const currentVal = inputRollNumber.value;
    let suffix = "";
    if (currentVal.length >= 9 && (currentVal.startsWith("110325104") || currentVal.startsWith("110324104") || currentVal.startsWith("110323104"))) {
      suffix = currentVal.substring(9);
    }
    inputRollNumber.value = prefixes[dialogSelectedYear] + suffix;
  });
});

formAddEditStudent.addEventListener("submit", async (e) => {
  e.preventDefault();
  const regNo = inputRollNumber.value.trim();
  const name = inputStudentName.value.trim();
  const section = inputSection.value.trim().toUpperCase() || "A";
  const position = parseInt(inputPosition.value) || 1;
  const active = inputActive.checked;
  const existingId = editStudentId.value;

  if (!regNo || !name) {
    alert("Please fill in both Roll Number and Student Name.");
    return;
  }

  // Check duplicate
  const duplicate = allStudents.find(s => s.registerNumber === regNo && s.id !== existingId);
  if (duplicate) {
    alert(`Roll number ${regNo} is already assigned to ${duplicate.name}.`);
    return;
  }

  const studentData = {
    registerNumber: regNo,
    name: name,
    year: dialogSelectedYear,
    section: section,
    position: position,
    active: active
  };

  try {
    if (db) {
      const docId = existingId || `stu_${regNo}`;
      await db.collection("students").doc(docId).set(studentData, { merge: true });
    } else {
      if (existingId) {
        const idx = allStudents.findIndex(s => s.id === existingId);
        if (idx >= 0) allStudents[idx] = { id: existingId, ...studentData };
      } else {
        allStudents.push({ id: `stu_${regNo}`, ...studentData });
      }
      renderStudents();
    }
    closeModal("modalAddEditStudent");
    showToast(`Roll number ${regNo} saved successfully!`);
  } catch (err) {
    alert("Failed to save student: " + err.message);
  }
});

// -------------------------------------------------------------
// Real Institutional Data Loader (Anna Univ Format)
// -------------------------------------------------------------
document.getElementById("btnLoadRealData").addEventListener("click", async () => {
  if (!confirm("This will load the authentic 12-digit Anna University roll numbers for 2nd, 3rd, and Final year CSE batches. Proceed?")) return;

  const realStudents = [];
  const commonNames = [
    "Aravind Kumar K", "Abinaya S", "Ajith Kumar M", "Anand R", "Bhavani P",
    "Deepak Raj V", "Dhivya M", "Gokul Nath S", "Hariharan K", "Hemalatha R",
    "Jeevitha M", "Karthik Raja P", "Keerthana V", "Kishore Kumar S", "Lavanya N",
    "Manikandan R", "Monisha K", "Naveen Kumar A", "Nithya Shree S", "Pavithra M",
    "Praveen Raj V", "Priyadharshini R", "Rahul S", "Ramya K", "Santhosh Kumar M"
  ];

  // Year 2 (25): 110325104001..120 (missing 31, 79, Subash R at 107)
  const y2Missing = new Set([31, 79]);
  for (let i = 1; i <= 120; i++) {
    if (y2Missing.has(i)) continue;
    const name = i === 107 ? "Subash R" : `${commonNames[(i-1) % commonNames.length]} (2Y-${i})`;
    const reg = `110325104${String(i).padStart(3, '0')}`;
    realStudents.push({ id: `stu_${reg}`, registerNumber: reg, name, year: 2, section: "A", position: i, active: true });
  }

  // Year 3 (24): 110324104001..120 (Lokesh S at 60)
  for (let i = 1; i <= 120; i++) {
    const name = i === 60 ? "Lokesh S" : `${commonNames[(i-1) % commonNames.length]} (3Y-${i})`;
    const reg = `110324104${String(i).padStart(3, '0')}`;
    realStudents.push({ id: `stu_${reg}`, registerNumber: reg, name, year: 3, section: "A", position: i, active: true });
  }

  // Year 4 (23): 110323104001..120
  for (let i = 1; i <= 120; i++) {
    const name = `${commonNames[(i-1) % commonNames.length]} (4Y-${i})`;
    const reg = `110323104${String(i).padStart(3, '0')}`;
    realStudents.push({ id: `stu_${reg}`, registerNumber: reg, name, year: 4, section: "A", position: i, active: true });
  }

  if (db) {
    const batch = db.batch();
    realStudents.forEach(s => batch.set(db.collection("students").doc(s.id), s));
    await batch.commit();
  } else {
    allStudents = realStudents;
    renderStudents();
  }
  showToast("Loaded institutional roll numbers successfully!");
});

// -------------------------------------------------------------
// Wipe Mock Data (Clean Slate Action)
// -------------------------------------------------------------
document.getElementById("btnWipeData").addEventListener("click", async () => {
  if (!confirm("Are you sure you want to wipe all student records? This will leave your database completely empty (0 students) for a clean production start.")) return;

  if (db) {
    const snapshot = await db.collection("students").get();
    const batch = db.batch();
    snapshot.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
  } else {
    allStudents = [];
    renderStudents();
  }
  showToast("Database wiped clean (0 students).");
});

// -------------------------------------------------------------
// Seating Arrangement Generator
// -------------------------------------------------------------
document.getElementById("btnRunGenerator").addEventListener("click", () => {
  const phase = parseInt(document.getElementById("examPhasePicker").value);
  const container = document.getElementById("seatingHallsContainer");
  container.innerHTML = "";

  const yearA = 2;
  const yearB = phase === 1 ? 3 : 4;
  const poolA = allStudents.filter(s => s.year === yearA && s.active !== false);
  const poolB = allStudents.filter(s => s.year === yearB && s.active !== false);

  if (poolA.length === 0 || poolB.length === 0) {
    alert(`Cannot generate seating: Need active students in both Year ${yearA} and Year ${yearB}.`);
    return;
  }

  let indexA = 0;
  let indexB = 0;

  DEFAULT_HALLS.forEach((hall, hIdx) => {
    const hallCard = document.createElement("div");
    hallCard.className = "seating-hall-card";

    let deskSlotsHtml = "";
    for (let d = 1; d <= 30; d++) {
      const isColA = d % 2 === 1;
      let student = null;
      if (isColA && indexA < poolA.length) student = poolA[indexA++];
      else if (!isColA && indexB < poolB.length) student = poolB[indexB++];

      deskSlotsHtml += `
        <div class="desk-slot ${isColA ? 'col-a' : 'col-b'}">
          <div class="desk-num">DESK #${d}</div>
          <div class="desk-roll">${student ? student.registerNumber : 'EMPTY'}</div>
          <div class="desk-year">${student ? `${student.year}Y CSE (${student.section || 'A'})` : 'VACANT'}</div>
        </div>
      `;
    }

    hallCard.innerHTML = `
      <div class="hall-header">
        <span class="hall-badge">Room ${hall.roomNumber} (Block ${hall.block})</span>
        <span class="invigilator-badge">Invigilator: Faculty Duty #${hIdx + 1}</span>
      </div>
      <div class="desk-matrix">${deskSlotsHtml}</div>
    `;

    container.appendChild(hallCard);
  });

  showToast("Seating arrangement generated successfully!");
});

document.getElementById("btnPrintArrangement").addEventListener("click", () => {
  window.print();
});

// -------------------------------------------------------------
// Halls & Teachers Renderers
// -------------------------------------------------------------
function renderHalls() {
  const container = document.getElementById("hallsListContainer");
  container.innerHTML = "";
  const hallsToRender = allHalls.length > 0 ? allHalls : DEFAULT_HALLS;

  hallsToRender.forEach(h => {
    const div = document.createElement("div");
    div.className = "roll-box";
    div.innerHTML = `
      <div class="roll-header">
        <span class="pos-badge">Block ${h.block}</span>
        <span class="status-indicator active">30 SEATS</span>
      </div>
      <div class="roll-number" style="font-size: 18px;">Room ${h.roomNumber}</div>
      <div class="roll-subtitle">Floor ${h.floor} · Standard Hall</div>
    `;
    container.appendChild(div);
  });
}

function renderTeachers() {
  const container = document.getElementById("teachersListContainer");
  container.innerHTML = "";
  allTeachers.forEach(t => {
    const div = document.createElement("div");
    div.className = "roll-box";
    div.innerHTML = `
      <div class="roll-header">
        <span class="pos-badge">${t.role || 'TEACHER'}</span>
        <span class="status-indicator active">ACTIVE</span>
      </div>
      <div class="roll-number" style="font-size: 16px;">${t.name}</div>
      <div class="roll-subtitle">${t.username}@grt.edu.in</div>
    `;
    container.appendChild(div);
  });
}

// -------------------------------------------------------------
// Modal & Toast Utilities
// -------------------------------------------------------------
function openModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add("open");
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove("open");
}

function showToast(message) {
  const container = document.getElementById("toastContainer");
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 3500);
}
