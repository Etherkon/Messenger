package com.westie.messenger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var edit_ip : EditText
    private lateinit var edit_port : EditText
    private lateinit var edit_name : EditText
    private lateinit var ip : String
    private lateinit var port : String
    private lateinit var name : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        setTitle("Settings")

        edit_ip = findViewById(R.id.edit_ip)
        edit_port = findViewById(R.id.edit_port)
        edit_name = findViewById(R.id.edit_name)
        ip = edit_ip.text.toString().trim()
        port = edit_port.text.toString().trim()
        name = edit_name.text.toString().trim()
    }

    override fun onClick(view: View) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putString("ip", ip)
        bundle.putString("port", port)
        bundle.putString("name", name)
        intent.putExtras(bundle)
        setResult(RESULT_OK, intent)
        finish()
    }
}
