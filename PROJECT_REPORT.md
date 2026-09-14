# GRT Exam Seating Arrangement — Project Report & User Guide

**App name:** GRT Exam Seating Arrangement
**Package:** `com.example.examhallallocation`
**Platform:** Android (min SDK 24 / Android 7.0+, target SDK 37)
**Version:** 1.0
**Architecture:** MVVM + Hilt DI + Room (offline-first) + Navigation Component

---

## 🔑 1. Demo Credentials (the part you asked for first)

The app runs in **offline demo mode** (no Firebase configured). On first launch it seeds itself with sample data and registers these accounts:

| Role | Username | Password | Logged in as |
|---|---|---|---|
| **Admin** | `admin` | `admin123` | "Exam Cell Admin" |
| **Teacher** | `rkumar` | `teacher123` | "R. Kumar" (Normal Teacher) |

**You don't even need to type them** — the login screen has two shortcut buttons, **"Sign in as Demo Admin"** and **"Sign in as Demo Teacher"**, which fill and submit the form in one tap.

Where they live in code (for your reference):
- `app/src/main/java/com/example/examhallallocation/data/seed/SeedData.kt` — constants `DEMO_ADMIN_USERNAME/PASSWORD`, `DEMO_TEACHER_USERNAME/PASSWORD`
- `app/src/main/java/com/example/examhallallocation/data/seed/DatabaseSeeder.kt` — registers them on first launch

> Notes:
> - Passwords are stored as salted SHA-256 hashes in EncryptedSharedPreferences, not plaintext.
> - There is **no UI to change passwords** and no "forgot password". Deactivating a teacher blocks their login.
> - New teachers you add from **Manage Teachers** get whatever "Initial Password" you type in the dialog.
> - Other seeded teacher usernames (e.g. `priya`, `anand`, `meena`…) have **no registered password** — only `rkumar` can log in as a teacher.

---

## 🧭 2. What the App Does

It automates exam hall seating + invigilation duty allocation for a college:

1. **Admin** sets up students, teachers, halls, and the exam timetable.
2. The app **auto-generates a seating arrangement** per exam day following strict college rules.
3. Admin previews, approves, and exports a formal **PDF**.
4. **Teachers** log in and see only their own duty, hall, and student list.

### Business rules enforced by the engine (and validated after every change)
- Students are grouped into fixed **15-position batches**; gaps (debarred/missing roll numbers) are preserved, never back-filled.
- **Phase 1** (2nd+3rd+4th year): max **12 halls**; **Phase 2** (2nd+3rd): max **8**; **Phase 3** (3rd only): exactly **2 halls**, two students per bench (so double capacity is allowed).
- Hall exam capacity (30) is never exceeded.
- **One invigilator per occupied hall**; a teacher never gets two halls on the same day.
- **HOD is exempt** from all duties; the **Exam Cell Coordinator** serves at most **one day** in the whole exam.
- Duty workload is balanced across days (max/min difference ≤ 1), consecutive-day runs minimized, ties broken randomly.

---

## 📱 3. Screen-by-Screen Walkthrough (all 11 screens)

### 3.1 Login Screen
- Username + password fields, Sign In button.
- Demo shortcut buttons (visible only in demo mode), plus an "Offline mode – demo credentials in use" note.
- Role-based routing: `ADMIN` → Admin Dashboard; every other role → Teacher Dashboard.
- Error banner for invalid credentials; loading state disables the button.

### 3.2 Admin Dashboard (home after admin login)
- **4 stat cards:** Total Students, Active Teachers, Active Halls, Exam Status (shows "Draft" once any arrangement exists).
- **Gold "Upcoming Examination" card:** latest exam name + two big buttons:
  - **Generate Arrangement** → generation screen
  - **Preview Arrangement** → preview screen
- **Quick access list:** Manage Students / Manage Teachers / Exam Setup / Manage Halls / Export PDF / Logout (with confirm dialog).

### 3.3 Student Management (admin)
- Search box (by name or register number), year filter chips (2nd / 3rd / 4th / All).
- Count header ("X active students") and per-year breakdown.
- **Add Student** (form: register number, name, year, section, position) and **Import CSV**.
- Each row: Edit, Delete (with confirm dialog).
- CSV format: `RegisterNumber,Name,Year,Section,Position` — header optional, duplicates skipped, position auto-assigned if omitted/0.

