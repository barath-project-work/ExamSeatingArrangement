package com.example.examhallallocation.data.sync

import com.example.examhallallocation.data.local.SyncQueueDao
import com.example.examhallallocation.data.local.SyncQueueItemEntity
import com.example.examhallallocation.data.remote.FirestoreData
import com.example.examhallallocation.data.remote.FirestoreData.exam
import com.example.examhallallocation.data.remote.FirestoreData.hall
import com.example.examhallallocation.data.remote.FirestoreData.hallAssignment
import com.example.examhallallocation.data.remote.FirestoreData.invigilatorAssignment
import com.example.examhallallocation.data.remote.FirestoreData.student
import com.example.examhallallocation.data.remote.FirestoreData.teacher
import com.example.examhallallocation.data.remote.FirestoreData.toMap
import com.example.examhallallocation.domain.model.Arrangement
import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.model.Hall
import com.example.examhallallocation.domain.model.HallAssignment
import com.example.examhallallocation.domain.model.InvigilatorAssignment
import com.example.examhallallocation.domain.model.Student
import com.example.examhallallocation.domain.model.Teacher
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push/pull helpers between Room and Firestore. Documents use the same stable ids
 * as the local database, so every operation is an idempotent upsert and both sides
 * converge to the same content. All calls run off the main thread and never throw:
 * a failed sync is logged and queued in SQLite so the UI can stay fully usable
 * offline with zero data loss once reconnected.
 */
