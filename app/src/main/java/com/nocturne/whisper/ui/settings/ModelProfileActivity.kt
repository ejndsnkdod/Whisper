package com.nocturne.whisper.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nocturne.whisper.R
import com.nocturne.whisper.data.model.ModelProfile
import com.nocturne.whisper.utils.ModelProfileManager
import com.nocturne.whisper.utils.SettingsManager

class ModelProfileActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var manager: ModelProfileManager
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_profile)

        manager = ModelProfileManager.getInstance(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setTitle(R.string.model_profiles)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ProfileAdapter()
        recyclerView.adapter = adapter

        findViewById<View>(R.id.fabAdd).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    private fun showAddDialog() {
        if (manager.getProfiles().size >= ModelProfileManager.MAX_PROFILES) {
            Toast.makeText(this, getString(R.string.max_model_profiles_reached), Toast.LENGTH_SHORT).show()
            return
        }

        showProfileDialog(
            titleRes = R.string.add_model_profile,
            confirmRes = R.string.save,
            profile = null
        ) { profile ->
            manager.addProfile(profile)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditDialog(profile: ModelProfile) {
        showProfileDialog(
            titleRes = R.string.edit_model_profile,
            confirmRes = R.string.save,
            profile = profile
        ) { updatedProfile ->
            val list = manager.getProfiles().toMutableList()
            val index = list.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                list[index] = updatedProfile
                manager.saveProfiles(list)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProfileDialog(
        titleRes: Int,
        confirmRes: Int,
        profile: ModelProfile?,
        onConfirm: (ModelProfile) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.config_name)
            setText(profile?.name.orEmpty())
        }
        val apiKeyInput = EditText(this).apply {
            hint = "API Key"
            setText(profile?.apiKey.orEmpty())
        }
        val apiUrlInput = EditText(this).apply {
            hint = getString(R.string.api_url_hint)
            setText(profile?.apiUrl.orEmpty())
        }
        val modelNameInput = EditText(this).apply {
            hint = getString(R.string.model_name_hint)
            setText(profile?.modelName.orEmpty())
        }

        container.addView(nameInput)
        container.addView(apiKeyInput)
        container.addView(apiUrlInput)
        container.addView(modelNameInput)

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(confirmRes) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.profile_name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                onConfirm(
                    (profile ?: ModelProfile()).copy(
                        name = name,
                        apiKey = apiKeyInput.text.toString().trim(),
                        apiUrl = apiUrlInput.text.toString().trim(),
                        modelName = modelNameInput.text.toString().trim()
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class ProfileAdapter : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

        inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameView: TextView = itemView.findViewById(R.id.tvProfileName)
            val modelView: TextView = itemView.findViewById(R.id.tvProfileModel)
            val urlView: TextView = itemView.findViewById(R.id.tvProfileUrl)
            val activeView: TextView = itemView.findViewById(R.id.tvActive)
            val deleteButton: ImageButton = itemView.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model_profile, parent, false)
            return ProfileViewHolder(view)
        }

        override fun getItemCount(): Int = manager.getProfiles().size

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val profile = manager.getProfiles()[position]
            val currentId = manager.getCurrentProfileId()

            holder.nameView.text = profile.name
            holder.modelView.text = profile.modelName
            holder.urlView.text = profile.apiUrl
            holder.activeView.visibility = if (profile.id == currentId) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                manager.setCurrentProfileId(profile.id)

                val settingsManager = SettingsManager.getInstance(this@ModelProfileActivity)
                val settings = settingsManager.getSettings()
                settingsManager.saveSettings(
                    settings.copy(
                        apiKey = profile.apiKey,
                        apiUrl = profile.apiUrl,
                        modelName = profile.modelName
                    )
                )

                notifyDataSetChanged()
                Toast.makeText(
                    this@ModelProfileActivity,
                    getString(R.string.profile_applied, profile.name),
                    Toast.LENGTH_SHORT
                ).show()
            }

            holder.itemView.setOnLongClickListener {
                showEditDialog(profile)
                true
            }

            holder.deleteButton.setOnClickListener {
                AlertDialog.Builder(this@ModelProfileActivity)
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.confirm_delete_profile, profile.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        manager.deleteProfile(profile.id)
                        notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
