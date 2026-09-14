package com.example.examhallallocation.data.seed

import com.example.examhallallocation.domain.model.*

/**
 * Deterministic sample data for demo mode. Sample data is clearly separated from
 * production data: replace [SeedDataProvider] with a real importer or sync source.
 *
 * The student lists intentionally contain missing positions (debarred/discontinued
 * students) so that generated arrangements demonstrate real-world occupancy below 30.
 */
object SeedDataProvider {

    const val DEMO_ADMIN_USERNAME = "admin"
    const val DEMO_ADMIN_PASSWORD = "admin@123"
    const val DEMO_TEACHER_USERNAME = "rkumar"
    const val DEMO_TEACHER_PASSWORD = "teacher123"

    // Exam window: 7 consecutive days starting 2026-04-13 (Monday).
    val examDates: List<String> = (0L..6L).map { offset ->
        java.time.LocalDate.of(2026, 4, 13).plusDays(offset).toString()
    }

    fun students(): List<Student> = buildList {
        addAll(year2Students())
        addAll(year3Students())
        addAll(year4Students())
    }

    fun teachers(): List<Teacher> = listOf(
        Teacher("tea_hod", "Dr. A. Ramesh", "hod", UserRole.HOD, active = true),
        Teacher("tea_coord", "S. Kalaiselvi", "coordinator", UserRole.EXAM_CELL_COORDINATOR, active = true),
        Teacher("tea_admin", "Exam Cell Admin", DEMO_ADMIN_USERNAME, UserRole.ADMIN, active = true),
        Teacher("tea_rkumar", "R. Kumar", "rkumar", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_priya", "P. Priya", "priya", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_anand", "A. Anand", "anand", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_meena", "M. Meena", "meena", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_suresh", "S. Suresh", "suresh", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_latha", "L. Latha", "latha", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_vikram", "V. Vikram", "vikram", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_divya", "D. Divya", "divya", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_naveen", "N. Naveen", "naveen", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_kavya", "K. Kavya", "kavya", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_arun", "A. Arun", "arun", UserRole.NORMAL_TEACHER, active = true),
        Teacher("tea_rekha", "R. Rekha", "rekha", UserRole.NORMAL_TEACHER, active = true),
    )

    fun halls(): List<Hall> = listOf(
        Hall("hall_a212", "A212", "A", 2, 30),
        Hall("hall_a213", "A213", "A", 2, 30),
        Hall("hall_a214", "A214", "A", 2, 30),
        Hall("hall_a306", "A306", "A", 3, 30),
        Hall("hall_a307", "A307", "A", 3, 30),
        Hall("hall_a308", "A308", "A", 3, 30),
        Hall("hall_b205", "B205", "B", 2, 30),
        Hall("hall_b206", "B206", "B", 2, 30),
        Hall("hall_b207", "B207", "B", 2, 30),
        Hall("hall_b213", "B213", "B", 2, 30),
        Hall("hall_b214", "B214", "B", 2, 30),
        Hall("hall_b215", "B215", "B", 2, 30),
    )

    val examName = "Assessment Test - I (NOV / DEC 2026)"

    /**
     * Official curriculum subjects catalog across II, III, and IV Year CSE.
     * Pure subjects without exam dates.
     */
    fun subjects(): List<Subject> = listOf(
        // II Year / Semester 3
        Subject("sub_cs24301_2", "CS24301", "DATA STRUCTURES AND ALGORITHMS", StudentYear.YEAR_2, 3, "CSE"),
        Subject("sub_ma24303_2", "MA24303", "DISCRETE MATHEMATICS", StudentYear.YEAR_2, 3, "CSE"),
        Subject("sub_ec24303_2", "EC24303", "COMPUTER ORGANIZATION AND DIGITAL PR", StudentYear.YEAR_2, 3, "CSE"),
        Subject("sub_cs24302_2", "CS24302", "PROGRAMMING IN JAVA", StudentYear.YEAR_2, 3, "CSE"),
        Subject("sub_cs24303_2", "CS24303", "FOUNDATION OF DATASCIENCE", StudentYear.YEAR_2, 3, "CSE"),
        Subject("sub_cs24304_2", "CS24304", "OPERATING SYSTEMS", StudentYear.YEAR_2, 3, "CSE"),

        // III Year / Semester 5
        Subject("sub_cs24502_3", "CS24502", "CLOUD COMPUTING", StudentYear.YEAR_3, 5, "CSE"),
        Subject("sub_cs24501_3", "CS24501", "INTERNET PROGRAMMING", StudentYear.YEAR_3, 5, "CSE"),
        Subject("sub_cs24503_3", "CS24503", "ARTIFICIAL INTELLIGENCE AND MACHINE LEARNING", StudentYear.YEAR_3, 5, "CSE"),
        Subject("sub_cs24504_3", "CS24504", "DISTRIBUTED SYSTEMS", StudentYear.YEAR_3, 5, "CSE"),
        Subject("sub_cs24505_3", "CS24505", "CYBER SECURITY", StudentYear.YEAR_3, 5, "CSE"),
        Subject("sub_cs24506_3", "CS24506", "COMPILER DESIGN", StudentYear.YEAR_3, 5, "CSE"),

        // IV Year / Semester 7
        Subject("sub_ai3021_4", "AI3021", "IT IN AGRICULTURAL SYSTEM", StudentYear.YEAR_4, 7, "CSE"),
        Subject("sub_cs3002_4", "CS3002", "OBJECT ORIENTED ANALYSIS AND DESIGN", StudentYear.YEAR_4, 7, "CSE"),
        Subject("sub_cs3701_4", "CS3701", "BLOCKCHAIN TECHNOLOGIES", StudentYear.YEAR_4, 7, "CSE"),
        Subject("sub_cs3702_4", "CS3702", "BIG DATA ANALYTICS", StudentYear.YEAR_4, 7, "CSE"),
        Subject("sub_cs3703_4", "CS3703", "INTERNET OF THINGS", StudentYear.YEAR_4, 7, "CSE"),
        Subject("sub_cs3704_4", "CS3704", "SOFTWARE TESTING", StudentYear.YEAR_4, 7, "CSE"),
    )

