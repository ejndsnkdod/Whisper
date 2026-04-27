package com.nocturne.whisper.ui.persona

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.nocturne.whisper.R
import com.nocturne.whisper.data.model.PersonaInfo
import com.nocturne.whisper.databinding.ActivityPersonaListBinding
import com.nocturne.whisper.utils.PersonaManager
import java.io.BufferedReader
import java.io.InputStreamReader

class PersonaListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonaListBinding
    private var personaAdapter: PersonaAdapter? = null

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importPersona(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonaListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()

        initPersonaAdapter()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        initPersonaAdapter()
    }

    private fun initPersonaAdapter() {
        val adapter = PersonaAdapter(
            items = PersonaManager.personaMap,
            onApplyClick = { id ->

                PersonaManager.currentPersonaId = id
                PersonaManager.saveCurrentDataToDisk(this)
                initPersonaAdapter()
                Toast.makeText(this, "已选择人设", Toast.LENGTH_SHORT).show()
            },
            onLongClick = { id ->
                showPersonaOptionsDialog(id)
                true
            }
        )
        this.personaAdapter = adapter
        binding.recyclerView.adapter = adapter

        binding.tvEmpty.visibility =
            if (PersonaManager.personaMap.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {

        binding.btnImport.setOnClickListener {
            importLauncher.launch("*/*")
        }
    }

    private fun importPersona(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val jsonString = reader.readText()

                android.util.Log.d("PersonaList", "读取JSON文件，长度: ${jsonString.length}")
                android.util.Log.d("PersonaList", "JSON前200字符: ${jsonString.take(200)}")

                val beforeCount = PersonaManager.personaMap.size

                val success = PersonaManager.loadFromJson(jsonString)

                val afterCount = PersonaManager.personaMap.size
                val importedCount = afterCount - beforeCount

                if (success && importedCount > 0) {

                    PersonaManager.saveCurrentDataToDisk(this)

                    initPersonaAdapter()

                    Toast.makeText(this, "导入成功！新增 $importedCount 个人设，共 $afterCount 个", Toast.LENGTH_LONG).show()
                } else if (success && importedCount == 0) {

                    Toast.makeText(this, "JSON解析成功，但没有找到有效人设数据\n请检查文件格式", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "导入失败，无法解析JSON格式\n请确保是有效的SillyTavern导出文件", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PersonaList", "导入异常", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPersonaOptionsDialog(id: String) {
        val persona = PersonaManager.personaMap[id] ?: return

        AlertDialog.Builder(this)
            .setTitle(persona.name)
            .setItems(arrayOf("编辑", "删除")) { _, which ->
                when (which) {
                    0 -> {

                        val intent = Intent(this, PersonaEditActivity::class.java).apply {
                            putExtra("persona_id", id)
                        }
                        startActivity(intent)
                    }
                    1 -> {

                        showDeleteDialog(id)
                    }
                }
            }
            .show()
    }

    private fun showDeleteDialog(id: String) {
        AlertDialog.Builder(this)
            .setTitle("删除人设")
            .setMessage("确定要删除这个人设吗？")
            .setPositiveButton("删除") { _, _ ->

                if (id == PersonaManager.currentPersonaId) {
                    PersonaManager.currentPersonaId = "sister-high"
                }
                PersonaManager.personaMap.remove(id)
                PersonaManager.saveCurrentDataToDisk(this)
                initPersonaAdapter()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class PersonaAdapter(
        private val items: Map<String, PersonaInfo>,
        private val onApplyClick: (String) -> Unit,
        private val onLongClick: (String) -> Boolean
    ) : RecyclerView.Adapter<PersonaAdapter.ViewHolder>() {

        private val ids: List<String> = items.keys.toList()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: com.google.android.material.card.MaterialCardView =
                itemView.findViewById(com.nocturne.whisper.R.id.cardPersona)
            val tvName: android.widget.TextView =
                itemView.findViewById(com.nocturne.whisper.R.id.tvName)
            val tvDescription: android.widget.TextView =
                itemView.findViewById(com.nocturne.whisper.R.id.tvDescription)
            val ivSelected: android.widget.ImageView =
                itemView.findViewById(com.nocturne.whisper.R.id.ivSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(com.nocturne.whisper.R.layout.item_persona, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val id = ids[position]
            val persona = items[id] ?: return

            holder.tvName.text = persona.name.ifEmpty { "未命名" }
            holder.tvDescription.text = persona.description.take(50).let {
                if (persona.description.length > 50) "$it..." else it
            }

            val isSelected = id == PersonaManager.currentPersonaId
            holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.cardView.isChecked = isSelected
            holder.cardView.strokeWidth = if (isSelected) 4 else 0
            holder.cardView.strokeColor = holder.itemView.context.getColor(com.nocturne.whisper.R.color.purple_500)

            holder.cardView.setOnClickListener {
                onApplyClick(id)
            }

            holder.cardView.setOnLongClickListener {
                onLongClick(id)
            }
        }

        override fun getItemCount(): Int = ids.size
    }
}
