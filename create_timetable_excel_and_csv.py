import os
import csv
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.drawing.image import Image as OpenpyxlImage
from PIL import Image as PILImage

PROJECT_DIR = r"c:\Users\Public\ExamHallAllocation"

# 1. Dataset from the Official PDF
header_lines = [
    "GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.",
    "(An Autonomous Institution)",
    "Approved by AICTE, New Delhi & Affiliated to Anna University, Chennai.",
    "Accredited by NAAC with 'A++' Grade & An ISO 9001:2015 Certified Institution",
    "GRT Mahalakshmi Nagar, Chennai - Tirupathi Highway, Tiruttani - 631 209, Tiruvallur District.",
    "NOV / DEC 2026 - Assessment Test - I (17/08/2026 - 25/08/2026)",
    "DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING - II/III/IV YEAR / 03/05/07 Semester"
]

columns = [
    "S.No",
    "Date of Exam",
    "Session",
    "Timing",
    "Year / Sem",
    "Department",
    "Subject Code",
    "Subject Name",
    "Students Count"
]

data_rows = [
    [1,  "17-08-2026 (Monday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "CS24301", "DATA STRUCTURES AND ALGORITHMS", 104],
    [2,  "18-08-2026 (Tuesday)",   "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "MA24303", "DISCRETE MATHEMATICS", 104],
    [3,  "19-08-2026 (Wednesday)", "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "EC24303", "COMPUTER ORGANIZATION AND DIGITAL PR", 104],
    [4,  "20-08-2026 (Thursday)",  "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "CS24302", "PROGRAMMING IN JAVA", 104],
    [5,  "21-08-2026 (Friday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "CS24303", "FOUNDATION OF DATASCIENCE", 104],
    [6,  "22-08-2026 (Saturday)",  "FN", "8:40 a.m. TO 10:10 a.m.", "II / 03",  "CSE", "CS24304", "OPERATING SYSTEMS", 104],
    [7,  "17-08-2026 (Monday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "CS24502", "CLOUD COMPUTING", 119],
    [8,  "18-08-2026 (Tuesday)",   "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "CS24503", "MOBILE APPLICATION DEVELOPMENT", 119],
    [9,  "19-08-2026 (Wednesday)", "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "CS24P01", "EXPLORATORY DATA ANALYSIS", 119],
    [10, "20-08-2026 (Thursday)",  "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "CS24P06", "UI & UX DESIGN", 119],
    [11, "21-08-2026 (Friday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "GE24501", "PROFESSIONAL ETHICS AND HUMAN VALUES", 119],
    [12, "22-08-2026 (Saturday)",  "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "MG24903", "BUISNESS STRAGEGY", 119],
    [13, "24-08-2026 (Monday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "III / 05", "CSE", "CS24501", "OBJECT ORIENTED SOFTWARE ENGINEERING", 119],
    [14, "17-08-2026 (Monday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "IV / 07",  "CSE", "AI3021",  "IT IN AGRICULTURAL SYSTEM", 117],
    [15, "18-08-2026 (Tuesday)",   "FN", "8:40 a.m. TO 10:10 a.m.", "IV / 07",  "CSE", "OBT356",  "LIFESTYLE DISEASES", 117],
    [16, "19-08-2026 (Wednesday)", "FN", "8:40 a.m. TO 10:10 a.m.", "IV / 07",  "CSE", "GE3791",  "HUMAN VALUES AND ETHICS", 117],
    [17, "20-08-2026 (Thursday)",  "FN", "8:40 a.m. TO 10:10 a.m.", "IV / 07",  "CSE", "GE3751",  "PRINCIPLES OF MANAGEMENT", 117],
    [18, "21-08-2026 (Friday)",    "FN", "8:40 a.m. TO 10:10 a.m.", "IV / 07",  "CSE", "OIM351",  "INDUSTRIAL MANAGEMENT", 117],
]

# =========================================================================
# 2. CREATE CLEAN DIGITAL CSV
# =========================================================================
csv_filename = "GRT_Assessment_Test_I_Timetable.csv"
csv_filepath = os.path.join(PROJECT_DIR, csv_filename)

with open(csv_filepath, mode="w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    for line in header_lines:
        writer.writerow([line] + [""] * 8)
    writer.writerow(columns)
    for row in data_rows:
        writer.writerow(row)
    writer.writerow([])
    writer.writerow(["EXAM CELL COORDINATOR", "", "", "HOD / CSE", "", "", "", "PRINCIPAL", ""])

print(f"[CSV Created] -> {csv_filepath}")

# =========================================================================
# 3. CREATE BEAUTIFUL DIGITAL EXCEL SPREADSHEET (.XLSX)
# =========================================================================
excel_filename = "GRT_Assessment_Test_I_Timetable.xlsx"
excel_filepath = os.path.join(PROJECT_DIR, excel_filename)

wb = Workbook()
ws = wb.active
ws.title = "Assessment Test - I"
ws.views.sheetView[0].showGridLines = True

# Style presets
font_family = "Segoe UI"
font_title = Font(name=font_family, size=13, bold=True, color="1E3A8A")
font_sub = Font(name=font_family, size=10, bold=True, color="334155")
font_affil = Font(name=font_family, size=9, italic=True, color="475569")
font_naac = Font(name=font_family, size=9, bold=True, color="047857")
font_addr = Font(name=font_family, size=8.5, color="64748B")

font_exam_bar = Font(name=font_family, size=11, bold=True, color="FFFFFF")
fill_exam_bar = PatternFill(start_color="1E3A8A", end_color="1E3A8A", fill_type="solid")

font_dept_bar = Font(name=font_family, size=10, bold=True, color="0F172A")
fill_dept_bar = PatternFill(start_color="E2E8F0", end_color="E2E8F0", fill_type="solid")

font_th = Font(name=font_family, size=10, bold=True, color="FFFFFF")
fill_th = PatternFill(start_color="2563EB", end_color="2563EB", fill_type="solid")

font_td = Font(name=font_family, size=9.5, color="1E293B")
font_code = Font(name=font_family, size=9.5, bold=True, color="0F172A")
font_total = Font(name=font_family, size=10, bold=True, color="0F172A")

fill_even = PatternFill(start_color="F8FAFC", end_color="F8FAFC", fill_type="solid")
fill_total = PatternFill(start_color="EEF2F6", end_color="EEF2F6", fill_type="solid")

thin_side = Side(border_style="thin", color="CBD5E1")
cell_border = Border(left=thin_side, right=thin_side, top=thin_side, bottom=thin_side)
double_bottom = Border(left=thin_side, right=thin_side, top=thin_side, bottom=Side(border_style="double", color="1E3A8A"))

# Prepare Logos for Placement
grt_logo_path = os.path.join(PROJECT_DIR, "logo_grt_clean.png")
tuv_logo_path = os.path.join(PROJECT_DIR, "logo_tuv_clean.png")

# Set Row Heights for Banner (Rows 1 to 7)
ws.row_dimensions[1].height = 24
ws.row_dimensions[2].height = 18
ws.row_dimensions[3].height = 18
ws.row_dimensions[4].height = 18
ws.row_dimensions[5].height = 16
ws.row_dimensions[6].height = 24
ws.row_dimensions[7].height = 22

# Merge columns B to H for institutional text so logos can sit on A and I
ws.merge_cells("B1:H1")
ws["B1"] = header_lines[0]
ws["B1"].font = font_title
ws["B1"].alignment = Alignment(horizontal="center", vertical="center")

ws.merge_cells("B2:H2")
ws["B2"] = header_lines[1]
ws["B2"].font = font_sub
ws["B2"].alignment = Alignment(horizontal="center", vertical="center")

ws.merge_cells("B3:H3")
ws["B3"] = header_lines[2]
ws["B3"].font = font_affil
ws["B3"].alignment = Alignment(horizontal="center", vertical="center")

ws.merge_cells("B4:H4")
ws["B4"] = header_lines[3]
ws["B4"].font = font_naac
ws["B4"].alignment = Alignment(horizontal="center", vertical="center")

ws.merge_cells("B5:H5")
ws["B5"] = header_lines[4]
ws["B5"].font = font_addr
ws["B5"].alignment = Alignment(horizontal="center", vertical="center")

# Insert GRT Logo on top-left (Col A)
if os.path.exists(grt_logo_path):
    with PILImage.open(grt_logo_path) as im:
        w, h = im.size
        # scale to ~60px high
        target_h = 65
        target_w = int(w * (target_h / h))
        grt_scaled_path = os.path.join(PROJECT_DIR, "logo_grt_excel.png")
        im.resize((target_w, target_h), PILImage.Resampling.LANCZOS).save(grt_scaled_path)
    img_grt = OpenpyxlImage(grt_scaled_path)
    img_grt.anchor = "A1"
    ws.add_image(img_grt)

# Insert TUV Logo on top-right (Col I)
if os.path.exists(tuv_logo_path):
    with PILImage.open(tuv_logo_path) as im:
        w, h = im.size
        target_h = 60
        target_w = int(w * (target_h / h))
        tuv_scaled_path = os.path.join(PROJECT_DIR, "logo_tuv_excel.png")
        im.resize((target_w, target_h), PILImage.Resampling.LANCZOS).save(tuv_scaled_path)
    img_tuv = OpenpyxlImage(tuv_scaled_path)
    img_tuv.anchor = "I1"
    ws.add_image(img_tuv)

# Row 6: Assessment Test Banner (Spanning A6 to I6)
ws.merge_cells("A6:I6")
ws["A6"] = header_lines[5]
ws["A6"].font = font_exam_bar
ws["A6"].alignment = Alignment(horizontal="center", vertical="center")
for col in range(1, 10):
    c = ws.cell(row=6, column=col)
    c.fill = fill_exam_bar
    c.border = cell_border

# Row 7: Department & Semester Banner (Spanning A7 to I7)
ws.merge_cells("A7:I7")
ws["A7"] = header_lines[6]
ws["A7"].font = font_dept_bar
ws["A7"].alignment = Alignment(horizontal="center", vertical="center")
for col in range(1, 10):
    c = ws.cell(row=7, column=col)
    c.fill = fill_dept_bar
    c.border = cell_border

# Row 8: Table Header (A8 to I8)
ws.row_dimensions[8].height = 26
for col_idx, col_name in enumerate(columns, start=1):
    c = ws.cell(row=8, column=col_idx, value=col_name)
    c.font = font_th
    c.fill = fill_th
    c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
    c.border = cell_border

# Rows 9 to 26: 18 Data Rows
for idx, row_vals in enumerate(data_rows):
    curr_row = 9 + idx
    ws.row_dimensions[curr_row].height = 22
    is_even = (idx % 2 == 1)
    fill_choice = fill_even if is_even else None

    for col_idx, val in enumerate(row_vals, start=1):
        cell = ws.cell(row=curr_row, column=col_idx, value=val)
        cell.border = cell_border
        if fill_choice:
            cell.fill = fill_choice

        # Alignment
        if col_idx in (1, 2, 3, 4, 5, 6):
            cell.alignment = Alignment(horizontal="center", vertical="center")
            cell.font = font_td
        elif col_idx == 7:  # Subject Code
            cell.alignment = Alignment(horizontal="center", vertical="center")
            cell.font = font_code
        elif col_idx == 8:  # Subject Name
            cell.alignment = Alignment(horizontal="left", vertical="center")
            cell.font = font_td
        elif col_idx == 9:  # Student Count
            cell.alignment = Alignment(horizontal="center", vertical="center")
            cell.font = font_code

# Row 27: Summary Row (Total Allocations)
ws.row_dimensions[27].height = 24
ws.merge_cells("A27:H27")
ws["A27"] = "TOTAL SCHEDULED PAPERS: 18 SUBJECTS | TOTAL CANDIDATE APPEARANCES"
ws["A27"].font = font_total
ws["A27"].alignment = Alignment(horizontal="right", vertical="center")

ws["I27"] = "=SUM(I9:I26)"
ws["I27"].font = font_total
ws["I27"].alignment = Alignment(horizontal="center", vertical="center")

for col in range(1, 10):
    c = ws.cell(row=27, column=col)
    c.fill = fill_total
    c.border = double_bottom

# Blank row 28
ws.row_dimensions[28].height = 14

# Signature Rows (29 & 30)
ws.row_dimensions[29].height = 32
ws.row_dimensions[30].height = 18

font_sig = Font(name=font_family, size=10, bold=True, color="0F172A")
font_sub_sig = Font(name=font_family, size=9, italic=True, color="64748B")

ws.cell(row=29, column=2, value="EXAM CELL COORDINATOR").font = font_sig
ws.cell(row=29, column=2).alignment = Alignment(horizontal="center", vertical="center")
ws.cell(row=30, column=2, value="Date: 13/08/2026").font = font_sub_sig
ws.cell(row=30, column=2).alignment = Alignment(horizontal="center", vertical="center")

ws.cell(row=29, column=5, value="HOD / CSE").font = font_sig
ws.cell(row=29, column=5).alignment = Alignment(horizontal="center", vertical="center")
ws.cell(row=30, column=5, value="Date: 13/08/2026").font = font_sub_sig
ws.cell(row=30, column=5).alignment = Alignment(horizontal="center", vertical="center")

ws.cell(row=29, column=8, value="PRINCIPAL").font = font_sig
ws.cell(row=29, column=8).alignment = Alignment(horizontal="center", vertical="center")
ws.cell(row=30, column=8, value="GRT IET, Tiruttani").font = font_sub_sig
ws.cell(row=30, column=8).alignment = Alignment(horizontal="center", vertical="center")

# Column Widths optimized for readability
column_widths = {
    "A": 8,   # S.No
    "B": 26,  # Date of Exam
    "C": 10,  # Session
    "D": 26,  # Timing
    "E": 14,  # Year / Sem
    "F": 14,  # Department
    "G": 16,  # Subject Code
    "H": 46,  # Subject Name
    "I": 16,  # Students Count
}

for col_letter, width in column_widths.items():
    ws.column_dimensions[col_letter].width = width

wb.save(excel_filepath)
print(f"[Excel Created] -> {excel_filepath}")