    /**
     * Official examination timetable matching GRT CSE Department Assessment Test - I.
     * Aligned with all 18 subjects across II, III, and IV year from college records.
     */
    fun exams(): List<Exam> = listOf(
        // II Year / 03 Semester
        Exam(
            id = "exam_20260817_2",
            examName = examName,
            date = "2026-08-17",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "CS24301",
            subjectName = "DATA STRUCTURES AND ALGORITHMS",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),
        Exam(
            id = "exam_20260818_2",
            examName = examName,
            date = "2026-08-18",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "MA24303",
            subjectName = "DISCRETE MATHEMATICS",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),
        Exam(
            id = "exam_20260819_2",
            examName = examName,
            date = "2026-08-19",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "EC24303",
            subjectName = "COMPUTER ORGANIZATION AND DIGITAL PR",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),
        Exam(
            id = "exam_20260820_2",
            examName = examName,
            date = "2026-08-20",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "CS24302",
            subjectName = "PROGRAMMING IN JAVA",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),
        Exam(
            id = "exam_20260821_2",
            examName = examName,
            date = "2026-08-21",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "CS24303",
            subjectName = "FOUNDATION OF DATASCIENCE",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),
        Exam(
            id = "exam_20260822_2",
            examName = examName,
            date = "2026-08-22",
            year = StudentYear.YEAR_2,
            semester = 3,
            subjectCode = "CS24304",
            subjectName = "OPERATING SYSTEMS",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 104,
        ),

        // III Year / 05 Semester
        Exam(
            id = "exam_20260817_3",
            examName = examName,
            date = "2026-08-17",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "CS24502",
            subjectName = "CLOUD COMPUTING",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260818_3",
            examName = examName,
            date = "2026-08-18",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "CS24503",
            subjectName = "MOBILE APPLICATION DEVELOPMENT",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260819_3",
            examName = examName,
            date = "2026-08-19",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "CS24P01",
            subjectName = "EXPLORATORY DATA ANALYSIS",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260820_3",
            examName = examName,
            date = "2026-08-20",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "CS24P06",
            subjectName = "UI & UX DESIGN",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260821_3",
            examName = examName,
            date = "2026-08-21",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "GE24501",
            subjectName = "PROFESSIONAL ETHICS AND HUMAN VALUES",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260822_3",
            examName = examName,
            date = "2026-08-22",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "MG24903",
            subjectName = "BUISNESS STRAGEGY",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),
        Exam(
            id = "exam_20260824_3",
            examName = examName,
            date = "2026-08-24",
            year = StudentYear.YEAR_3,
            semester = 5,
            subjectCode = "CS24501",
            subjectName = "OBJECT ORIENTED SOFTWARE ENGINEERING",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 119,
        ),

        // IV Year / 07 Semester
        Exam(
            id = "exam_20260817_4",
            examName = examName,
            date = "2026-08-17",
            year = StudentYear.YEAR_4,
            semester = 7,
            subjectCode = "AI3021",
            subjectName = "IT IN AGRICULTURAL SYSTEM",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 117,
        ),
        Exam(
            id = "exam_20260818_4",
            examName = examName,
            date = "2026-08-18",
            year = StudentYear.YEAR_4,
            semester = 7,
            subjectCode = "OBT356",
            subjectName = "LIFESTYLE DISEASES",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 117,
        ),
        Exam(
            id = "exam_20260819_4",
            examName = examName,
            date = "2026-08-19",
            year = StudentYear.YEAR_4,
            semester = 7,
            subjectCode = "GE3791",
            subjectName = "HUMAN VALUES AND ETHICS",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 117,
        ),
        Exam(
            id = "exam_20260820_4",
            examName = examName,
            date = "2026-08-20",
            year = StudentYear.YEAR_4,
            semester = 7,
            subjectCode = "GE3751",
            subjectName = "PRINCIPLES OF MANAGEMENT",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 117,
        ),
        Exam(
            id = "exam_20260821_4",
            examName = examName,
            date = "2026-08-21",
            year = StudentYear.YEAR_4,
            semester = 7,
            subjectCode = "OIM351",
            subjectName = "INDUSTRIAL MANAGEMENT",
            session = "FN",
            timing = "8:40 a.m. TO 10:10 a.m.",
            department = "CSE",
            studentCount = 117,
        ),
    )

