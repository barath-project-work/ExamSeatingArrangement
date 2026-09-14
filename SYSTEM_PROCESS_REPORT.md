# 📋 Exam Hall Allocation System — Comprehensive Architecture & Feature Process Report

This document provides an exhaustive, clear, and transparent analysis of the entire **ExamHallAllocation** codebase. It outlines how data flows, how documents are imported and parsed, what columns are required, how PDF exports are constructed, what the application currently does, what it cannot do, and potential features ready to be trimmed or refined.

---

## 1. 📥 How Document Import Works

The application features an in-house **Smart Data Extractor** (`SmartDataExtractor.kt`) designed to read files directly on Android without third-party desktop bloat.

### Supported File Formats:
1. **Excel Workbooks (`.xlsx`)**: Native OpenXML parsing (decompresses zip packages and reads XML sheets directly in memory).
2. **Delimited Text (`.csv`, `.tsv`)**: Dynamic delimiter detection (automatically senses commas, semicolons, or tabs) with quoted-string escaping.
3. **Clipboard / Plain Text**: Raw text pasted directly into the import dialog.

### How the Parser Analyzes Files:
1. **Letterhead & Banner Immunity**:
   - The parser inspects the first 15 rows of every sheet.
   - It distinguishes between institution banners (e.g., college name, address, autonomous status) and real table headers by counting non-blank cells and matching keyword pairs.
2. **Fuzzy Header Mapping**:
   - Header column names do not need to match rigid spelling. The parser normalizes all names (lowercasing, stripping spaces, punctuation) and matches via fuzzy keyword heuristics.
3. **Fallback Positional Parsing**:
   - If a file contains no header row at all, the parser falls back to positional column patterns (e.g., Col 0 = S.No, Col 1 = Register Number, Col 2 = Name).
4. **Automatic Year & Section Deduction**:
   - If the student's academic year is not explicitly written in a column, the parser inspects:
     - The Excel sheet tab title (e.g., `"III-CSE"`, `"II yr"`, `"IV Year"`).
     - The Anna University / GRT Register Number prefix (e.g., `110323...` → 3rd Year; `110324...` → 2nd Year; `110322...` → 4th Year).
     - The active tab currently open on the screen.
5. **Automatic Inactive / TC Student Dropping**:
   - Any row containing remarks such as *"Got TC"*, *"Debarred"*, or *"Discontinued"* is automatically excluded from seating generation so they never occupy exam hall capacity.

---

## 2. 📊 Required Columns for Importing Inside the App

### A. Student Roster Import (`parseStudents`)
| Column Type | Accepted Header Names (Fuzzy) | Example Value | Mandatory? | Fallback if Missing |
| :--- | :--- | :--- | :--- | :--- |
| **Register Number** | `reg`, `roll`, `usn`, `htno`, `register`, `candidate` | `110323104045` | **YES** | Scanned by digit pattern |
| **Student Name** | `name`, `student`, `candidate`, `student name` | `JANANI G` | **YES** | `"Student <RegNo>"` |
| **Academic Year** | `year`, `yr`, `batch`, `class`, `sem` | `III`, `3`, `3rd` | No | Inferred from Reg No / Sheet name |
| **Section** | `sec`, `section` | `A`, `B` | No | Defaults to `"A"` |
| **S.No / Roll No** | `pos`, `position`, `sno`, `s.no`, `slno`, `rank` | `45` | No | Extracted from last 3 digits of Reg No |
| **Remarks / TC** | `remark`, `status`, `note`, `tc` | `Promoted` / `TC` | No | Defaults to Active |

---

### B. Exam Timetable Import (`parseExams`)
| Column Type | Accepted Header Names (Fuzzy) | Example Value | Mandatory? | Fallback if Missing |
| :--- | :--- | :--- | :--- | :--- |
| **Date of Exam** | `date`, `day`, `schedule` | `17-08-2026` or `17/08/2026` | **YES** | Normalizes multiple date formats |
| **Session** | `session`, `slot`, `sessio` | `FN` or `AN` | No | Defaults to `"FN"` |
| **Timing** | `timing`, `time`, `hour` | `8:40 a.m. TO 10:10 a.m.` | No | Defaults to `8:40 a.m. TO 10:10 a.m.` |
| **Year / Sem** | `yearsem`, `year/sem`, `yr/sem`, `class`, `batch` | `II / 03`, `III / 05`, `IV / 07` | **YES** | Splits year & semester |
| **Department** | `dept`, `department`, `branch` | `CSE` | No | Defaults to `"CSE"` |
| **Subject Code** | `subcode`, `subjectcode`, `code`, `paper` | `CS24301` | **YES** | Regex pattern matching |
| **Subject Name** | `subname`, `subjectname`, `subject`, `name`, `course` | `DATA STRUCTURES AND ALGORITHMS` | **YES** | Uses Subject Code if missing |
| **Student Count** | `count`, `studentscount`, `studentcount`, `strength` | `104` | No | Defaults to `0` |