### 3.4 Teacher Management (admin)
- List of teachers with role and active status.
- **Add Teacher:** name, username, initial password, role picker (Teacher / Exam Cell Coordinator / HOD / Admin). Username is fixed once created.
- **Edit:** name, role, active toggle (deactivated teachers can't log in or receive duties).
- "Delete" actually **deactivates** (safe for historical duty records).

### 3.5 Exam Setup (admin)
- List of exams grouped by date ("Day X of 7" style).
- **Add Exam:** date (material date picker), year picker (2nd/3rd/4th), semester, subject code, subject name.
- Delete exam. The exam schedule is what drives phases on the Generate screen.

### 3.6 Hall Management (admin)
- List of halls: room number, block, floor, capacity, active status.
- **Add / Edit hall:** room number, block, floor, exam capacity, active toggle.
- Delete hall. Inactive halls are excluded from generation.

### 3.7 Generate Arrangement (admin)
- Shows the **next ungenerated exam date**, exam name, detected **phase**, and applicable years.
- **GENERATE ARRANGEMENT** button with progress indicator.
- On success: **summary card** — Halls used / Students seated / Invigilators assigned.
- On failure: human-readable error list (e.g., not enough halls, no active students, not enough teachers).
- Rules applied are listed on screen. After success you can jump straight to **Preview**.

### 3.8 Arrangement Preview (admin)
- Table of every hall block: Date, Room, Block, Year/Sem, position range, student count, invigilator name.
- **Approve** (confirm dialog) → unlocks PDF export. Status becomes APPROVED.
- **Regenerate** for the latest date (confirm dialog) — creates a fresh randomized, still-fully-validated arrangement.
- **Export PDF** button — blocked with an explanatory dialog until approved.
- Empty state if nothing generated yet.

### 3.9 PDF Export (admin)
- Summary line: Days · Hall blocks · Students seated.
- Requires **all** arrangements to be APPROVED.
- Generates a formal A4 landscape PDF (iText): college logo + header, "EXAMINATION HALL ALLOCATION", per-phase sections, navy/white print-friendly tables (S.No, Room, Floor, Branch, Year/Sem, Reg. No. From/To, No. of Students, Total), signature lines (Exam Cell Coordinator / Principal).
- Saved via MediaStore to **`Documents/GRT_Exam_Arrangement/`** with timestamped filename, then opens in any PDF viewer (toast fallback if none installed).

### 3.10 Teacher Dashboard ("My Duties")
- Welcome header with the teacher's name; role notes for HOD/Coordinator.
- **"Do I have duty today?"** status: green "Duty on \<date\>" or "No invigilation duty assigned".
- Duty details: My Hall (room/block/floor), Year/Sem, subjects that day.
- **Students in my hall** list (register number + name, sorted by position).
- Stat cards: Total duty days, Free days.
- **Profile** button and **Logout** (confirm dialog).

### 3.11 Teacher Profile
- Name, @username, role label, Logout button.

### Navigation map
```
Login ──admin──▶ Admin Dashboard ─▶ Students / Teachers / Exams / Halls
  │                        │
  │                        ├─▶ Generate ─▶ Preview ─▶ PDF Export
  │                        │
  teacher─▶ Teacher Dashboard ─▶ Profile
```
Logout from any screen returns to Login (back stack cleared).

---

## ✅ 4. What Works (verified in code + unit tests)

| Area | Status |
|---|---|
| Demo login with one-tap shortcuts | ✅ Works |
| Role-based routing (admin vs teacher) | ✅ Works |
| Students: add/edit/delete/search/filter/CSV import | ✅ Works (duplicate reg-no protection, per-line CSV errors) |
| Teachers: add/edit/deactivate with roles & initial passwords | ✅ Works |
| Exams: add with date picker, delete | ✅ Works |
| Halls: add/edit/delete/activate, capacity respected | ✅ Works |
| Seating engine: 15-position batches, gaps preserved, phase logic, capacity | ✅ Works (covered by 20 unit tests) |
| Invigilation engine: HOD exempt, coordinator 1 day, 1 hall/teacher/day, workload balancing | ✅ Works (covered by tests) |
| Post-generation validation (edits can't bypass rules) | ✅ Works |
| Preview: approve / regenerate / export gating | ✅ Works |
| PDF generation + save to Documents + auto-open | ✅ Works (needs a PDF viewer installed to auto-open) |
| Teacher dashboard: own duty, hall roster, privacy (sees only own data) | ✅ Works |
| Offline-first: everything works with no internet | ✅ Works |
| Deterministic demo seed on first launch (353 students, 15 teachers, 12 halls, 17 exams) | ✅ Works |

---

## ⚠️ 5. What's Missing / Known Limitations (be honest when sharing)

1. **Demo mode only** — no `google-services.json`, so Firebase auth/sync is compiled in but inactive. Data is **per-device**; two phones never see each other's data.
2. **No password change / reset** — demo passwords are fixed; anyone with the APK knows `admin123`.
3. **No manual edit of assignments in the UI** — the code has a `validateAndSave()` hook for manual edits, but no screen exposes it. Only Approve / Regenerate exist.
4. **Phase detection is automatic** — you can't manually force a phase; it's derived from which years have exams on that date.
5. **Generate walks dates in order** — the Generate screen always picks the *first date without an arrangement*; you can't generate day 3 before day 1. Regenerate (in Preview) replaces one specific date.
6. **Regenerate is random** — re-running produces a different (but always rule-compliant) arrangement; there's no "lock" to keep parts you liked.
7. **PDF "Branch" column is hardcoded** to "B.Tech CSE".
8. **Teacher duty screen shows only the first duty's details** in the breakdown card (duty list itself shows all days via the count cards).
9. **No dark mode, no landscape tablet layout, no multi-language** support.
10. **No data export/backup** other than the PDF; uninstalling clears all data (seeder will re-create demo data on next install).
11. **Min Android 7.0** — won't install on Android 6 or older.
12. **Release build is unoptimized** (`minifyEnabled false`) — fine for review, larger APK than necessary.

*(Nothing found that is outright broken — flows compile, are wired correctly, and the engine is covered by a solid test suite: `app/src/test/.../ArrangementGeneratorTest.kt`.)*

---

## 📦 6. How to Build & Share the APK

### Quick (debug APK — recommended for UI review)
```bash
# From project root (Windows Git Bash / any shell)
./gradlew assembleDebug
```
Output: **`app/build/outputs/apk/debug/app-debug.apk`**

This is the one to share for UI/feature verification. Debug builds can be installed directly on any Android 7.0+ phone ("Install unknown apps" permission needed).

### Optional: release APK
The project has no signing config, so a plain release APK will be unsigned and **will not install**. Either sign it yourself, or stick to the debug APK for review purposes.

### Sharing checklist
1. Share the APK via Google Drive / WhatsApp / email (rename it to something friendly like `GRT-Exam-Setup-demo.apk`).
2. Recipient enables **Install unknown apps** for their browser/file manager, then taps the APK.
3. First launch auto-seeds demo data (353 students, 15 teachers, 12 halls, 17 exams over 7 days).
4. Log in with the demo buttons — **no typing required**.
5. Suggested review script for testers:
   - Login as **Admin** → glance at dashboard stats.
   - **Manage Students** → try search + year chips + add one.
   - **Exam Setup** → view the seeded 7-day plan.
   - **Generate** → press GENERATE, note summary numbers.
   - **Preview** → check table, press **Approve**.
   - **Export PDF** → confirm the PDF opens from Documents/GRT_Exam_Arrangement.
   - Logout → login as **Teacher (rkumar)** → see duty + hall students.
6. Ask testers to report: UI glitches, crashes, confusing wording — not data sync (each device is its own island in demo mode).

---

## 🗄 7. Technical Summary (for the record)

- **Language:** Kotlin; **UI:** XML ViewBinding + Material 3 components + RecyclerView; **no Jetpack Compose**.
- **DI:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`).
- **Storage:** Room DB `grt_exam_hall.db` (students, teachers, halls, exams, arrangements); credentials in EncryptedSharedPreferences.
- **Async:** Kotlin coroutines + StateFlow; `UiState` sealed classes for Loading/Success/Error/Empty.
- **Auth:** `AuthRepositoryImplSelector` picks Firebase when configured, else `DemoAuthRepository` — demo-first by design.
- **PDF:** iText 7 (core), landscape A4, staged via cache then copied into MediaStore (scoped-storage safe).
- **Tests:** `ArrangementGeneratorTest.kt` — 20 tests covering batch integrity, capacity, phase rules, HOD/coordinator constraints, balancing, CSV parsing.
- **Launcher icon:** generated from the college logo (`grt_logo.jpg`) into all densities via `tools/generate_app_icons.py` (Python + Pillow). Adaptive icon (Android 8+) uses the logo foreground on the brand navy background; Android 7 devices get the legacy PNG icons.

*Report generated by analyzing all 45 Kotlin source files, 20 layouts, navigation graph, and resources on 2026-09-10.*
