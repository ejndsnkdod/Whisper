package com.nocturne.whisper.ui.persona

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nocturne.whisper.data.model.PersonaInfo
import com.nocturne.whisper.databinding.ActivityPersonaEditBinding
import com.nocturne.whisper.utils.PersonaManager

class PersonaEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonaEditBinding
    private var editingPersonaId: String? = null
    private var editingPersona: PersonaInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonaEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupButtons()

        val personaId = intent.getStringExtra("persona_id")
        if (personaId != null) {
            editingPersonaId = personaId
            editingPersona = PersonaManager.personaMap[personaId]
            loadPersonaData()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (editingPersona != null) "编辑人设" else "创建人设"

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            savePersona()
        }
    }

    private fun loadPersonaData() {
        editingPersona?.let { persona ->
            binding.etName.setText(persona.name)
            binding.etDescription.setText(persona.description)
            binding.etPersonality.setText(persona.personality)
            binding.etScenario.setText(persona.scenario)
            binding.etCreatorNotes.setText(persona.creatorNotes)
        }
    }

    private fun savePersona() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "请输入名称"
            return
        }

        val persona = PersonaInfo(
            name = name,
            description = binding.etDescription.text.toString().trim(),
            personality = binding.etPersonality.text.toString().trim(),
            scenario = binding.etScenario.text.toString().trim(),
            creatorNotes = binding.etCreatorNotes.text.toString().trim()
        )

        val id = editingPersonaId ?: System.currentTimeMillis().toString()
        PersonaManager.updatePersona(this, id, persona)

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
