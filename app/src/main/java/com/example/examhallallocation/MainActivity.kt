package com.example.examhallallocation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.example.examhallallocation.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val title = when (destination.id) {
                R.id.loginFragment -> getString(R.string.app_name)
                R.id.adminDashboardFragment -> getString(R.string.admin_dashboard_title)
                R.id.studentsFragment -> getString(R.string.students_title)
                R.id.teachersFragment -> getString(R.string.teachers_title)
                R.id.examsFragment -> getString(R.string.exams_title)
                R.id.hallsFragment -> getString(R.string.halls_title)
                R.id.generateFragment,
                R.id.previewFragment,
                R.id.pdfExportFragment -> "Seating Allocation & PDF"
                R.id.teacherDashboardFragment -> getString(R.string.teacher_dashboard_title)
                R.id.teacherProfileFragment -> getString(R.string.nav_profile)
                else -> getString(R.string.app_name)
            }
            binding.tvTitle.text = title
            val isTopLevel = destination.id == R.id.loginFragment ||
                    destination.id == R.id.adminDashboardFragment ||
                    destination.id == R.id.teacherDashboardFragment
            binding.btnBack.visibility = if (!isTopLevel) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.btnBack.setOnClickListener { navController.navigateUp() }
    }
}