    // ------------------------------------------------------------------
    // Students (Actual 12-digit GRT Roll Numbers: 1103 + YY + 104 + XXX)
    // ------------------------------------------------------------------

    private val commonStudentNames = listOf(
        "Aravind Kumar K", "Abinaya S", "Ajith Kumar M", "Anand R", "Bhavani P",
        "Deepak Raj V", "Dhivya M", "Gokul Nath S", "Hariharan K", "Hemalatha R",
        "Jeevitha M", "Karthik Raja P", "Keerthana V", "Kishore Kumar S", "Lavanya N",
        "Manikandan R", "Monisha K", "Naveen Kumar A", "Nithya Shree S", "Pavithra M",
        "Praveen Raj V", "Priyadharshini R", "Rahul S", "Ramya K", "Santhosh Kumar M",
        "Saravanan P", "Shalini V", "Sneha R", "Srinath K", "Surya Prakash M",
        "Swetha S", "Tamilarasan V", "Tharani R", "Vignesh Kumar K", "Yuvaraj M"
    )

    private fun year2Students(): List<Student> {
        // II Year (Batch 25 / Sem 3): 110325104001 to 110325104120.
        // Roll numbers 31 and 79 are gaps as seen in the GRT allocation document.
        val missing = setOf(31, 79)
        return (1..120).filter { it !in missing }.map { index ->
            val name = if (index == 107) "Subash R" else studentName(2, index)
            student("110325104", index, index, StudentYear.YEAR_2, 3, name)
        }
    }

    private fun year3Students(): List<Student> {
        // III Year (Batch 24 / Sem 5): 110324104001 to 110324104120 + Lateral 110324104301.
        val missing = setOf(12, 17, 44, 76)
        val regular = (1..120).filter { it !in missing }.map { index ->
            val name = if (index == 60) "Lokesh S" else studentName(3, index)
            student("110324104", index, index, StudentYear.YEAR_3, 5, name)
        }
        val lateral = Student(
            id = "stu_110324104301",
            registerNumber = "110324104301",
            name = "Vigneshwaran M",
            year = StudentYear.YEAR_3,
            section = "B",
            position = 121,
            active = true,
        )
        return regular + lateral
    }

    private fun year4Students(): List<Student> {
        // IV / Final Year (Batch 23 / Sem 7): 110323104001 to 110323104120 + Laterals 301, 302.
        val missing = setOf(45, 63, 93)
        val regular = (1..120).filter { it !in missing }.map { index ->
            student("110323104", index, index, StudentYear.YEAR_4, 7, studentName(4, index))
        }
        val lateral1 = Student(
            id = "stu_110323104301",
            registerNumber = "110323104301",
            name = "Karthikeyan G",
            year = StudentYear.YEAR_4,
            section = "B",
            position = 121,
            active = true,
        )
        val lateral2 = Student(
            id = "stu_110323104302",
            registerNumber = "110323104302",
            name = "Praveen Kumar S",
            year = StudentYear.YEAR_4,
            section = "B",
            position = 122,
            active = true,
        )
        return regular + lateral1 + lateral2
    }

    private fun studentName(year: Int, index: Int): String {
        val base = commonStudentNames[(index - 1) % commonStudentNames.size]
        return "$base (${year}Y-$index)"
    }

    private fun student(
        prefix: String,
        serial: Int,
        position: Int,
        year: StudentYear,
        semester: Int,
        name: String,
    ): Student {
        val reg = "$prefix${serial.toString().padStart(3, '0')}"
        return Student(
            id = "stu_$reg",
            registerNumber = reg,
            name = name,
            year = year,
            section = if (serial % 2 == 0) "B" else "A",
            position = position,
            active = true,
        )
    }
}