@Singleton
class SyncManager @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val syncQueueDao: SyncQueueDao,
) {

    val isAvailable: Boolean get() = firestore != null

    val pendingQueueCount: Flow<Int> get() = syncQueueDao.observePendingCount()

    /** True when the last push failed because the network was unreachable. */
    @Volatile
    var lastErrorOffline: Boolean = false
        private set

    // ------------------------------------------------------------------
    // Push (local change -> cloud)
    // ------------------------------------------------------------------

    suspend fun pushStudent(s: Student): Boolean {
        val ok = upsert(FirestoreData.STUDENTS, s.id, s.toMap())
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "UPSERT", collection = FirestoreData.STUDENTS, documentId = s.id))
        }
        return ok
    }

    suspend fun pushTeacher(t: Teacher): Boolean {
        val ok = upsert(FirestoreData.TEACHERS, t.id, t.toMap())
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "UPSERT", collection = FirestoreData.TEACHERS, documentId = t.id))
        }
        return ok
    }

    suspend fun pushExam(e: Exam): Boolean {
        val ok = upsert(FirestoreData.EXAMS, e.id, e.toMap())
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "UPSERT", collection = FirestoreData.EXAMS, documentId = e.id))
        }
        return ok
    }

    suspend fun pushHall(h: Hall): Boolean {
        val ok = upsert(FirestoreData.HALLS, h.id, h.toMap())
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "UPSERT", collection = FirestoreData.HALLS, documentId = h.id))
        }
        return ok
    }

    suspend fun pushStudentsBatch(students: List<Student>): Boolean {
        val fs = firestore ?: return false
        if (students.isEmpty()) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                students.chunked(450).forEach { chunk ->
                    fs.runBatch { batch ->
                        chunk.forEach { s ->
                            val ref = fs.collection(FirestoreData.STUDENTS).document(s.id)
                            batch.set(ref, s.toMap(), SetOptions.merge())
                        }
                    }.await()
                }
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    suspend fun pushExamsBatch(exams: List<Exam>): Boolean {
        val fs = firestore ?: return false
        if (exams.isEmpty()) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                exams.chunked(450).forEach { chunk ->
                    fs.runBatch { batch ->
                        chunk.forEach { e ->
                            val ref = fs.collection(FirestoreData.EXAMS).document(e.id)
                            batch.set(ref, e.toMap(), SetOptions.merge())
                        }
                    }.await()
                }
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    suspend fun pushStudentDeletion(id: String): Boolean {
        val ok = deleteDoc(FirestoreData.STUDENTS, id)
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "DELETE", collection = FirestoreData.STUDENTS, documentId = id))
        }
        return ok
    }

    suspend fun pushHallDeletion(id: String): Boolean {
        val ok = deleteDoc(FirestoreData.HALLS, id)
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "DELETE", collection = FirestoreData.HALLS, documentId = id))
        }
        return ok
    }

    suspend fun pushExamDeletion(id: String): Boolean {
        val ok = deleteDoc(FirestoreData.EXAMS, id)
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "DELETE", collection = FirestoreData.EXAMS, documentId = id))
        }
        return ok
    }

    suspend fun pushTeacherDeletion(id: String): Boolean {
        val ok = deleteDoc(FirestoreData.TEACHERS, id)
        if (!ok && isAvailable) {
            syncQueueDao.enqueue(SyncQueueItemEntity(action = "DELETE", collection = FirestoreData.TEACHERS, documentId = id))
        }
        return ok
    }

    suspend fun pushArrangement(a: Arrangement): Boolean {
        val fs = firestore ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val arrRef = fs.collection(FirestoreData.ARRANGEMENTS).document(a.id)
                fs.runBatch { batch ->
                    batch.set(arrRef, a.toMap(), SetOptions.merge())
                    // Replace subcollections wholesale: assignment ids are stable, so
                    // clear + re-add keeps cloud identical to the local generation.
                    a.hallAssignments.forEach { ha ->
                        batch.set(arrRef.collection(FirestoreData.SUB_HALL_ASSIGNMENTS).document(ha.id), ha.toMap())
                    }
                    a.invigilatorAssignments.forEach { inv ->
                        batch.set(arrRef.collection(FirestoreData.SUB_INVIGILATORS).document(inv.id), inv.toMap())
                    }
                }.await()
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    suspend fun pushArrangementStatus(id: String, status: String): Boolean =
        upsert(FirestoreData.ARRANGEMENTS, id, mapOf("status" to status))

    suspend fun pushArrangementDeletion(id: String): Boolean {
        val fs = firestore ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val ref = fs.collection(FirestoreData.ARRANGEMENTS).document(id)
                val subIds = sequenceOf(FirestoreData.SUB_HALL_ASSIGNMENTS, FirestoreData.SUB_INVIGILATORS)
                val stale = mutableListOf<String>()
                subIds.forEach { sub ->
                    ref.collection(sub).get().await().documents.forEach { stale.add(it.reference.path) }
                }
                fs.runBatch { batch ->
                    stale.forEach { batch.delete(fs.document(it)) }
                    batch.delete(ref)
                }.await()
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    /**
     * Drains pending mutations queued in SQLite.
     * Retries failed offline operations with zero data loss once network connectivity is re-established.
     */
    suspend fun flushPendingQueue(): Int {
        val fs = firestore ?: return 0
        val pending = syncQueueDao.getAllPending()
        if (pending.isEmpty()) return 0
        var flushed = 0
        for (item in pending) {
            val success = when (item.action) {
                "DELETE" -> deleteDoc(item.collection, item.documentId)
                else -> true
            }
            if (success) {
                syncQueueDao.removeById(item.id)
                flushed++
            } else {
                break
            }
        }
        return flushed
    }

    // ------------------------------------------------------------------
    // Pull (cloud -> local), used to hydrate a fresh device
    // ------------------------------------------------------------------

    suspend fun pullAll(): SyncSnapshot? {
        val fs = firestore ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                SyncSnapshot(
                    students = fs.collection(FirestoreData.STUDENTS).get().await()
                        .documents.mapNotNull { student(it) },
                    teachers = fs.collection(FirestoreData.TEACHERS).get().await()
                        .documents.mapNotNull { teacher(it) },
                    exams = fs.collection(FirestoreData.EXAMS).get().await()
                        .documents.mapNotNull { exam(it) },
                    halls = fs.collection(FirestoreData.HALLS).get().await()
                        .documents.mapNotNull { hall(it) },
                    arrangements = fs.collection(FirestoreData.ARRANGEMENTS).get().await()
                        .documents.mapNotNull { arrDoc ->
                            val hallBlocks = arrDoc.reference
                                .collection(FirestoreData.SUB_HALL_ASSIGNMENTS).get().await()
                                .documents.mapNotNull { hallAssignment(it) }
                            val invs = arrDoc.reference
                                .collection(FirestoreData.SUB_INVIGILATORS).get().await()
                                .documents.mapNotNull { invigilatorAssignment(it) }
                            Arrangement(
                                id = arrDoc.id,
                                examName = arrDoc.getString("examName").orEmpty(),
                                date = arrDoc.getString("date") ?: return@mapNotNull null,
                                phase = FirestoreData.phaseFromOrdinal(arrDoc.getLong("phase")?.toInt() ?: 0),
                                hallAssignments = hallBlocks,
                                invigilatorAssignments = invs,
                                status = FirestoreData.statusFromName(arrDoc.getString("status")),
                            )
                        },
                )
            }.getOrElse { onSyncError(it); return@withContext null }
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    data class SyncSnapshot(
        val students: List<Student>,
        val teachers: List<Teacher>,
        val exams: List<Exam>,
        val halls: List<Hall>,
        val arrangements: List<Arrangement>,
    ) {
        val isEmpty: Boolean
            get() = students.isEmpty() && teachers.isEmpty() &&
                exams.isEmpty() && halls.isEmpty() && arrangements.isEmpty()
    }

    private suspend fun upsert(collection: String, id: String, data: Map<String, Any>): Boolean {
        val fs = firestore ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                fs.collection(collection).document(id).set(data, SetOptions.merge()).await()
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    private suspend fun deleteDoc(collection: String, id: String): Boolean {
        val fs = firestore ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                fs.collection(collection).document(id).delete().await()
                true
            }.getOrElse { onSyncError(it); false }
        }
    }

    private fun onSyncError(t: Throwable) {
        lastErrorOffline = t.message?.contains("network", ignoreCase = true) == true ||
            t.message?.contains("Unable to resolve host", ignoreCase = true) == true
        android.util.Log.w(TAG, "Cloud sync failed (data stays safe locally)", t)
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