---

### C. Physical Halls Import (`parseHalls`)
| Column Type | Accepted Header Names (Fuzzy) | Example Value | Mandatory? |
| :--- | :--- | :--- | :--- |
| **Hall / Room No** | `room`, `hall`, `roomnumber`, `hallno` | `A212`, `B215` | **YES** |
| **Block** | `block`, `building` | `A-Block`, `Main` | No (defaults to `"Main"`) |
| **Floor** | `floor`, `level` | `2`, `Ground` | No (defaults to `1`) |
| **Capacity** | `capacity`, `seats`, `intake` | `30` | No (defaults to `30`) |

---

### D. Faculty / Teachers Import (`parseTeachers`)
| Column Type | Accepted Header Names (Fuzzy) | Example Value | Mandatory? |
| :--- | :--- | :--- | :--- |
| **Faculty Name** | `name`, `faculty`, `teacher`, `staff` | `Dr. K. Ramesh` | **YES** |
| **Username** | `user`, `username`, `email`, `staffid` | `ramesh` | **YES** |
| **Password** | `pass`, `password`, `pwd` | `Welcome@123` | No (defaults to username) |
| **Designation / Role** | `role`, `designation`, `post`, `dept` | `Assistant Professor` / `HOD` | No (defaults to Normal Teacher) |

---

## 3. 📄 Exported Documents & Exact Column Specifications

The application uses Android's native graphics subsystem (`android.graphics.pdf.PdfDocument`) to generate vector PDFs that do not crash or consume excessive memory.

### 1. Master Examination Seating Plan PDF (`PdfGenerator.generate`)
* **Orientation**: **A4 Landscape** (`842 x 595` pt)
* **Header**: College Logo, Institutional Name, Autonomous & NAAC A++ banner, Examination Period.
* **Total Columns**: **9 Columns**
  1. `S.No`: Sequential room index.
  2. `Hall No`: Physical classroom number (e.g. `A212`).
  3. `Floor`: Floor number (e.g. `2`).
  4. `Dept`: Department (`CSE`).
  5. `Year / Sem`: Year and semester label (e.g. `2nd Year / Sem 3`).
  6. `Reg No From`: Starting Register Number of the seated batch (e.g. `110324104001`).
  7. `Reg No To`: Ending Register Number of the seated batch (e.g. `110324104015`).
  8. `Count`: Number of students in that specific batch (normally `15`).
  9. `Total`: Total students assigned to the entire room (normally `30`).
* **Footer**: Signature lines for **Exam Cell Coordinator**, **HOD / CSE**, and **Principal**.

---

### 2. Student Directory PDF Export (`PdfGenerator.generateStudentListPdf`)
* **Orientation**: **A4 Portrait** (`595 x 842` pt)
* **Header**: Institutional banner with full accreditation details.
* **Total Columns**: **5 Columns**
  1. `S.NO`: Sequential roll number (ordered 1 to 120 per year).
  2. `REGISTER NO`: Official University Register Number.
  3. `STUDENT NAME`: Full Name.
  4. `YR / SEC`: Year and Section (e.g. `2n - A`).
  5. `STATUS`: Promoted / Active indicator.

---

### 3. Student Directory CSV Export (`PdfGenerator.generateStudentListCsv`)
* **First 7 Lines**: Institutional Letterhead text.
* **Header Row**:
  `S.NO, REGISTER NO, STUDENT NAME, YEAR, SECTION, STATUS` (**6 Columns**)
* **Data Rows**: Sorted strictly by:
  - 1st Year (Roll 1..120)
  - 2nd Year (Roll 1..120)
  - 3rd Year (Roll 1..120)
  - 4th Year (Roll 1..120)

