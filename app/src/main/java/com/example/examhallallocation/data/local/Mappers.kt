package com.example.examhallallocation.data.local

import com.example.examhallallocation.domain.model.*

fun StudentEntity.toDomain(): Student = Student(
    id = id,
    registerNumber = registerNumber,
    name = name,
    year = StudentYear.fromValue(year) ?: StudentYear.YEAR_2,
    section = section,
    position = position,
    active = active,
)

fun Student.toEntity(): StudentEntity = StudentEntity(
    id = id,
    registerNumber = registerNumber,
    name = name,
    year = year.value,
    section = section,
    position = position,
    active = active,
)

fun TeacherEntity.toDomain(): Teacher = Teacher(
    id = id,
    name = name,
    username = username,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.NORMAL_TEACHER),
    active = active,
)

fun Teacher.toEntity(passwordHash: String? = null): TeacherEntity = TeacherEntity(
    id = id,
    name = name,
    username = username,
    role = role.name,
    active = active,
)

fun SubjectEntity.toDomain(): Subject = Subject(
    id = id,
    code = code,
    name = name,
    year = StudentYear.fromValue(year) ?: StudentYear.YEAR_2,
    semester = semester,
    department = department,
    active = active,
)

fun Subject.toEntity(): SubjectEntity = SubjectEntity(
    id = id,
    code = code,
    name = name,
    year = year.value,
    semester = semester,
    department = department,
    active = active,
)

fun ExamEntity.toDomain(): Exam = Exam(
    id = id,
    examName = examName,
    date = date,
    year = StudentYear.fromValue(year) ?: StudentYear.YEAR_2,
    semester = semester,
    subjectCode = subjectCode,
    subjectName = subjectName,
    session = session,
    timing = timing,
    department = department,
    studentCount = studentCount,
)

fun Exam.toEntity(): ExamEntity = ExamEntity(
    id = id,
    examName = examName,
    date = date,
    year = year.value,
    semester = semester,
    subjectCode = subjectCode,
    subjectName = subjectName,
    session = session,
    timing = timing,
    department = department,
    studentCount = studentCount,
)

fun HallEntity.toDomain(): Hall = Hall(
    id = id,
    roomNumber = roomNumber,
    block = block,
    floor = floor,
    capacity = capacity,
    active = active,
)

fun Hall.toEntity(): HallEntity = HallEntity(
    id = id,
    roomNumber = roomNumber,
    block = block,
    floor = floor,
    capacity = capacity,
    active = active,
)

fun ArrangementEntity.toDomain(
    hallAssignments: List<HallAssignmentEntity>,
    invigilatorAssignments: List<InvigilatorAssignmentEntity>,
): Arrangement = Arrangement(
    id = id,
    examName = examName,
    date = date,
    phase = ExamPhase.entries.first { it.index == phase },
    hallAssignments = hallAssignments.map { it.toDomain() },
    invigilatorAssignments = invigilatorAssignments.map { it.toDomain() },
    status = runCatching { ArrangementStatus.valueOf(status) }.getOrDefault(ArrangementStatus.DRAFT),
)

fun HallAssignmentEntity.toDomain(): HallAssignment = HallAssignment(
    id = id,
    hallId = hallId,
    year = StudentYear.fromValue(year) ?: StudentYear.YEAR_2,
    semester = semester,
    startPosition = startPosition,
    endPosition = endPosition,
    studentIds = if (studentIdsJoined.isBlank()) emptyList() else studentIdsJoined.split(","),
)

fun HallAssignment.toEntity(arrangementId: String): HallAssignmentEntity = HallAssignmentEntity(
    id = id,
    arrangementId = arrangementId,
    hallId = hallId,
    year = year.value,
    semester = semester,
    startPosition = startPosition,
    endPosition = endPosition,
    studentIdsJoined = studentIds.joinToString(","),
    studentCount = studentIds.size,
)

fun InvigilatorAssignmentEntity.toDomain(): InvigilatorAssignment = InvigilatorAssignment(
    id = id,
    hallId = hallId,
    teacherId = teacherId,
)

fun InvigilatorAssignment.toEntity(arrangementId: String): InvigilatorAssignmentEntity = InvigilatorAssignmentEntity(
    id = id,
    arrangementId = arrangementId,
    hallId = hallId,
    teacherId = teacherId,
)

fun Arrangement.toEntity(): ArrangementEntity = ArrangementEntity(
    id = id,
    examName = examName,
    date = date,
    phase = phase.index,
    status = status.name,
)