---

## 4. 🚀 What the Application CAN Do Right Now

1. **Dual Cloud & Offline Architecture**:
   - Writes to local SQLite (Room) for offline reliability.
   - Pushes to Google Cloud Firestore so data syncs across multiple mobile devices.
2. **Automated Cross-Device Sync**:
   - Pulls cloud data automatically upon login.
   - Background sync when the Admin Dashboard opens.
   - Dedicated "Firebase Cloud Database" sync button on the dashboard with real-time feedback.
3. **Student Directory Operations**:
   - Add, edit, search, and delete students.
   - Tabbed filtering across all 4 academic years.
   - Single-click export of sorted rosters to PDF and CSV.
4. **Exam Timetable Portal**:
   - Add, edit, delete, and search exam schedules.
   - Upload official college exam timetable spreadsheets (ignoring letterhead lines).
   - Preloaded official "Assessment Test - I" (18 subjects across II, III, IV year).
5. **Physical Hall Management**:
   - Manage physical rooms (`A212`, `B215`, etc.), capacities, and floor levels.
6. **Teacher & Faculty Directory**:
   - Track teacher accounts, HOD designations, and assigned invigilation duties.
7. **Seating Allocation Engine**:
   - Pairs students from different academic years on the same bench (Left seat vs. Right seat).
   - Prevents students of the same year/subject from sitting next to each other.
   - Assigns invigilators automatically while excluding HODs and balancing workload.
8. **Interactive Seating Preview**:
   - Inspect individual rooms, examine student batches, and swap invigilators between rooms.
   - Toggle status between `Draft` and `Published`.

---

## 5. ⚠️ What the Application CANNOT Do Right Now

1. **Dynamic Seating Layouts (Rigid 15x2 Bench Assumption)**:
   - The engine strictly assumes 30-seater classrooms (15 benches x 2 students).
   - It cannot currently configure custom seating layouts (e.g. 1 student per bench, 3 per desk, or large 60+ seat halls).
2. **Individual Desk Matrix Grid in PDF**:
   - The master export prints summary ranges (`From Reg No` to `To Reg No`) rather than an individual desk-by-desk seating layout diagram (Desk 1: Seat A / Seat B).
3. **Multi-Department Cross-Mixing**:
   - The engine currently mixes years within CSE (`II`, `III`, `IV`). It does not automatically cross-mix students from ECE, Mechanical, or IT into the same room.
4. **Dynamic Date-Driven Subject Pairing**:
   - The engine uses a predefined Phase concept (`Phase 1: II+III+IV`, `Phase 2: II+III`, `Phase 3: III`) instead of dynamically reading whatever subjects happen to be on the timetable for that specific date.
5. **Student Photos & QR/Barcodes**:
   - There is no student photo capture or barcode scanning on seating slips.

---

## 6. ✂️ Identified Candidate Features for Removal / Simplification

Based on user feedback that *"some features are not even needed"*, here are elements that may be adding unnecessary complexity:

| Feature / UI Element | Current Purpose | Why It Might Be Unneeded |
| :--- | :--- | :--- |
| **Teacher Invigilation Swapping** | Allows dragging/swapping teachers between halls in Preview | If exam cell manually allocates teachers or prints the plan directly, this interactive swap adds UI clutter. |
| **Separate Phase 1 / Phase 2 / Phase 3 Tabs** | Configures which years are eligible for allocation | Confusing to admins. The app should simply look at the selected exam date and automatically allocate whatever years have exams on that day. |
| **Wipe Mock Data Button** | Deletes local database rows | Dangerous in production if tapped accidentally; only useful during initial development. |
| **Offline Demo Mode vs Firebase Mode** | Allows logging in without internet | If the college operates online via Firebase, demo mode creates confusion and dual code paths. |
| **Teacher Portal Screen** | Allows faculty to log in and see their duties | If hall allocations are distributed via printed PDF or notice boards, the teacher login interface is redundant. |
| **Notice Board vs Stat Cards redundancy** | Shows duplicate counters on dashboard | Can be consolidated into a cleaner single-page dashboard. |

---

*This report is stored at [`SYSTEM_PROCESS_REPORT.md`](file:///c:/Users/Public/ExamHallAllocation/SYSTEM_PROCESS_REPORT.md) in your project directory.*
